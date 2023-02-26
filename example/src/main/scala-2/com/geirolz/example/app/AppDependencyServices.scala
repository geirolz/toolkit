package com.geirolz.example.app

import cats.effect.{IO, Resource}
import com.geirolz.app.toolkit.AppResources
import com.geirolz.example.app.provided.KafkaConsumer
import org.typelevel.log4cats.SelfAwareStructuredLogger

case class AppDependencyServices(
  kafkaConsumer: KafkaConsumer[IO]
)
object AppDependencyServices {

  def resource(
    res: AppResources[AppInfo, SelfAwareStructuredLogger[IO], AppConfig]
  ): Resource[IO, AppDependencyServices] =
    Resource.pure(
      AppDependencyServices(
        KafkaConsumer.fake(res.config.kafkaBroker.host)
      )
    )
}
