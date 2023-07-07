package com.geirolz.example.app

import cats.effect.{IO, Resource}
import com.geirolz.app.toolkit.App
import com.geirolz.app.toolkit.novalues.NoResources
import com.geirolz.example.app.provided.KafkaConsumer
import org.typelevel.log4cats.SelfAwareStructuredLogger

case class AppDependencyServices(
  kafkaConsumer: KafkaConsumer[IO]
)
object AppDependencyServices:

  def resource(res: App.Resources[AppInfo, SelfAwareStructuredLogger[IO], AppConfig, NoResources]): Resource[IO, AppDependencyServices] =
    Resource.pure(
      AppDependencyServices(
        KafkaConsumer.fake(res.config.kafkaBroker.host)
      )
    )
