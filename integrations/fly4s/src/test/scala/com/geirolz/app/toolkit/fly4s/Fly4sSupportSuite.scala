package com.geirolz.app.toolkit.fly4s

import cats.effect.IO
import com.geirolz.app.toolkit.fly4s.syntax.*
import com.geirolz.app.toolkit.fly4s.testing.TestConfig
import com.geirolz.app.toolkit.{App, SimpleAppInfo}

class Fly4sSupportSuite extends munit.CatsEffectSuite {

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
        .withConfig(
          TestConfig(
            dbUrl      = "jdbc:postgresql://localhost:5432/toolkit",
            dbUser     = Some("postgres"),
            dbPassword = Some("postgres".toCharArray)
          )
        )
        .withoutDependencies
        .provideOne(_ => IO.unit)
        .beforeProvidingMigrateDatabaseWithConfig(
          url      = _.dbUrl,
          user     = _.dbUser,
          password = _.dbPassword
        )
        .run_
    )
  }
}
