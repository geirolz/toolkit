package com.geirolz.app.toolkit.config

import cats.Show
import pureconfig.ConfigReader

import java.nio.charset.StandardCharsets

class Secret(private val value: Array[Byte]) {

  def stringValue: String = new String(value, StandardCharsets.UTF_8)

  override def toString: String = Secret.placeHolder
}
object Secret {

  private val placeHolder = "** MASKED **"

  def apply(value: String): Secret =
    new Secret(value.getBytes(StandardCharsets.UTF_8))

  implicit val showInstanceForSecretString: Show[Secret] =
    _ => Secret.placeHolder

  implicit val configReaderForSecretString: ConfigReader[Secret] =
    ConfigReader.stringConfigReader
      .map(str => new Secret(str.getBytes(StandardCharsets.UTF_8)))
}
