package com.geirolz.example.app

import cats.effect.{ExitCode, IO, IOApp}
import com.geirolz.app.toolkit.*
import com.geirolz.app.toolkit.config.pureconfig.*
import com.geirolz.app.toolkit.logger.given
import com.geirolz.app.toolkit.novalues.NoResources
import com.geirolz.example.app.provided.AppHttpServer
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import cats.syntax.all.given

object AppMain extends IOApp:
  override def run(args: List[String]): IO[ExitCode] =
    App[IO]
      .withInfo(AppInfo.fromBuildInfo)
      .withPureLogger(Slf4jLogger.getLogger[IO])
      .withConfigF(pureconfigLoader[IO, AppConfig])
      .dependsOnE(AppDependencyServices.resource.map(_.asRight))
      .beforeProviding(ctx.logger.info("CUSTOM PRE-RUN"))
      .provide(
        List(
          // HTTP server
          AppHttpServer.resource(ctx.config).useForever,

          // Kafka consumer
          ctx.dependencies.kafkaConsumer
            .consumeFrom("test-topic")
            .evalTap(record => ctx.logger.info(s"Received record $record"))
            .compile
            .drain
        )
      )
      .onFinalize(ctx.logger.info("CUSTOM END"))
      .run(args)
