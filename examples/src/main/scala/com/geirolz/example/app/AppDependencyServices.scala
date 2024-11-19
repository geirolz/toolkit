package com.geirolz.example.app

import cats.effect.{IO, Resource}
import com.geirolz.app.toolkit.{ctx, AppContext}
import com.geirolz.example.app.provided.{HostTable, KafkaConsumer}

case class AppDependencyServices(
  kafkaConsumer: KafkaConsumer[IO],
  hostTable: HostTable[IO]
)
object AppDependencyServices:

  def resource(using AppMain.CtxNoDeps): Resource[IO, AppDependencyServices] =
    Resource.pure(
      AppDependencyServices(
        kafkaConsumer = KafkaConsumer.fake(ctx.config.kafkaBroker.host),
        hostTable     = HostTable.fromString(ctx.resources.hostTableValues)
      )
    )
