package com.geirolz.example.app

import cats.effect.{IO, Resource}
import com.geirolz.example.app.provided.KafkaConsumer
import com.geirolz.example.app.AppMain.AppRes

case class AppDependencyServices(
  kafkaConsumer: KafkaConsumer[IO]
)
object AppDependencyServices {

  def resource(res: AppRes): Resource[IO, AppDependencyServices] =
    Resource.pure(
      AppDependencyServices(
        KafkaConsumer.fake(res.config.kafkaBroker.host)
      )
    )
}
