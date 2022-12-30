package com.geirolz.fpmicroservice.toolkit

import cats.{Parallel, Show}
import cats.effect.{Async, MonadCancel, Resource}
import org.typelevel.log4cats.StructuredLogger

object AppBuilder {

  import cats.effect.syntax.all.*
  import cats.implicits.*

  def build[F[_]: Async: Parallel, CONFIG: Show, APP_INFO <: AppInfo[?], SERVICES](
    appInfo: APP_INFO
  )(
    logger: StructuredLogger[F],
    configLoader: F[CONFIG],
    dependencyServicesBuilder: CONFIG => Resource[F, SERVICES],
    providedServicesBuilder: (CONFIG, SERVICES) => F[List[Resource[F, Unit]]]
  )(implicit c: MonadCancel[F, _]): F[Unit] = {

    val resLogger = logger.mapK(Resource.liftK[F])
    val appResources: Resource[F, List[Resource[F, Unit]]] =
      for {
        // ------------------- CONFIGURATION ------------------
        _      <- resLogger.info("Loading configuration...")
        config <- Resource.eval(configLoader)
        _      <- resLogger.info("Configuration successfully loaded.")
        _      <- resLogger.info(config.show)

        // ---------------------- SERVICES --------------------
        _        <- resLogger.info("Building services environment...")
        services <- dependencyServicesBuilder(config)
        _        <- resLogger.info("Services environment successfully built.")

        // --------------------- RESOURCES --------------------
        _   <- resLogger.info("Building App...")
        app <- Resource.eval(providedServicesBuilder(config, services))
        _   <- resLogger.info("App successfully built.")
      } yield app

    logger.info(s"Starting ${appInfo.buildRefName}...") >>
    appResources
      .onFinalize(logger.info(s"Shutting down ${appInfo.name}..."))
      .use(_.parTraverse[F, Nothing](_.useForever))
      .onCancel(logger.info(s"${appInfo.name} was stopped."))
      .onError(_ => logger.info(s"${appInfo.name} was stopped due an error."))
      .void
  }
}
