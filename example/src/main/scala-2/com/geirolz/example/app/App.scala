package com.geirolz.example.app

import cats.effect.{ExitCode, IO, IOApp}
import com.geirolz.app.toolkit.{AppBuilder, AppResources}
import com.geirolz.app.toolkit.config.pureconfig.syntax.*
import com.geirolz.example.app.provided.AppHttpServer
import org.typelevel.log4cats.slf4j.Slf4jLogger

object App extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    AppBuilder[IO]
      .withResourcesLoader(
        AppResources
          .loader[IO, AppInfo](AppInfo.fromBuildInfo)
          .withLogger(Slf4jLogger.getLogger[IO])
          .withPureConfigLoader[AppConfig]
      )
      .dependsOn(AppDependencyServices.resource(_))
      .provide(deps =>
        List(
          // HTTP server
          AppHttpServer.resource(deps.config).useForever,

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
