package com.geirolz.app.toolkit.fly4s

import cats.effect.IO
import com.geirolz.app.toolkit.fly4s.testing.TestConfig
import com.geirolz.app.toolkit.{ctx, App, AppMessages, SimpleAppInfo}

import java.time.LocalDateTime

class Fly4sSupportSuite extends munit.CatsEffectSuite:

  test("Syntax works as expected") {
    App[IO]
      .withInfo(
        name         = "toolkit",
        version      = "0.0.1",
        scalaVersion = "2.13.10",
        sbtVersion   = "1.8.0",
        builtOn      = LocalDateTime.now()
      )
      .withConfigPure(
        TestConfig(
          dbUrl      = "jdbc:postgresql://localhost:5432/toolkit",
          dbUser     = Some("postgres"),
          dbPassword = Some("postgres".toCharArray)
        )
      )
      .withoutDependencies
      .beforeProviding(
        migrateDatabaseWith(
          url      = ctx.config.dbUrl,
          user     = ctx.config.dbUser,
          password = ctx.config.dbPassword
        )
      )
      .provideOne(IO.unit)
  }
