package com.geirolz.example.app

import cats.effect.IO
import com.geirolz.app.toolkit.config.pureconfig.pureconfigLoader
import com.geirolz.app.toolkit.logger.given
import com.geirolz.app.toolkit.novalues.NoResources
import com.geirolz.app.toolkit.{ctx, App, AppMessages, IOApp}
import com.geirolz.example.app.provided.AppHttpServer
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import cats.syntax.all.*

object AppWithFailures extends IOApp.Toolkit:
  val app: App[IO, AppError, AppInfo, SelfAwareStructuredLogger, AppConfig, AppResources, AppDependencyServices] =
    App[IO, AppError]
      .withInfo(AppInfo.fromBuildInfo)
      .withLogger(_ => Slf4jLogger.create[IO])
      .withConfig(pureconfigLoader[IO, AppConfig])
      .withResources(AppResources.load)
      .dependsOnE(AppDependencyServices.resource.map(_.asRight))
      .beforeProviding(ctx.logger.info("CUSTOM PRE-RUN"))
      .provideParallel(
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
