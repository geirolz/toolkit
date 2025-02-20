package com.geirolz.example.app

import cats.Show
import com.comcast.ip4s.{Hostname, Port}
import io.circe.Encoder
import pureconfig.*
import io.circe.syntax.*
import io.circe.generic.semiauto.*
import pureconfig.module.ip4s.*

case class AppConfig(
  httpServer: HttpServerConfig,
  kafkaBroker: KafkaBrokerSetting
) derives ConfigReader,
      Encoder.AsObject

object AppConfig:
  given Show[AppConfig] =
    Show.show(_.asJson.toString())

case class HttpServerConfig(port: Port, host: Hostname) derives ConfigReader, Encoder.AsObject

case class KafkaBrokerSetting(host: Hostname) derives ConfigReader, Encoder.AsObject

given Encoder[Hostname] =
  Encoder.encodeString.contramap(_.toString)

given Encoder[Port] =
  Encoder.encodeInt.contramap(_.value)
