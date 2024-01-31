package com.geirolz.example.app

import cats.effect.{IO, Resource}
import com.geirolz.app.toolkit.App.ctx
import com.geirolz.app.toolkit.{App, AppContext}
import com.geirolz.example.app.provided.KafkaConsumer
import org.typelevel.log4cats.SelfAwareStructuredLogger

case class AppDependencyServices(
  kafkaConsumer: KafkaConsumer[IO]
)
object AppDependencyServices:

  def resource(using
    AppContext.NoDepsAndRes[AppInfo, SelfAwareStructuredLogger[IO], AppConfig]
  ): Resource[IO, AppDependencyServices] =
    Resource.pure(
      AppDependencyServices(
        KafkaConsumer.fake(ctx.config.kafkaBroker.host)
      )
    )
