package com.geirolz.app.toolkit

import cats.{Parallel, Show}
import cats.data.{EitherT, NonEmptyList}
import cats.effect.{Async, Resource, Sync}
import com.geirolz.app.toolkit.error.ErrorLifter
import com.geirolz.app.toolkit.logger.LoggerAdapter

class AppBuilder[F[+_]: Async: Parallel, E, APP_INFO <: SimpleAppInfo[
  ?
], LOGGER_T[
  _[_]
]: LoggerAdapter, CONFIG: Show, DEPENDENCIES](
  resourcesLoader: AppResources.Loader[F, APP_INFO, LOGGER_T, CONFIG],
  depsBuilder: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => Resource[F, E | DEPENDENCIES]
)(implicit el: ErrorLifter[F, E]) {
  $this =>

  import cats.syntax.all.*

  // ------------------- DEPENDS ON -------------------
  private[toolkit] def _dependsOnRight[DEP_2](
    f: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => Resource[F, DEP_2]
  ): AppBuilder[F, E, APP_INFO, LOGGER_T, CONFIG, DEP_2] =
    _dependsOn[DEP_2](el.liftResourceFunction(f))

  private[toolkit] def _dependsOn[DEP_2](
    f: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => Resource[F, E | DEP_2]
  ): AppBuilder[F, E, APP_INFO, LOGGER_T, CONFIG, DEP_2] =
    new AppBuilder(
      resourcesLoader = $this.resourcesLoader,
      depsBuilder     = f
    )

  // ------------------- PROVIDE ONE -------------------
  private[toolkit] def _provideOneRight(
    f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[Any]
  ): Resource[F, E | App[F, E, APP_INFO, LOGGER_T, CONFIG]] =
    _provideOne(el.liftFunction(f))

  private[toolkit] def _provideOne(
    f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[E | Any]
  ): Resource[F, E | App[F, E, APP_INFO, LOGGER_T, CONFIG]] =
    _provide(_ => OnFailure.CancelAll)(f.andThen(_.pure[List]))
      .map(_.map(_.failureMap(_.value.head)))

  // ------------------- PROVIDE -------------------
  private[toolkit] def _provideRight(
    f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => List[F[Any]]
  ): Resource[F, E | App[F, NonEmptyList[E], APP_INFO, LOGGER_T, CONFIG]] =
    _provide(_ => OnFailure.CancelAll)(f.andThen(_.map(el.lift(_))))

  private[toolkit] def _provide(onFailure: E => OnFailure)(
    f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => List[F[E | Any]]
  ): Resource[F, E | App[F, NonEmptyList[E], APP_INFO, LOGGER_T, CONFIG]] =
    _provideFE(onFailure)(f.andThen(_.asRight.pure[F]))

  // ------------------- PROVIDE F -------------------
  private[toolkit] def _provideRightF(
    f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[List[F[Any]]]
  ): Resource[F, E | App[F, NonEmptyList[E], APP_INFO, LOGGER_T, CONFIG]] =
    _provideF(_ => OnFailure.CancelAll)(f.andThen(_.map(_.map(el.lift(_)))))

  private[toolkit] def _provideF(onFailure: E => OnFailure)(
    f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[List[F[E | Any]]]
  ): Resource[F, E | App[F, NonEmptyList[E], APP_INFO, LOGGER_T, CONFIG]] =
    _provideFE(onFailure)(el.liftFunction(f))

  private[toolkit] def _provideFE(onFailure: E => OnFailure)(
    f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[E | List[F[E | Any]]]
  ): Resource[F, E | App[F, NonEmptyList[E], APP_INFO, LOGGER_T, CONFIG]] = {
    (
      for {
        // -------------------- RESOURCES -------------------
        appResources <- EitherT.right[E](Resource.eval(resourcesLoader.load))
        resLogger = LoggerAdapter[LOGGER_T]
          .toToolkit(appResources.logger)
          .mapK(Resource.liftK[F].andThen(EitherT.liftK[Resource[F, *], E]))

        // ------------------- DEPENDENCIES -----------------
        _              <- resLogger.info("Building services environment...")
        appDepServices <- EitherT(depsBuilder(appResources))
        _              <- resLogger.info("Services environment successfully built.")

        // --------------------- SERVICES -------------------
        _ <- resLogger.info("Building App...")
        appProvServices <- EitherT(
          Resource.eval(f(AppDependencies(appResources, appDepServices)))
        )
        _ <- resLogger.info("App successfully built.")
      } yield App.fromServices(appResources, appProvServices, onFailure)
    ).value
  }
}
object AppBuilder extends AppBuilderSyntax {

  def apply[F[+_]: Async: Parallel, E: =:!=[*, Throwable]: ErrorLifter[F, *]]
    : AppBuilder.RuntimeSelected[F, E] =
    new AppBuilder.RuntimeSelected[F, E]

  def apply[F[+_]: Async: Parallel](implicit
    di: DummyImplicit
  ): AppBuilder.RuntimeSelected[F, Throwable] =
    new AppBuilder.RuntimeSelected[F, Throwable]

  final class RuntimeSelected[F[+_]: Async: Parallel, E: ErrorLifter[F, *]] {

