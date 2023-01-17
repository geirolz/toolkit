package com.geirolz.example.app

import cats.effect.{IO, Resource}
import com.geirolz.app.toolkit.AppResources
import com.geirolz.app.toolkit.logger.ToolkitLogger
import com.geirolz.example.app.provided.KafkaConsumer

case class AppDependencyServices(
  kafkaConsumer: KafkaConsumer[IO]
)
object AppDependencyServices {

  def make(
    res: AppResources[AppInfo, ToolkitLogger[IO], AppConfig]
  ): Resource[IO, AppDependencyServices] =
    Resource.pure(
      AppDependencyServices(
        KafkaConsumer.fake(res.config.kafkaBroker.host)
      )
    )
}
