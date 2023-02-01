package com.geirolz.app.toolkit

import cats.effect.{IO, Ref, Resource}
import com.geirolz.app.toolkit.logger.ToolkitLogger
import com.geirolz.app.toolkit.testing.*

import scala.concurrent.duration.DurationInt

class AppTest extends munit.CatsEffectSuite {

  import EventLogger.*
  import cats.syntax.all.*
  import com.geirolz.app.toolkit.error._

  test("Loader and App work as expected with dependsOn and logic fails") {
    EventLogger
      .create[IO]
      .flatMap(logger => {
        implicit val loggerImplicit: EventLogger[IO] = logger
        for {
          counter: Ref[IO, Int] <- IO.ref(0)
          appLoader: Resource[IO, App[IO, Throwable, TestAppInfo, ToolkitLogger, TestConfig]] =
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
          _   <- app.flattenLogic.traceAsAppRuntime.use_

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
      })
  }

  test("Loader released when app provideF fails") {
    EventLogger
      .create[IO]
      .flatMap(logger => {
        implicit val loggerImplicit: EventLogger[IO] = logger
        for {
          appLoader: Resource[IO, App[IO, Throwable, TestAppInfo, ToolkitLogger, TestConfig]] <-
            App[IO]
              .withResourcesLoader(
                AppResources
                  .loader[IO, TestAppInfo](TestAppInfo.value)
                  .withLogger(ToolkitLogger.console[IO])
                  .withConfig(TestConfig.defaultTest)
              )
              .provideF(_ => IO.raiseError(error"BOOM!"))
              .pure[IO]
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
      })
  }

  test("Loader and App work as expected with provide") {
    EventLogger
      .create[IO]
      .flatMap(logger => {
        implicit val loggerImplicit: EventLogger[IO] = logger
        for {
          appLoader: Resource[IO, App[IO, Throwable, TestAppInfo, ToolkitLogger, TestConfig]] <-
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
              .pure[IO]
          app <- appLoader.traceAsAppLoader.use(IO.pure)
          _   <- app.flattenLogic.traceAsAppRuntime.use_

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
      })
  }

  test("Loader and App work as expected with provideOne") {
    EventLogger
      .create[IO]
      .flatMap(logger => {
        implicit val loggerImplicit: EventLogger[IO] = logger
        for {
          appLoader <-
            App[IO]
              .withResourcesLoader(
                AppResources
                  .loader[IO, TestAppInfo](TestAppInfo.value)
                  .withLogger(ToolkitLogger.console[IO])
                  .withConfig(TestConfig.defaultTest)
              )
              .provideOne(_ => IO.sleep(1.second))
              .pure[IO]
          app <- appLoader.traceAsAppLoader.use(IO.pure)
          _   <- app.flattenLogic.traceAsAppRuntime.use_

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
      })
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

    EventLogger
      .create[IO]
      .flatMap(logger => {
        implicit val loggerImplicit: EventLogger[IO] = logger
        for {
          app <- appLoader.traceAsAppLoader.use(IO.pure)
          _   <- app.flattenLogic.traceAsAppRuntime.use_

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
      })
  }

  test("Loader released even if the app crash") {
    val appLoader: Resource[IO, App[IO, Throwable, TestAppInfo, ToolkitLogger, TestConfig]] =
      App[IO]
        .withResourcesLoader(
          AppResources
            .loader[IO, TestAppInfo](TestAppInfo.value)
            .withLogger(ToolkitLogger.console[IO])
            .withConfig(TestConfig.defaultTest)
        )
        .provideOne(_ => IO.raiseError(error"BOOM!"))

    EventLogger
      .create[IO]
      .flatMap(logger => {
        implicit val loggerImplicit: EventLogger[IO] = logger
        for {
          app <- appLoader.traceAsAppLoader.use(IO.pure)
          _   <- app.flattenLogic.traceAsAppRuntime.attempt.use_

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
      })
  }
}
