package com.geirolz.example.app

import cats.effect.{ExitCode, IO, IOApp}
import com.geirolz.app.toolkit.{AppResources, *}
import com.geirolz.example.app.provided.AppHttpServer
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object AppMain extends IOApp {

  import com.geirolz.app.toolkit.config.pureconfig.syntax.*

  type AppRes = AppResources[AppInfo, SelfAwareStructuredLogger[IO], AppConfig]

  override def run(args: List[String]): IO[ExitCode] =
    AppBuilder[IO]
      .withResourcesLoader(
        AppResources
          .loader[IO, AppInfo](AppInfo.fromBuildInfo)
          .withLogger(Slf4jLogger.getLogger[IO])
          .withPureConfigLoader[AppConfig]
      )
      .dependsOn(AppDependencyServices.make(_))
      .provide(deps =>
        List(
          // HTTP server
          AppHttpServer.make(deps.config).useForever,

          // Kafka consumer
          deps.dependencies.kafkaConsumer
            .consumeFrom("test-topic")
            .evalTap(record => deps.logger.info(s"Received record $record"))
            .compile
            .drain
        )
      )
      .use(app =>
        app
          .preRun(_.logger.info("CUSTOM PRE-RUN"))
          .onFinalize(_.logger.info("CUSTOM END"))
          .run
          .as(ExitCode.Success)
      )
}
