package com.geirolz.app.toolkit

import cats.{Applicative, ApplicativeError, Parallel, Show}
import cats.effect.{Async, Resource}
import cats.effect.implicits.monadCancelOps_
import cats.effect.kernel.{MonadCancel, Spawn}
import cats.effect.kernel.Resource.ExitCase
import com.geirolz.app.toolkit.logger.LoggerAdapter

trait App[F[+_], APP_INFO <: BasicAppInfo[?], LOGGER_T[_[_]], CONFIG] {

  // ------------------- DEFINITION ------------------
  val resources: AppResources[APP_INFO, LOGGER_T[F], CONFIG]

  val logic: AppServiceRunner[F] => Resource[F, Unit]

  // ---------------------- RUN ----------------------
  final def compiledRun_(implicit F: MonadCancel[F, Throwable]): Resource[F, Unit] =
    logic(AppServiceRunner.run_[F])

  final def compiledRunForever(implicit F: Spawn[F]): Resource[F, Unit] =
    logic(AppServiceRunner.runForever[F])

  final def run(implicit F: MonadCancel[F, Throwable]): F[Unit] =
    compiledRun_.use_

  final def runForever(implicit F: Spawn[F]): F[Nothing] =
    compiledRunForever.useForever

  // -------------------- MAPPING --------------------
  final def map(f: Resource[F, Unit] => Resource[F, Unit]): App[F, APP_INFO, LOGGER_T, CONFIG] =
    App.of(resources, logic.andThen(f))

  final def preRun(
    f: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => F[Unit]
  ): App[F, APP_INFO, LOGGER_T, CONFIG] =
    map(_.preAllocate(f(resources)))

  final def onFinalize(f: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => F[Unit])(implicit
    F: Applicative[F]
  ): App[F, APP_INFO, LOGGER_T, CONFIG] =
    map(_.onFinalize(f(resources)))

  final def onFinalizeCase(f: (AppResources[APP_INFO, LOGGER_T[F], CONFIG], ExitCase) => F[Unit])(
    implicit F: Applicative[F]
  ): App[F, APP_INFO, LOGGER_T, CONFIG] =
    map(_.onFinalizeCase(ec => f(resources, ec)))

  final def onError(f: (AppResources[APP_INFO, LOGGER_T[F], CONFIG], Throwable) => F[Unit])(implicit
    F: Applicative[F]
  ): App[F, APP_INFO, LOGGER_T, CONFIG] =
    onFinalizeCase { case (res, ec) =>
      ec match {
        case ExitCase.Errored(e) => f(res, e)
        case _                   => F.unit
      }
    }

  final def onCancel(f: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => F[Unit])(implicit
    F: Applicative[F]
  ): App[F, APP_INFO, LOGGER_T, CONFIG] =
    onFinalizeCase { case (res, ec) =>
      ec match {
        case ExitCase.Canceled => f(res)
        case _                 => F.unit
      }
    }

  final def handleErrorWith[E](
    f: (AppResources[APP_INFO, LOGGER_T[F], CONFIG], E) => F[Unit]
  )(implicit
    F: ApplicativeError[F, E]
  ): App[F, APP_INFO, LOGGER_T, CONFIG] =
    map(_.handleErrorWith[Unit, E](e => Resource.eval(f(resources, e))))
}
object App {

  import cats.syntax.all.*

  def apply[F[+_]: Async: Parallel]: AppBuilderRuntimeSelected[F] =
    new AppBuilderRuntimeSelected[F]

  final class AppBuilderRuntimeSelected[F[+_]: Async: Parallel] {

    def withResources[APP_INFO <: BasicAppInfo[?], LOGGER_T[_[_]]: LoggerAdapter, CONFIG: Show](
      resources: AppResources[APP_INFO, LOGGER_T[F], CONFIG]
    ): AppBuilder[F, APP_INFO, LOGGER_T, CONFIG, Unit] =
      withResourcesLoader(AppResources.pureLoader(resources))

    def withResourcesLoader[APP_INFO <: BasicAppInfo[?], LOGGER_T[
      _[_]
    ]: LoggerAdapter, CONFIG: Show](
      resourcesLoader: AppResources.Loader[F, APP_INFO, LOGGER_T, CONFIG]
    ): AppBuilder[F, APP_INFO, LOGGER_T, CONFIG, Unit] =
      new AppBuilder(
        resourcesLoader,
        _ => Resource.unit[F]
      )
  }

  class AppBuilder[F[+_]: Async: Parallel, APP_INFO <: BasicAppInfo[?], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show, DEPENDENCIES](
    resourcesLoader: AppResources.Loader[F, APP_INFO, LOGGER_T, CONFIG],
    dependencies: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => Resource[F, DEPENDENCIES]
  ) { $this =>

    def dependsOn[DEPENDENCIES_2](
      dependencies: AppResources[APP_INFO, LOGGER_T[F], CONFIG] => Resource[F, DEPENDENCIES_2]
    ): AppBuilder[F, APP_INFO, LOGGER_T, CONFIG, DEPENDENCIES_2] =
      new AppBuilder(
        resourcesLoader = $this.resourcesLoader,
        dependencies    = dependencies
      )

    def logic(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[Unit]
    ): Resource[F, App[F, APP_INFO, LOGGER_T, CONFIG]] =
      provideOne(deps => Resource.eval(f(deps)))

    def provideOne(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => Resource[F, Any]
    ): Resource[F, App[F, APP_INFO, LOGGER_T, CONFIG]] =
      provide(deps => List(f(deps)))

    def provide(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => List[
        Resource[F, Any]
      ]
    ): Resource[F, App[F, APP_INFO, LOGGER_T, CONFIG]] =
      provideF(deps => f(deps).pure[F])

    def provideF(
      f: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[List[Resource[F, Any]]]
    ): Resource[F, App[F, APP_INFO, LOGGER_T, CONFIG]] =
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

  def fromServices[F[+_]: Async: Parallel, APP_INFO <: BasicAppInfo[?], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show](
    appResources: AppResources[APP_INFO, LOGGER_T[F], CONFIG],
    appProvServices: List[Resource[F, Any]]
  ): App[F, APP_INFO, LOGGER_T, CONFIG] = {
    val toolkitLogger = LoggerAdapter[LOGGER_T].toToolkit[F](appResources.logger)
    val logic: AppServiceRunner[F] => Resource[F, Unit] = runner => {
      val info = appResources.info
      Resource
        .eval[F, Unit](
          toolkitLogger.info(s"Starting ${info.buildRefName}...") >>
            appProvServices
              .parTraverse[F, Any](runner.run(_))
              .onCancel(toolkitLogger.info(s"${info.name} was stopped."))
              .onError(e => toolkitLogger.error(e)(s"${info.name} was stopped due an error."))
              .void
        )
        .onFinalize(toolkitLogger.info(s"Shutting down ${info.name}..."))
    }

    App.of(appResources, logic)
  }

  def of[F[+_], APP_INFO <: BasicAppInfo[?], LOGGER_T[_[_]], CONFIG](
    appResources: AppResources[APP_INFO, LOGGER_T[F], CONFIG],
    appLogic: AppServiceRunner[F] => Resource[F, Unit]
  ): App[F, APP_INFO, LOGGER_T, CONFIG] = new App[F, APP_INFO, LOGGER_T, CONFIG] {
    override val resources: AppResources[APP_INFO, LOGGER_T[F], CONFIG] = appResources
    override val logic: AppServiceRunner[F] => Resource[F, Unit]        = appLogic
  }
}
