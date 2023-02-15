package com.geirolz.app.toolkit

import cats.{Parallel, Show}
import cats.data.EitherT
import cats.effect.{Async, Resource, Sync}
import cats.kernel.Semigroup
import com.geirolz.app.toolkit.error.ErrorLifter
import com.geirolz.app.toolkit.logger.LoggerAdapter

class AppBuilder[F[+_]: Async: Parallel, E, APP_INFO <: BasicAppInfo[
  ?
], LOGGER_T[
  _[_]
]: LoggerAdapter, CONFIG: Show, DEPENDENCIES](
  resourcesLoader: AppResources.Loader[F, APP_INFO, LOGGER_T, CONFIG],
  depsBuilder: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => Resource[F, E | DEPENDENCIES]
) {
  $this =>

  import cats.syntax.all.*

  def dependsOnE[DEP_2](
    dependenciesBuilder: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => Resource[F, E | DEP_2]
  ): AppBuilder[F, E, APP_INFO, LOGGER_T, CONFIG, DEP_2] =
    new AppBuilder(
      resourcesLoader = $this.resourcesLoader,
      depsBuilder     = dependenciesBuilder
    )

  def provideOneE(onFailure: E => OnFailure)(
    f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[E | Any]
  ): Resource[F, E | App[F, E, APP_INFO, LOGGER_T, CONFIG]] =
    provideE(onFailure)(f.andThen(_.pure[List])).map(_.map(_.failureMap(_.value.head)))

  def provideE(onFailure: E => OnFailure)(
    f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => List[F[E | Any]]
  ): Resource[F, E | App[F, Nel[E], APP_INFO, LOGGER_T, CONFIG]] =
    provideFEE(onFailure)(f.andThen(_.asRight.pure[F]))

  def provideFE(onFailure: E => OnFailure)(
    f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[List[F[E | Any]]]
  )(implicit
    el: ErrorLifter[F, E]
  ): Resource[F, E | App[F, Nel[E], APP_INFO, LOGGER_T, CONFIG]] =
    provideFEE(onFailure)(el.liftFunction(f))

  def provideFEE(onFailure: E => OnFailure)(
    f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[E | List[F[E | Any]]]
  ): Resource[F, E | App[F, Nel[E], APP_INFO, LOGGER_T, CONFIG]] = {
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

  type Throw[F[+_], APP_INFO <: BasicAppInfo[?], LOGGER_T[_[_]], CONFIG, DEPENDENCIES] =
    AppBuilder[F, Throwable, APP_INFO, LOGGER_T, CONFIG, DEPENDENCIES]

  def apply[F[+_]: Async: Parallel, E]: AppBuilder.RuntimeSelected[F, E] =
    new AppBuilder.RuntimeSelected[F, E]

  def apply[F[+_]: Async: Parallel](implicit
    di: DummyImplicit
  ): AppBuilder.RuntimeSelected[F, Throwable] =
    AppBuilder[F, Throwable]

  final class RuntimeSelected[F[+_]: Async: Parallel, E] {

    def withResources[APP_INFO <: BasicAppInfo[?], LOGGER_T[_[_]]: LoggerAdapter, CONFIG: Show](
      resources: AppResources[APP_INFO, LOGGER_T[F], CONFIG]
    ): AppBuilder[F, E, APP_INFO, LOGGER_T, CONFIG, Unit] =
      withResourcesLoader(AppResources.pureLoader(resources))

    def withResourcesLoader[APP_INFO <: BasicAppInfo[?], LOGGER_T[
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

sealed trait AppBuilderSyntax {

  import cats.syntax.all.*

  implicit class AppBuilderErrorLiftOps[F[+_]: Async, E: Semigroup, APP_INFO <: BasicAppInfo[
    ?
  ], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show, DEPENDENCIES](
    appBuilder: AppBuilder[F, E, APP_INFO, LOGGER_T, CONFIG, DEPENDENCIES]
  ) {

    def dependsOn[DEP_2](
      dependenciesBuilder: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => Resource[F, DEP_2]
    )(implicit
      el: ErrorLifter.Resource[F, E]
    ): AppBuilder[F, E, APP_INFO, LOGGER_T, CONFIG, DEP_2] =
      appBuilder.dependsOnE[DEP_2](el.liftFunction(dependenciesBuilder))

    def provideOne(f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[Any])(
      implicit el: ErrorLifter[F, E]
    ): Resource[F, E | App[F, E, APP_INFO, LOGGER_T, CONFIG]] =
      appBuilder.provideOneE(_ => OnFailure.CancelAll)(el.liftFunction(f))

    def provide(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => List[F[Any]]
    )(implicit
      el: ErrorLifter[F, E]
    ): Resource[F, E | App[F, Nel[E], APP_INFO, LOGGER_T, CONFIG]] =
      appBuilder
        .provideE(_ => OnFailure.CancelAll)(f.andThen(_.map(el.lift(_))))

    def provideF(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[List[F[Any]]]
    )(implicit
      el: ErrorLifter[F, E]
    ): Resource[F, E | App[F, Nel[E], APP_INFO, LOGGER_T, CONFIG]] =
      appBuilder
        .provideFE(_ => OnFailure.CancelAll)(
          f.andThen(_.map(_.map(el.lift(_))))
        )
  }

  implicit class AppBuilderThrowOps[F[+_]: Async, APP_INFO <: BasicAppInfo[
    ?
  ], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show, DEPENDENCIES](
    appBuilder: AppBuilder.Throw[F, APP_INFO, LOGGER_T, CONFIG, DEPENDENCIES]
  )(implicit semigroupThrow: Semigroup[Throwable]) {

    def provideOneT(f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[Any])(
      implicit el: ErrorLifter[F, Throwable]
    ): Resource[F, App.Throw[F, APP_INFO, LOGGER_T, CONFIG]] =
      appBuilder
        .provideOne(f)
        .evalMap(flatThrowError)

    def provideT(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => List[F[Any]]
    )(implicit
      el: ErrorLifter[F, Throwable]
    ): Resource[F, App.ThrowNel[F, APP_INFO, LOGGER_T, CONFIG]] =
      appBuilder
        .provideE(_ => OnFailure.CancelAll)(f.andThen(_.map(el.lift(_))))
        .evalMap(flatThrowError)

    def provideFT(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[List[F[Any]]]
    )(implicit
      el: ErrorLifter[F, Throwable]
    ): Resource[F, App.ThrowNel[F, APP_INFO, LOGGER_T, CONFIG]] =
      appBuilder
        .provideFE(_ => OnFailure.CancelAll)(
          f.andThen(_.map(_.map(el.lift(_))))
        )
        .evalMap(flatThrowError)

    private def flatThrowError[R]: Throwable | R => F[R] = {
      case Left(e)    => Sync[F].raiseError(e)
      case Right(app) => Sync[F].pure(app)
    }
  }
}
