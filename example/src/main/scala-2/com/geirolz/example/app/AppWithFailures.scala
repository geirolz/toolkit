package com.geirolz.example.app

import cats.effect.{ExitCode, IO, IOApp}
import com.geirolz.app.toolkit.App
import com.geirolz.app.toolkit.config.pureconfig.syntax.AppResourcesLoaderOps
import com.geirolz.example.app.provided.AppHttpServer
import org.typelevel.log4cats.slf4j.Slf4jLogger

object AppWithFailures extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    App[IO, AppError]
      .withInfo(AppInfo.fromBuildInfo)
      .withLogger(Slf4jLogger.getLogger[IO])
      .withPureConfigLoader[AppConfig]
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
      .beforeRun(_.logger.info("CUSTOM PRE-RUN"))
      .onFinalize(_.logger.info("CUSTOM END"))
      .run
}
