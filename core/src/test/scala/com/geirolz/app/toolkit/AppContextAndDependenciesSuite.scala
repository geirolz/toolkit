package com.geirolz.app.toolkit

import cats.effect.{IO, Resource}
import com.geirolz.app.toolkit.logger.ToolkitLogger
import com.geirolz.app.toolkit.testing.{TestAppInfo, TestConfig}

class AppContextAndDependenciesSuite extends munit.FunSuite:

  // false positive not exhaustive pattern matching ? TODO: investigate
  test("AppContext unapply works as expected") {
    val res = App[IO]
      .withInfo(TestAppInfo.value)
      .withPureLogger(ToolkitLogger.console[IO](_))
      .withPureConfig(TestConfig.defaultTest)
      .withoutResources
      .withoutDependencies
      .provideOne(_ => IO.unit)
      .run()
      .void
  }

  // false positive not exhaustive pattern matching ? TODO: investigate
  test("AppDependencies unapply works as expected") {
    App[IO]
      .withInfo(TestAppInfo.value)
      .withPureLogger(ToolkitLogger.console[IO](_))
      .withPureConfig(TestConfig.defaultTest)
      .withoutDependencies
      .provideOne { case _ | AppDependencies(_, _, _, _, _, _) =>
        IO.unit
      }
      .run()
      .void
  }
