package com.geirolz.app.toolkit.config

import _root_.pureconfig.ConfigReader
import _root_.pureconfig.backend.ConfigFactoryWrapper
import com.typesafe.config.Config

class PureconfigSecretSupportSuite extends munit.FunSuite {

  test("Read secret string with pureconfig") {

    val config: Config = ConfigFactoryWrapper
      .parseString(
        """
        |conf {
        | secret-value: "my-super-secret-password"
        |}""".stripMargin
      )
      .toOption
      .get

    val result: ConfigReader.Result[Secret[String]] = implicitly[ConfigReader[Secret[String]]].from(
      config.getValue("conf.secret-value")
    )

    assertEquals(
      obtained = result.map(_.use),
      expected = Right("my-super-secret-password")
    )
  }
}
