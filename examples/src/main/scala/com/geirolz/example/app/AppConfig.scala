package com.geirolz.example.app

import cats.Show
import com.comcast.ip4s.{Hostname, Port}
import io.circe.Encoder
import pureconfig.*
import pureconfig.generic.derivation.default.*

case class AppConfig(
  httpServer: HttpServerConfig,
  kafkaBroker: KafkaBrokerSetting
) derives ConfigReader

object AppConfig:

  import io.circe.syntax.*
  import io.circe.generic.semiauto.*
  import pureconfig.module.ip4s.*

  // ------------------- CIRCE -------------------
  given Encoder[Hostname] =
    Encoder.encodeString.contramap(_.toString)

  given Encoder[Port] =
    Encoder.encodeInt.contramap(_.value)

  given Encoder[AppConfig] =
    deriveEncoder[AppConfig]

  given Show[AppConfig] =
    Show.show(_.asJson.toString())

end AppConfig

case class HttpServerConfig(port: Port, host: Hostname)

case class KafkaBrokerSetting(host: Hostname)
