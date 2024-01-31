package com.geirolz.app.toolkit.logger

import cats.effect.IO
import com.geirolz.app.toolkit.{App, SimpleAppInfo}
import com.geirolz.app.toolkit.error.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

class Log4CatsLoggerAdapterSuite extends munit.CatsEffectSuite {

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
        .withLogger(NoOpLogger[IO])
        .withoutDependencies
        .provideOne(IO.unit)
        .run()
        .void
    )
  }

  test("Implicit conversion with Logger") {
    val adapterLogger: LoggerAdapter[Logger] = implicitly[LoggerAdapter[Logger]]
    val tkLogger                             = adapterLogger.toToolkit(NoOpLogger[IO])

    assertIO_(
      tkLogger.info("msg") >> tkLogger.error(error"BOOM!")("msg")
    )
  }
}
