package com.geirolz.example.app

import cats.Show
import com.comcast.ip4s.{Hostname, Port}
import io.circe.Encoder
import pureconfig.ConfigReader

case class AppConfig(
  httpServer: HttpServerConfig,
  kafkaBroker: KafkaBrokerSetting
)
object AppConfig {

  import io.circe.generic.auto.*
  import io.circe.syntax.*
  import pureconfig.generic.auto.*
  import pureconfig.generic.semiauto.*
  import pureconfig.module.ip4s.*

  implicit val configReader: ConfigReader[AppConfig] = deriveReader[AppConfig]

  // ------------------- CIRCE -------------------
  implicit val hostnameCirceEncoder: Encoder[Hostname] =
    Encoder.encodeString.contramap(_.toString)

  implicit val portCirceEncoder: Encoder[Port] =
    Encoder.encodeInt.contramap(_.value)

  implicit val showInstanceForConfig: Show[AppConfig] = _.asJson.toString()
}

case class HttpServerConfig(port: Port, host: Hostname)

case class KafkaBrokerSetting(host: Hostname)
