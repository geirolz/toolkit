package com.geirolz.app.toolkit

import pureconfig.ConfigReader

import java.nio.charset.StandardCharsets

package object config {

  implicit val configReaderForSecretString: ConfigReader[Secret] =
    ConfigReader.stringConfigReader
      .map(str => new Secret(str.getBytes(StandardCharsets.UTF_8)))
}
