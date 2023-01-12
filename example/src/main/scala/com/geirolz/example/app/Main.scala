package com.geirolz.example.app

import cats.effect.{ExitCode, IO, IOApp}
import com.geirolz.app.toolkit.{App, AppResources}
import com.geirolz.app.toolkit.logger.ToolkitLogger
import pureconfig.ConfigSource

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    App[IO]
      .withResourcesLoader(
        AppResources
          .loader[IO, AppInfo](AppInfo.fromBuildInfo)
          .withLogger(ToolkitLogger.console[IO](_))
          .withConfigLoader(_ => IO(ConfigSource.default.loadOrThrow[AppConfig]))
      )
      .dependsOn(AppDependencyServices.make(_))
      .provideOne(deps => AppHttpService.make(deps.resources.config))
      .use(app =>
        app
          .preRun(_.logger.info("CUSTOM PRE-RUN"))
          .onFinalize(_.logger.info("CUSTOM END"))
          .runForever
          .as(ExitCode.Success)
      )
}
