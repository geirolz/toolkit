package com.geirolz.app.toolkit

import cats.effect.{IO, Ref, Resource}
import com.geirolz.app.toolkit.logger.ToolkitLogger
import com.geirolz.app.toolkit.testing.*

import scala.concurrent.duration.DurationInt

class AppTest extends munit.CatsEffectSuite {

  import ErrorSyntax.*
  import EventLogger.*

  test("Loader and App work as expected with dependsOn and logic fails") {
    for {
      case implicit0(logger: EventLogger[IO]) <- EventLogger.create[IO]
      counter: Ref[IO, Int] <- IO.ref(0)
      appLoader: Resource[IO, App[IO, TestAppInfo, ToolkitLogger, TestConfig]] =
        App[IO]
          .withResourcesLoader(
            AppResources
              .loader[IO, TestAppInfo](TestAppInfo.value)
              .withLogger(ToolkitLogger.console[IO])
              .withConfig(TestConfig.defaultTest)
          )
          .dependsOn(_ =>
            Resource.pure[IO, Ref[IO, Int]](counter).trace(LabeledResource.appDependencies)
          )
          .provideOne(_.dependencies.set(1))
      app <- appLoader.traceAsAppLoader.use(IO.pure)
      _   <- app.logic.traceAsAppRuntime.use_

      // assert
      _ <- assertIO(
        obtained = logger.events,
        returns = List(
          // loading resources and dependencies
          LabeledResource.appLoader.starting,
          LabeledResource.appDependencies.starting,
          LabeledResource.appDependencies.succeeded,
          LabeledResource.appLoader.succeeded,
          LabeledResource.appDependencies.finalized,
          LabeledResource.appLoader.finalized,
          // runtime
          LabeledResource.appRuntime.starting,
          LabeledResource.appRuntime.succeeded,
          LabeledResource.appRuntime.finalized
        )
      )
      _ <- assertIO(
        obtained = counter.get,
        returns  = 1
      )
    } yield ()
  }

  test("Loader released when app provideF fails") {
    for {
      case implicit0(logger: EventLogger[IO]) <- EventLogger.create[IO]
      appLoader: Resource[IO, App[IO, TestAppInfo, ToolkitLogger, TestConfig]] =
        App[IO]
          .withResourcesLoader(
            AppResources
              .loader[IO, TestAppInfo](TestAppInfo.value)
              .withLogger(ToolkitLogger.console[IO])
              .withConfig(TestConfig.defaultTest)
          )
          .provideF(_ => IO.raiseError(error"BOOM!"))
      _ <- appLoader.traceAsAppLoader.attempt.use(IO.pure)

      // assert
      _ <- assertIO(
        obtained = logger.events,
        returns = List(
          // loading resources
          LabeledResource.appLoader.starting,
          LabeledResource.appLoader.errored("BOOM!"),
          LabeledResource.appLoader.finalized
        )
      )
    } yield ()
  }

  test("Loader and App work as expected with provide") {
    for {
      case implicit0(logger: EventLogger[IO]) <- EventLogger.create[IO]
      appLoader: Resource[IO, App[IO, TestAppInfo, ToolkitLogger, TestConfig]] =
        App[IO]
          .withResourcesLoader(
            AppResources
              .loader[IO, TestAppInfo](TestAppInfo.value)
              .withLogger(ToolkitLogger.console[IO])
              .withConfig(TestConfig.defaultTest)
          )
          .provide(_ =>
            List(
              IO.sleep(300.millis),
              IO.sleep(50.millis),
              IO.sleep(200.millis)
            )
          )
      app <- appLoader.traceAsAppLoader.use(IO.pure)
      _   <- app.logic.traceAsAppRuntime.use_

      // assert
      _ <- assertIO(
        obtained = logger.events,
        returns = List(
          // loading resources
          LabeledResource.appLoader.starting,
          LabeledResource.appLoader.succeeded,
          LabeledResource.appLoader.finalized,

          // runtime
          LabeledResource.appRuntime.starting,
          LabeledResource.appRuntime.succeeded,
          LabeledResource.appRuntime.finalized
        )
      )
    } yield ()
  }

  test("Loader and App work as expected with provideOne") {
    for {
      case implicit0(logger: EventLogger[IO]) <- EventLogger.create[IO]
      appLoader =
        App[IO]
          .withResourcesLoader(
            AppResources
              .loader[IO, TestAppInfo](TestAppInfo.value)
              .withLogger(ToolkitLogger.console[IO])
              .withConfig(TestConfig.defaultTest)
          )
          .provideOne(_ => IO.sleep(1.second))
      app <- appLoader.traceAsAppLoader.use(IO.pure)
      _   <- app.logic.traceAsAppRuntime.use_

      // assert
      _ <- assertIO(
        obtained = logger.events,
        returns = List(
          // loading resources
          LabeledResource.appLoader.starting,
          LabeledResource.appLoader.succeeded,
          LabeledResource.appLoader.finalized,

          // runtime
          LabeledResource.appRuntime.starting,
          LabeledResource.appRuntime.succeeded,
          LabeledResource.appRuntime.finalized
        )
      )
    } yield ()
  }

  test("Loader and App work as expected with provideOne") {
    val appLoader =
      App[IO]
        .withResourcesLoader(
          AppResources
            .loader[IO, TestAppInfo](TestAppInfo.value)
            .withLogger(ToolkitLogger.console[IO])
            .withConfig(TestConfig.defaultTest)
        )
        .provideOne(_ => IO.unit)

    for {
      case implicit0(logger: EventLogger[IO]) <- EventLogger.create[IO]
      app <- appLoader.traceAsAppLoader.use(IO.pure)
      _   <- app.logic.traceAsAppRuntime.use_

      // assert
      _ <- assertIO(
        obtained = logger.events,
        returns = List(
          // loading resources
          LabeledResource.appLoader.starting,
          LabeledResource.appLoader.succeeded,
          LabeledResource.appLoader.finalized,

          // runtime
          LabeledResource.appRuntime.starting,
          LabeledResource.appRuntime.succeeded,
          LabeledResource.appRuntime.finalized
        )
      )
    } yield ()
  }

  test("Loader released even if the app crash") {
    val appLoader: Resource[IO, App[IO, TestAppInfo, ToolkitLogger, TestConfig]] =
      App[IO]
        .withResourcesLoader(
          AppResources
            .loader[IO, TestAppInfo](TestAppInfo.value)
            .withLogger(ToolkitLogger.console[IO])
            .withConfig(TestConfig.defaultTest)
        )
        .provideOne(_ => IO.raiseError(error"BOOM!"))

    for {
      case implicit0(logger: EventLogger[IO]) <- EventLogger.create[IO]
      app <- appLoader.traceAsAppLoader.use(IO.pure)
      _   <- app.logic.traceAsAppRuntime.attempt.use_

      // assert
      _ <- assertIO(
        obtained = logger.events,
        returns = List(
          // loading resources
          LabeledResource.appLoader.starting,
          LabeledResource.appLoader.succeeded,
          LabeledResource.appLoader.finalized,

          // runtime
          LabeledResource.appRuntime.starting,
          LabeledResource.appRuntime.errored("BOOM!"),
          LabeledResource.appRuntime.finalized
        )
      )
    } yield ()
  }
}