    def withResources[APP_INFO <: SimpleAppInfo[?], LOGGER_T[_[_]]: LoggerAdapter, CONFIG: Show](
      resources: AppResources[APP_INFO, LOGGER_T[F], CONFIG]
    ): AppBuilder[F, E, APP_INFO, LOGGER_T, CONFIG, Unit] =
      withResourcesLoader(AppResources.pureLoader(resources))

    def withResourcesLoader[APP_INFO <: SimpleAppInfo[?], LOGGER_T[
      _[_]
    ]: LoggerAdapter, CONFIG: Show](
      resourcesLoader: AppResources.Loader[F, APP_INFO, LOGGER_T, CONFIG]
    ): AppBuilder[F, E, APP_INFO, LOGGER_T, CONFIG, Unit] =
      new AppBuilder(
        resourcesLoader,
        _ => Resource.pure(Right(()))
      )
  }
}

sealed trait AppBuilderSyntax { this: AppBuilder.type =>

  implicit class AppBuilderErrorOps[F[+_]: Async, E, APP_INFO <: SimpleAppInfo[
    ?
  ], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show, DEPENDENCIES](
    appBuilder: AppBuilder[F, E, APP_INFO, LOGGER_T, CONFIG, DEPENDENCIES]
  )(implicit env: E =:!= Throwable, el: ErrorLifter[F, E]) {

    // ------------ dependsOnE ------------
    def dependsOn[DEP_2](
      f: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => Resource[F, DEP_2]
    )(implicit dummyImplicit: DummyImplicit): AppBuilder[F, E, APP_INFO, LOGGER_T, CONFIG, DEP_2] =
      appBuilder._dependsOnRight[DEP_2](f)

    def dependsOn[DEP_2](
      f: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => Resource[F, E | DEP_2]
    ): AppBuilder[F, E, APP_INFO, LOGGER_T, CONFIG, DEP_2] =
      appBuilder._dependsOn(f)

    // ------------ provideOneE ------------
    def provideOneRight(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[Any]
    ): Resource[F, E | App[F, E, APP_INFO, LOGGER_T, CONFIG]] =
      appBuilder._provideOneRight(f)

    def provideOne(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[E | Any]
    ): Resource[F, E | App[F, E, APP_INFO, LOGGER_T, CONFIG]] =
      appBuilder._provideOne(f)

    // ------------ provideE ------------
    def provideRight(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => List[F[Any]]
    ): Resource[F, E | App[F, NonEmptyList[E], APP_INFO, LOGGER_T, CONFIG]] =
      appBuilder._provideRight(f)

    def provide(onFailure: E => OnFailure)(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => List[F[E | Any]]
    ): Resource[F, E | App[F, NonEmptyList[E], APP_INFO, LOGGER_T, CONFIG]] =
      appBuilder._provide(onFailure)(f)

    // ------------ provideFE ------------
    def provideRightF(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[List[F[Any]]]
    ): Resource[F, E | App[F, NonEmptyList[E], APP_INFO, LOGGER_T, CONFIG]] =
      appBuilder._provideRightF(f)

    def provideF(onFailure: E => OnFailure)(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[List[F[E | Any]]]
    ): Resource[F, E | App[F, NonEmptyList[E], APP_INFO, LOGGER_T, CONFIG]] =
      appBuilder._provideF(onFailure)(f)

    def provideFE(onFailure: E => OnFailure)(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[E | List[F[E | Any]]]
    ): Resource[F, E | App[F, NonEmptyList[E], APP_INFO, LOGGER_T, CONFIG]] =
      appBuilder._provideFE(onFailure)(f)
  }

  implicit class AppBuilderThrowOps[F[+_]: Async, APP_INFO <: SimpleAppInfo[
    ?
  ], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show, DEPENDENCIES](
    appBuilder: AppBuilder[F, Throwable, APP_INFO, LOGGER_T, CONFIG, DEPENDENCIES]
  ) {

    def dependsOn[DEP_2](
      f: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => Resource[F, DEP_2]
    ): AppBuilder[F, Throwable, APP_INFO, LOGGER_T, CONFIG, DEP_2] =
      appBuilder._dependsOnRight[DEP_2](f)

    def provideOne(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[Any]
    ): Resource[F, App[F, Throwable, APP_INFO, LOGGER_T, CONFIG]] =
      appBuilder
        ._provideOneRight(f)
        .evalMap(flatThrowError)

    def provide(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => List[F[Any]]
    ): Resource[F, App[F, NonEmptyList[Throwable], APP_INFO, LOGGER_T, CONFIG]] =
      appBuilder
        ._provideRight(f)
        .evalMap(flatThrowError)

    def provideF(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[List[F[Any]]]
    ): Resource[F, App[F, NonEmptyList[Throwable], APP_INFO, LOGGER_T, CONFIG]] =
      appBuilder
        ._provideRightF(f)
        .evalMap(flatThrowError)

    private def flatThrowError[R]: Throwable | R => F[R] = {
      case Left(e)    => Sync[F].raiseError(e)
      case Right(app) => Sync[F].pure(app)
    }
  }
}
