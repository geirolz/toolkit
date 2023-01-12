package com.geirolz.app.toolkit

import cats.{Parallel, Show}
import cats.effect.{Async, Resource, Spawn}
import cats.effect.implicits.monadCancelOps_
import cats.effect.kernel.MonadCancel
import com.geirolz.app.toolkit.logger.LoggerAdapter

trait App[F[_], APP_INFO <: BasicAppInfo[?], LOGGER_T[_[_]], CONFIG] {

  val resources: AppResources[APP_INFO, LOGGER_T[F], CONFIG]

  val logic: Resource[F, Unit]

  def run(implicit F: MonadCancel[F, Throwable]): F[Unit] =
    logic.use_

  def runForever(implicit F: Spawn[F]): F[Nothing] =
    logic.useForever
}
object App {

  import cats.syntax.all.*

  def apply[F[_]: Async: Parallel]: AppBuilderRuntimeSelected[F] =
    new AppBuilderRuntimeSelected[F]

  final class AppBuilderRuntimeSelected[F[_]: Async: Parallel] {

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

  class AppBuilder[F[_]: Async: Parallel, APP_INFO <: BasicAppInfo[?], LOGGER_T[
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
      logic: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[Unit]
    ): Resource[F, App[F, APP_INFO, LOGGER_T, CONFIG]] =
      provideOne(deps => Resource.eval(logic(deps)))

    def provideOne(
      service: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => Resource[F, Unit]
    ): Resource[F, App[F, APP_INFO, LOGGER_T, CONFIG]] =
      provide(deps => List(service(deps)))

    def provide(
      services: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => List[
        Resource[F, Unit]
      ]
    ): Resource[F, App[F, APP_INFO, LOGGER_T, CONFIG]] =
      provideF(deps => services(deps).pure[F])

    def provideF(
      servicesBuilder: AppDependencies[APP_INFO, LOGGER_T[F], CONFIG, DEPENDENCIES] => F[
        List[Resource[F, Unit]]
      ]
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
          servicesBuilder(AppDependencies(appResources, appDepServices))
        )
        _ <- resLogger.info("App successfully built.")
      } yield App.of(appResources, appProvServices)
  }

  def of[F[_]: Async: Parallel, APP_INFO <: BasicAppInfo[?], LOGGER_T[
    _[_]
  ]: LoggerAdapter, CONFIG: Show](
    appResources: AppResources[APP_INFO, LOGGER_T[F], CONFIG],
    appProvServices: List[Resource[F, Unit]]
  ): App[F, APP_INFO, LOGGER_T, CONFIG] = {
    val toolkitLogger = LoggerAdapter[LOGGER_T].toToolkit[F](appResources.logger)
    new App[F, APP_INFO, LOGGER_T, CONFIG] {
      override val resources: AppResources[APP_INFO, LOGGER_T[F], CONFIG] = appResources
      override val logic: Resource[F, Unit] = {
        val info = resources.info
        Resource
          .eval[F, Unit](
            toolkitLogger.info(s"Starting ${info.buildRefName}...") >>
              appProvServices
                .parTraverse[F, Unit](_.use_)
                .onCancel(toolkitLogger.info(s"${info.name} was stopped."))
                .onError(e => toolkitLogger.error(e)(s"${info.name} was stopped due an error."))
                .void
          )
          .onFinalize(toolkitLogger.info(s"Shutting down ${info.name}..."))
      }
    }
  }
}
