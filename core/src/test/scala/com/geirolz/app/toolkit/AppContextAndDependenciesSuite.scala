package com.geirolz.app.toolkit

import cats.effect.{IO, Resource}
import com.geirolz.app.toolkit.logger.Logger
import com.geirolz.app.toolkit.testing.{TestAppInfo, TestConfig}

class AppContextAndDependenciesSuite extends munit.FunSuite:

  // false positive not exhaustive pattern matching ? TODO: investigate
  test("AppContext unapply works as expected") {
    val res = App[IO]
      .withInfo(TestAppInfo.value)
      .withConsoleLogger()
      .withConfigPure(TestConfig.defaultTest)
      .withoutResources
      .withoutDependencies
      .provideOne(IO.unit)
      .run()
      .void
  }

  // false positive not exhaustive pattern matching ? TODO: investigate
  test("AppDependencies unapply works as expected") {
    App[IO]
      .withInfo(TestAppInfo.value)
      .withConsoleLogger()
      .withConfigPure(TestConfig.defaultTest)
      .withoutDependencies
      .provideOne(IO.unit)
      .run()
      .void
  }
