package com.geirolz.app.toolkit.config

import _root_.pureconfig.ConfigReader
import _root_.pureconfig.backend.ConfigFactoryWrapper
import cats.effect.IO
import com.geirolz.app.toolkit.config.pureconfig.pureconfigLoader
import com.geirolz.app.toolkit.config.testing.TestConfig
import com.geirolz.app.toolkit.{App, SimpleAppInfo}
import com.typesafe.config.Config

class PureconfigSecretSupportSuite extends munit.CatsEffectSuite {

  test("Syntax works as expected") {
    assertIO_(
      App[IO]
        .withInfo(
          SimpleAppInfo.string(
            name         = "toolkit",
            version      = "0.0.1",
            scalaVersion = "2.13.10",
            sbtVersion   = "1.8.0"
          )
        )
        .withConfigLoader(pureconfigLoader[IO, TestConfig])
        .withoutDependencies
        .provideOne(_ => IO.unit)
        .run_
    )
  }

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

    assert(
      result
        .flatMap(_.useE(secretValue => {
          assertEquals(
            obtained = secretValue,
            expected = "my-super-secret-password"
          )
        }))
        .isRight
    )

  }
}
