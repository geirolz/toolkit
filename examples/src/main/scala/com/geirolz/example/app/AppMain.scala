package com.geirolz.example.app

import cats.effect.{ExitCode, IO, IOApp}
import com.geirolz.app.toolkit.App
import com.geirolz.app.toolkit.config.pureconfig.*
import com.geirolz.app.toolkit.logger.given
import com.geirolz.app.toolkit.novalues.NoResources
import com.geirolz.example.app.provided.AppHttpServer
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object AppMain extends IOApp:
  override def run(args: List[String]): IO[ExitCode] =
    App[IO]
      .withInfo(AppInfo.fromBuildInfo)
      .withPureLogger(Slf4jLogger.getLogger[IO])
      .withConfigF(pureconfigLoader[IO, AppConfig])
      .dependsOn(AppDependencyServices.resource)
      .beforeProvidingSeq(_.logger.info("CUSTOM PRE-RUN"))
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
      .onFinalizeSeq(_.logger.info("CUSTOM END"))
      .run(args)
