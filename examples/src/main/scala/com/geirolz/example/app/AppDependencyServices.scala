package com.geirolz.example.app

import cats.effect.{IO, Resource}
import com.geirolz.app.toolkit.{ctx, AppContext}
import com.geirolz.example.app.provided.{HostTable, KafkaConsumer}
import org.typelevel.log4cats.SelfAwareStructuredLogger

case class AppDependencyServices(
  kafkaConsumer: KafkaConsumer[IO],
  hostTable: HostTable[IO]
)
object AppDependencyServices:

  def resource(using
    AppContext.NoDeps[AppInfo, SelfAwareStructuredLogger[IO], AppConfig, AppResources]
  ): Resource[IO, AppDependencyServices] =
    Resource.pure(
      AppDependencyServices(
        kafkaConsumer = KafkaConsumer.fake(ctx.config.kafkaBroker.host),
        hostTable     = HostTable.fromString(ctx.resources.hostTableValues)
      )
    )
