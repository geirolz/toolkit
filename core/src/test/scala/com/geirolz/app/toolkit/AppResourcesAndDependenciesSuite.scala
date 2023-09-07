package com.geirolz.app.toolkit

import cats.effect.{IO, Resource}
import com.geirolz.app.toolkit.logger.ToolkitLogger
import com.geirolz.app.toolkit.testing.{TestAppInfo, TestConfig}

class AppResourcesAndDependenciesSuite extends munit.FunSuite {

  // false positive not exhaustive pattern matching ? TODO: investigate
  test("App.Resources unapply works as expected") {
    App[IO]
      .withInfo(TestAppInfo.value)
      .withLogger(ToolkitLogger.console[IO](_))
      .withConfig(TestConfig.defaultTest)
      .dependsOn { case _ | App.Resources(_, _, _, _, _) =>
        Resource.eval(IO.unit)
      }
      .provideOne(_ => IO.unit)
      .run_
  }

  // false positive not exhaustive pattern matching ? TODO: investigate
  test("App.Dependencies unapply works as expected") {
    App[IO]
      .withInfo(TestAppInfo.value)
      .withLogger(ToolkitLogger.console[IO](_))
      .withConfig(TestConfig.defaultTest)
      .withoutDependencies
      .provideOne { case _ | App.Dependencies(_, _, _, _, _, _) =>
        IO.unit
      }
      .run_
  }

}
