package com.geirolz.app.toolkit

import cats.data.NonEmptyList
import cats.effect.{IO, Ref, Resource}
import com.geirolz.app.toolkit.App.ctx
import com.geirolz.app.toolkit.failure.FailureHandler.OnFailureBehaviour
import com.geirolz.app.toolkit.logger.Logger
import com.geirolz.app.toolkit.novalues.NoFailure
import com.geirolz.app.toolkit.testing.*

import scala.concurrent.duration.DurationInt

class AppSuite extends munit.CatsEffectSuite {

  import EventLogger.*
  import com.geirolz.app.toolkit.error.*
  import cats.syntax.all.*

  test("Loader and App work as expected with dependsOn and logic fails") {
    EventLogger
      .create[IO]
      .flatMap(logger => {
        implicit val loggerImplicit: EventLogger[IO] = logger
        for {
          counter: Ref[IO, Int] <- IO.ref(0)
          _ <- App[IO]
            .withInfo(TestAppInfo.value)
            .withConsoleLogger()
            .withPureConfig(TestConfig.defaultTest)
            .dependsOn(Resource.pure[IO, Ref[IO, Int]](counter).trace(LabeledResource.appDependencies))
            .provideOne(ctx.dependencies.set(1))
            .compile()
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

              // runtime
              LabeledResource.appRuntime.starting,
              LabeledResource.appRuntime.succeeded,

              // finalizing dependencies
              LabeledResource.appRuntime.finalized,
              LabeledResource.appDependencies.finalized,
              LabeledResource.appLoader.finalized
            )
          )
          _ <- assertIO(
            obtained = counter.get,
            returns  = 1
          )
        } yield ()
      })
  }

  test("Loader and dependencies released when app provideOneF fails") {
    EventLogger
      .create[IO]
      .flatMap(logger => {
        implicit val loggerImplicit: EventLogger[IO] = logger
        for {
          _ <- App[IO]
            .withInfo(TestAppInfo.value)
            .withConsoleLogger()
            .withPureConfig(TestConfig.defaultTest)
            .dependsOn(Resource.unit[IO].trace(LabeledResource.appDependencies))
            .provideOneF(IO.raiseError(error"BOOM!"))
            .compile()
            .traceAsAppLoader
            .attempt
            .use_

          // assert
          _ <- assertIO(
            obtained = logger.events,
            returns = List(
              // loading resources
              LabeledResource.appLoader.starting,
              LabeledResource.appDependencies.starting,
              LabeledResource.appDependencies.succeeded,
              LabeledResource.appLoader.errored("BOOM!"),

              // finalizing dependencies
              LabeledResource.appDependencies.finalized,
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
            .withConsoleLogger()
            .withPureConfig(TestConfig.defaultTest)
            .withoutDependencies
            .provide(
              List(
                IO.sleep(300.millis),
                IO.sleep(50.millis),
                IO.sleep(200.millis)
              )
            )
            .compile()
            .runFullTracedApp

          // assert
          _ <- assertIO(
            obtained = logger.events,
            returns = List(
              // loading resources
              LabeledResource.appLoader.starting,
              LabeledResource.appLoader.succeeded,

              // runtime
              LabeledResource.appRuntime.starting,
              LabeledResource.appRuntime.succeeded,

              // finalizing dependencies
              LabeledResource.appRuntime.finalized,
              LabeledResource.appLoader.finalized
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
            .withConsoleLogger()
            .withPureConfig(TestConfig.defaultTest)
            .withoutDependencies
            .provideOne(IO.sleep(1.second))
            .compile()
            .runFullTracedApp

          // assert
          _ <- assertIO(
            obtained = logger.events,
            returns = List(
              // loading resources
              LabeledResource.appLoader.starting,
              LabeledResource.appLoader.succeeded,

              // runtime
              LabeledResource.appRuntime.starting,
              LabeledResource.appRuntime.succeeded,

              // finalizing dependencies
              LabeledResource.appRuntime.finalized,
              LabeledResource.appLoader.finalized
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
            .withConsoleLogger()
            .withPureConfig(TestConfig.defaultTest)
            .withoutDependencies
            .provideF(
              IO(
                List(
                  IO.sleep(300.millis),
                  IO.sleep(50.millis),
                  IO.sleep(200.millis)
                )
              )
            )
            .compile()
            .runFullTracedApp

          // assert
          _ <- assertIO(
            obtained = logger.events,
            returns = List(
              // loading resources
              LabeledResource.appLoader.starting,
              LabeledResource.appLoader.succeeded,

              // runtime
              LabeledResource.appRuntime.starting,
              LabeledResource.appRuntime.succeeded,

              // finalizing dependencies
              LabeledResource.appRuntime.finalized,
              LabeledResource.appLoader.finalized
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
              .withConsoleLogger()
              .withPureConfig(TestConfig.defaultTest)
              .dependsOn(Resource.pure[IO, Unit](()).trace(LabeledResource.appDependencies))
              .provideOne(IO.raiseError(error"BOOM!"))
              .compile()
              .runFullTracedApp
              .attempt

          // assert
          _ <- assertIO(
            obtained = logger.events,
            returns = List(
              // loading resources
              LabeledResource.appLoader.starting,
              LabeledResource.appDependencies.starting,
              LabeledResource.appDependencies.succeeded,
              LabeledResource.appLoader.succeeded,

              // runtime
              LabeledResource.appRuntime.starting,
              LabeledResource.appRuntime.errored("BOOM!"),

              // finalizing dependencies
              LabeledResource.appRuntime.finalized,
              LabeledResource.appDependencies.finalized,
              LabeledResource.appLoader.finalized
            )
          )
        } yield ()
      })
  }

  test("beforeProviding and onFinalize with List work as expected") {
    EventLogger
      .create[IO]
      .flatMap(logger => {
        given EventLogger[IO] = logger
        for {
          _ <- App[IO]
            .withInfo(TestAppInfo.value)
            .withConsoleLogger()
            .withPureConfig(TestConfig.defaultTest)
            .withoutDependencies
            .beforeProviding(
              List(
                logger.append(Event.Custom("beforeProviding_1")),
                logger.append(Event.Custom("beforeProviding_2")),
                logger.append(Event.Custom("beforeProviding_3"))
              ).sequence_
            )
            .provideOne(logger.append(Event.Custom("provide")))
            .onFinalize(
              List(
                logger.append(Event.Custom("onFinalize_1")),
                logger.append(Event.Custom("onFinalize_2")),
                logger.append(Event.Custom("onFinalize_3"))
              ).sequence_
            )
            .compile()
            .runFullTracedApp

          // assert
          _ <- assertIO(
            obtained = logger.events,
            returns = List(
              // loading resources
              LabeledResource.appLoader.starting,
              LabeledResource.appLoader.succeeded,

              // runtime
              LabeledResource.appRuntime.starting,

              // before providing
              Event.Custom("beforeProviding_1"),
              Event.Custom("beforeProviding_2"),
              Event.Custom("beforeProviding_3"),

              // providing
              Event.Custom("provide"),

              // on finalize
              Event.Custom("onFinalize_1"),
              Event.Custom("onFinalize_2"),
              Event.Custom("onFinalize_3"),

              // runtime
              LabeledResource.appRuntime.succeeded,

              // finalizing dependencies
              LabeledResource.appRuntime.finalized,
              LabeledResource.appLoader.finalized
            )
          )
        } yield ()
      })
  }

  test("App can use args from Run method") {

    for {
      state <- IO.ref[Boolean](false)
      _ <- App[IO]
        .withInfo(TestAppInfo.value)
        .withConsoleLogger()
        .withPureConfig(TestConfig.defaultTest)
        .withoutDependencies
        .provideOne(
          state.set(
            ctx.args.exists(
              _.getVar[Int]("arg1").contains(1),
              _.hasFlags("verbose", "debug")
            )
          )
        )
        .run(List("arg1=1", "verbose", "debug"))
        .void

      // assert
      _ <- assertIO(
        obtained = state.get,
        returns  = true
      )
    } yield ()
  }

  test("Custom Error with CancelAll") {

    trait AppError
    object AppError {
      case class Boom() extends AppError
    }

    val test =
      for {
        state <- IO.ref[Boolean](false)
        app <- App[IO, AppError]
          .withInfo(TestAppInfo.value)
          .withConsoleLogger()
          .withPureConfig(TestConfig.defaultTest)
          .withoutDependencies
          .provideE(
            List(
              IO(Left(AppError.Boom())),
              IO.sleep(1.seconds) >> IO(Left(AppError.Boom())),
              IO.sleep(5.seconds) >> state.set(true).as(Right(()))
            )
          )
          .onFailure_(failures => ctx.logger.error(failures.toString))
          .runRaw()
        finalState <- state.get
      } yield (finalState, app)

    assertIO_(
      test.map { case (finalState, appResult) =>
        assertEquals(
          obtained = finalState,
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
          .withConsoleLogger()
          .withPureConfig(TestConfig.defaultTest)
          .withoutDependencies
          .provideE {
            List(
              IO(Left(AppError.Boom())),
              IO.sleep(1.seconds) >> IO(Left(AppError.Boom())),
              IO.sleep(1.seconds) >> state.set(true).as(Right(()))
            )
          }
          .onFailure((failure: AppError) => ctx.logger.error(failure.toString).as(OnFailureBehaviour.DoNothing))
          .runRaw()
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
