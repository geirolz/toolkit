package com.geirolz.app.toolkit

import cats.{Monad, Show}
import com.geirolz.app.toolkit.logger.{LoggerAdapter, NoopLogger}
import com.geirolz.app.toolkit.novalues.NoConfig

case class  AppResources[APP_INFO <: BasicAppInfo[?], LOGGER, CONFIG](
  info: APP_INFO,
  logger: LOGGER,
  config: CONFIG
)

object AppResources {

  import cats.syntax.all.*

  def loader[F[_]: Monad, APP_INFO <: BasicAppInfo[?]](
    appInfo: APP_INFO
  ): AppResources.Loader[F, APP_INFO, NoopLogger, NoConfig] = {
    pureLoader(
      AppResources(
        info   = appInfo,
        logger = NoopLogger[F],
        config = NoConfig.value
      )
    )
  }

  def pureLoader[
    F[_]: Monad,
    APP_INFO <: BasicAppInfo[?],
    LOGGER_T[_[_]]: LoggerAdapter,
    CONFIG: Show
  ](
    resources: AppResources[APP_INFO, LOGGER_T[F], CONFIG]
  ): AppResources.Loader[F, APP_INFO, LOGGER_T, CONFIG] =
    AppResources.Loader(
      appInfo       = resources.info,
      loggerBuilder = resources.logger.pure[F],
      configLoader  = resources.config.pure[F]
    )

  final case class Loader[
    F[_]: Monad,
    APP_INFO <: BasicAppInfo[?],
    LOGGER_T[_[_]]: LoggerAdapter,
    CONFIG: Show
  ](
    private[Loader] val appInfo: APP_INFO,
    private[Loader] val loggerBuilder: F[LOGGER_T[F]],
    private[Loader] val configLoader: F[CONFIG]
  ) {

    // ------- LOGGER -------
    def withNoopLogger: AppResources.Loader[F, APP_INFO, NoopLogger, CONFIG] =
      withLogger(logger = NoopLogger[F])

    def withLogger[LOGGER_T2[_[_]]: LoggerAdapter](
      logger: LOGGER_T2[F]
    ): AppResources.Loader[F, APP_INFO, LOGGER_T2, CONFIG] =
      withLogger(loggerF = _ => logger)

    def withLogger[LOGGER_T2[_[_]]: LoggerAdapter](
      loggerF: APP_INFO => LOGGER_T2[F]
    ): AppResources.Loader[F, APP_INFO, LOGGER_T2, CONFIG] =
      withLoggerBuilder(loggerBuilder = appInfo => loggerF(appInfo).pure[F])

    def withLoggerBuilder[LOGGER_T2[_[_]]: LoggerAdapter](
      loggerBuilder: APP_INFO => F[LOGGER_T2[F]]
    ): AppResources.Loader[F, APP_INFO, LOGGER_T2, CONFIG] =
      copy(loggerBuilder = loggerBuilder(appInfo))

    // ------- CONFIG -------
    def withoutConfig: Loader[F, APP_INFO, LOGGER_T, NoConfig] =
      withConfig[NoConfig](NoConfig.value)

    def withConfig[CONFIG2: Show](
      config: CONFIG2
    ): AppResources.Loader[F, APP_INFO, LOGGER_T, CONFIG2] =
      copy(configLoader = config.pure[F])

    def withConfigLoader[CONFIG2: Show](
      configF: APP_INFO => F[CONFIG2]
    ): Loader[F, APP_INFO, LOGGER_T, CONFIG2] =
      copy(configLoader = configF(appInfo))

    def load: F[AppResources[APP_INFO, LOGGER_T[F], CONFIG]] =
      for {
        // --------------------- LOGGER --------------------
        appLogger <- this.loggerBuilder
        tkLogger = LoggerAdapter[LOGGER_T].toToolkit[F](appLogger)

        // ----------------- CONFIGURATION ------------------
        _         <- tkLogger.info("Loading configuration...")
        appConfig <- this.configLoader
        _         <- tkLogger.info("Configuration successfully loaded.")
        _         <- tkLogger.info(appConfig.show)

        // ---------------- GROUP APP RESOURCES --------------
        appResources = AppResources(
          info   = this.appInfo,
          logger = appLogger,
          config = appConfig
        )
      } yield appResources
  }
}
