package com.geirolz.example.app

import cats.effect.IO
import cats.syntax.all.given
import com.geirolz.app.toolkit.{App, IOApp}
import com.geirolz.app.toolkit.config.pureconfig.*
import com.geirolz.app.toolkit.logger.given
import com.geirolz.app.toolkit.novalues.{NoFailure, NoResources}
import com.geirolz.example.app.provided.AppHttpServer
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object AppMain extends IOApp.Toolkit:
  val app: App[IO, NoFailure, AppInfo, SelfAwareStructuredLogger, AppConfig, NoResources, AppDependencyServices] =
    App[IO]
      .withInfo(AppInfo.fromBuildInfo)
      .withLogger(Slf4jLogger.getLogger[IO])
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
