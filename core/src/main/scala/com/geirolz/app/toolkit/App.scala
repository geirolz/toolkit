package com.geirolz.app.toolkit

import cats.{Applicative, ApplicativeError, Parallel, Show}
import cats.effect.{Async, Resource}
import cats.effect.implicits.monadCancelOps_
import cats.effect.kernel.MonadCancel
import cats.effect.kernel.Resource.ExitCase
import com.geirolz.app.toolkit.logger.LoggerAdapter

trait App[F[+_], E, APP_INFO <: BasicAppInfo[?], LOGGER_T[_[_]], CONFIG] {

  import cats.syntax.all.*

  // ------------------- DEFINITION ------------------
  val resources: AppResources[APP_INFO, LOGGER_T[F], CONFIG]

  val logic: Resource[F, Either[E, Unit]]

  def flattenLogic(implicit F: MonadCancel[F, Throwable], env: E <:< Throwable): Resource[F, Unit] =
    logic.flatMap {
      case Left(left)   => Resource.eval(F.raiseError(left))
      case Right(value) => Resource.pure(value)
    }

  // ---------------------- RUN ----------------------
  final def run(implicit F: MonadCancel[F, Throwable], env: E <:< Throwable): F[Unit] =
    flattenLogic.use_

  // -------------------- MAPPING --------------------
  final def logicMap[EE](
    f: Resource[F, Either[E, Unit]] => Resource[F, Either[EE, Unit]]
  ): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    App.of(resources, f(logic))

  final def map[EE](
    f: Either[E, Unit] => Either[EE, Unit]
  ): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.map(f))

  final def flatMap[EE](
    f: Either[E, Unit] => Resource[F, Either[EE, Unit]]
  ): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.flatMap(f))

  final def evalMap[EE](
    f: Either[E, Unit] => F[Either[EE, Unit]]
  ): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.evalMap(f))

  final def evalTap(
    f: Either[E, Unit] => F[Unit]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.evalTap(f))

  final def preRun(
    f: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => F[Unit]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.preAllocate(f(resources)))

  final def onFinalize(f: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => F[Unit])(implicit
    F: Applicative[F]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.onFinalize(f(resources)))

  final def onFinalizeCase(f: (AppResources[APP_INFO, LOGGER_T[F], CONFIG], ExitCase) => F[Unit])(
    implicit F: Applicative[F]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.onFinalizeCase(ec => f(resources, ec)))

  final def onCancel(f: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => F[Unit])(implicit
    F: Applicative[F]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] =
    onFinalizeCase { case (res, ec) =>
      ec match {
        case ExitCase.Canceled => f(res)
        case _                 => F.unit
      }
    }

  // -------------------- FAILURE --------------------
  final def failureMap[EE](f: E => EE): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    map(_.leftMap(f))

  final def failureEvalMap[EE](
    f: E => F[EE]
  )(implicit F: Applicative[F]): App[F, EE, APP_INFO, LOGGER_T, CONFIG] =
    evalMap {
      case Left(e)      => f(e).map(Left(_))
      case Right(value) => Right(value).pure[F]
    }

  final def recoverFailureWith(
    f: E => F[Unit]
  )(implicit F: Applicative[F]): App[F, E, APP_INFO, LOGGER_T, CONFIG] =
    evalMap {
      case Left(e)      => f(e).map(Right(_))
      case Right(value) => Right(value).pure[F]
    }

  // -------------------- ERROR --------------------
  final def onError(f: (AppResources[APP_INFO, LOGGER_T[F], CONFIG], Throwable) => F[Unit])(implicit
    F: Applicative[F]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] =
    onFinalizeCase { case (res, ec) =>
      ec match {
        case ExitCase.Errored(e) => f(res, e)
        case _                   => F.unit
      }
    }

  final def handleErrorWith(
    f: (AppResources[APP_INFO, LOGGER_T[F], CONFIG], Throwable) => F[Either[E, Unit]]
  )(implicit
    F: ApplicativeError[F, Throwable]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] =
    logicMap(_.handleErrorWith[Either[E, Unit], Throwable](e => Resource.eval(f(resources, e))))
}
object App {

