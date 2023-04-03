package com.geirolz.app.toolkit

import cats.data.NonEmptyList
import cats.effect.{IO, Ref, Resource}
import com.geirolz.app.toolkit.logger.ToolkitLogger
import com.geirolz.app.toolkit.testing.*
import com.geirolz.app.toolkit.FailureHandler.OnFailureBehaviour

import scala.concurrent.duration.DurationInt

class AppTest extends munit.CatsEffectSuite {

  import EventLogger.*
  import com.geirolz.app.toolkit.error.*

  test("Loader and App work as expected with dependsOn and logic fails") {
    EventLogger
      .create[IO]
      .flatMap(logger => {
        implicit val loggerImplicit: EventLogger[IO] = logger
        for {
          counter: Ref[IO, Int] <- IO.ref(0)
          _ <- App[IO]
            .withInfo(TestAppInfo.value)
            .withLogger(ToolkitLogger.console[IO])
            .withConfig(TestConfig.defaultTest)
            .dependsOn(_ => Resource.pure[IO, Ref[IO, Int]](counter).trace(LabeledResource.appDependencies))
            .provideOne(_.dependencies.set(1))
            .compile
            .runFullTracedApp

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

  test("Loader released when app provideOneF fails") {
    EventLogger
      .create[IO]
      .flatMap(logger => {
        implicit val loggerImplicit: EventLogger[IO] = logger
        for {
          _ <- App[IO]
            .withInfo(TestAppInfo.value)
            .withLogger(ToolkitLogger.console[IO])
            .withConfig(TestConfig.defaultTest)
            .withoutDependencies
            .provideOneF(_ => IO.raiseError(ex"BOOM!"))
            .compile
            .traceAsAppLoader
            .attempt
            .use_

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
          _ <- App[IO]
            .withInfo(TestAppInfo.value)
            .withLogger(ToolkitLogger.console[IO])
            .withConfig(TestConfig.defaultTest)
            .withoutDependencies
            .provide(_ =>
              List(
                IO.sleep(300.millis),
                IO.sleep(50.millis),
                IO.sleep(200.millis)
              )
            )
            .compile
            .runFullTracedApp

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
          _ <- App[IO]
            .withInfo(TestAppInfo.value)
            .withLogger(ToolkitLogger.console[IO])
            .withConfig(TestConfig.defaultTest)
            .withoutDependencies
            .provideOne(_ => IO.sleep(1.second))
            .compile
            .runFullTracedApp

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

  test("Loader and App work as expected with provideF") {
    EventLogger
      .create[IO]
      .flatMap(logger => {
        implicit val loggerImplicit: EventLogger[IO] = logger
        for {
          _ <- App[IO]
            .withInfo(TestAppInfo.value)
            .withLogger(ToolkitLogger.console[IO])
            .withConfig(TestConfig.defaultTest)
            .withoutDependencies
            .provideF(_ =>
              IO(
                List(
                  IO.sleep(300.millis),
                  IO.sleep(50.millis),
                  IO.sleep(200.millis)
                )
              )
            )
            .compile
            .runFullTracedApp

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

  test("Loader released even if the app crash - provideOneF") {

    EventLogger
      .create[IO]
      .flatMap(logger => {
        implicit val loggerImplicit: EventLogger[IO] = logger
        for {
          _ <-
            App[IO]
              .withInfo(TestAppInfo.value)
              .withLogger(ToolkitLogger.console[IO])
              .withConfig(TestConfig.defaultTest)
              .withoutDependencies
              .provideOne(_ => IO.raiseError(ex"BOOM!"))
              .compile
              .runFullTracedApp
              .attempt

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

  test("Custom Error with CancelAll") {

    trait AppError
    object AppError {
      case class Boom() extends AppError
    }

    val test: IO[(Boolean, NonEmptyList[AppError] \/ Unit)] =
      for {
        state <- IO.ref[Boolean](false)
        app <- App[IO, AppError]
          .withInfo(TestAppInfo.value)
          .withLogger(ToolkitLogger.console[IO])
          .withConfig(TestConfig.defaultTest)
          .withoutDependencies
          .provide(_ =>
            List(
              IO(Left(AppError.Boom())),
              IO.sleep(1.seconds) >> IO(Left(AppError.Boom())),
              IO.sleep(5.seconds) >> state.set(true).as(Right(()))
            )
          )
          .onFailure_(res =>
            res.useTupledAll[IO[Unit]] { case (_, _, logger, failures, _) =>
              logger.error(failures.toString)
            }
          )
          .run(identity)
        finalState <- state.get
      } yield (finalState, app)

    assertIO_(
      test.map { case (state, appResult) =>
        assertEquals(
          obtained = state,
          expected = false
        )
        assert(cond = appResult.isLeft)
      }
    )
  }

  test("Custom Error with DoNothing") {

    trait AppError
    object AppError {
      case class Boom() extends AppError
    }

    val test: IO[(Boolean, NonEmptyList[AppError] \/ Unit)] =
      for {
        state <- IO.ref[Boolean](false)
        app <- App[IO, AppError]
          .withInfo(TestAppInfo.value)
          .withLogger(ToolkitLogger.console[IO])
          .withConfig(TestConfig.defaultTest)
          .withoutDependencies
          .provide { _ =>
            List(
              IO(Left(AppError.Boom())),
              IO.sleep(1.seconds) >> IO(Left(AppError.Boom())),
              IO.sleep(1.seconds) >> state.set(true).as(Right(()))
            )
          }
          .onFailure(_.useTupledAll { case (_, _, logger, failures, _) =>
            logger.error(failures.toString).as(OnFailureBehaviour.DoNothing)
          })
          .run(identity)
        finalState <- state.get
      } yield (finalState, app)

    assertIO_(
      test.map { case (state, appResult) =>
        assertEquals(
          obtained = state,
          expected = true
        )
        assertEquals(
          obtained = appResult.left.toOption.get.toList.size,
          expected = 2
        )
      }
    )
  }

}
