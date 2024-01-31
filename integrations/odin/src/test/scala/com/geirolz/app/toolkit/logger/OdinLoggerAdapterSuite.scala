package com.geirolz.app.toolkit.logger

import cats.effect.IO
import com.geirolz.app.toolkit.{App, SimpleAppInfo}
import com.geirolz.app.toolkit.error.*
import io.odin.Logger as OdinLogger

class OdinLoggerAdapterSuite extends munit.CatsEffectSuite {

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
        .withPureLogger(OdinLogger.noop[IO])
        .withoutDependencies
        .provideOne(IO.unit)
        .run()
        .void
    )
  }

  test("Implicit conversion with Logger") {
    val adapterLogger: LoggerAdapter[OdinLogger] = summon[LoggerAdapter[OdinLogger]]
    val tkLogger                                 = adapterLogger.toToolkit(OdinLogger.noop[IO])

    assertIO_(
      tkLogger.info("msg") >> tkLogger.error(error"BOOM!")("msg")
    )
  }
}