  import cats.syntax.all.*

  def apply[F[+_]: Async: Parallel, E]: AppBuilderRuntimeSelected[F, E] =
    new AppBuilderRuntimeSelected[F, E]

  def apply[F[+_]: Async: Parallel](implicit
    di: DummyImplicit
  ): AppBuilderRuntimeSelected[F, Throwable] =
    App[F, Throwable]

  final class AppBuilderRuntimeSelected[F[+_]: Async: Parallel, E] {

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
        _ => Resource.unit[F]
      )
  }

  class AppBuilder[F[+_]: Async: Parallel, E, APP_INFO <: BasicAppInfo[?], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show, DEPENDENCIES](
    resourcesLoader: AppResources.Loader[F, APP_INFO, LOGGER_T, CONFIG],
    dependencies: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => Resource[F, DEPENDENCIES]
  ) { $this =>

    def dependsOn[DEPENDENCIES_2](
      dependencies: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => Resource[F, DEPENDENCIES_2]
    ): AppBuilder[F, E, APP_INFO, LOGGER_T, CONFIG, DEPENDENCIES_2] =
      new AppBuilder(
        resourcesLoader = $this.resourcesLoader,
        dependencies    = dependencies
      )

    def provideOne(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[Any]
    ): Resource[F, App[F, E, APP_INFO, LOGGER_T, CONFIG]] =
      provide(deps => List(f(deps)))

    def provide(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => List[F[Any]]
    ): Resource[F, App[F, E, APP_INFO, LOGGER_T, CONFIG]] =
      provideF(deps => f(deps).pure[F])

    def provideF(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[List[F[Any]]]
    ): Resource[F, App[F, E, APP_INFO, LOGGER_T, CONFIG]] =
      for {
        // -------------------- RESOURCES -------------------
        appResources <- Resource.eval(resourcesLoader.load)
        resLogger = LoggerAdapter[LOGGER_T].toToolkit(appResources.logger).mapK(Resource.liftK[F])

        // ------------------- DEPENDENCIES -----------------
        _              <- resLogger.info("Building services environment...")
        appDepServices <- dependencies(appResources)
        _              <- resLogger.info("Services environment successfully built.")

        // --------------------- SERVICES -------------------
        _ <- resLogger.info("Building App...")
        appProvServices <- Resource.eval(
          f(AppDependencies(appResources, appDepServices))
        )
        _ <- resLogger.info("App successfully built.")
      } yield App.fromServices(appResources, appProvServices)
  }

  def fromServices[F[+_]: Async: Parallel, E, APP_INFO <: BasicAppInfo[?], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show](
    appResources: AppResources[APP_INFO, LOGGER_T[F], CONFIG],
    appProvServices: List[F[Any]]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] = {
    val toolkitLogger = LoggerAdapter[LOGGER_T].toToolkit[F](appResources.logger)
    val logic: Resource[F, Either[E, Unit]] = {
      val info = appResources.info
      Resource
        .eval[F, Either[E, Unit]](
          toolkitLogger.info(s"Starting ${info.buildRefName}...") >>
            appProvServices
              .parTraverse[F, Any](identity)
              .onCancel(toolkitLogger.info(s"${info.name} was stopped."))
              .onError(e => toolkitLogger.error(e)(s"${info.name} was stopped due an error."))
              .void
              .map(Right(_))
        )
        .onFinalize(toolkitLogger.info(s"Shutting down ${info.name}..."))
    }

    App.of(appResources, logic)
  }

  def of[F[+_], E, APP_INFO <: BasicAppInfo[?], LOGGER_T[_[_]], CONFIG](
    appResources: AppResources[APP_INFO, LOGGER_T[F], CONFIG],
    appLogic: Resource[F, Either[E, Unit]]
  ): App[F, E, APP_INFO, LOGGER_T, CONFIG] = new App[F, E, APP_INFO, LOGGER_T, CONFIG] {
    override val resources: AppResources[APP_INFO, LOGGER_T[F], CONFIG] = appResources
    override val logic: Resource[F, Either[E, Unit]]                    = appLogic
  }
}
