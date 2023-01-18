package com.geirolz.app.toolkit

import cats.effect.IO
import com.geirolz.app.toolkit.logger.LoggerAdapter
import com.geirolz.app.toolkit.ErrorSyntax.RuntimeExpressionStringCtx
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

class Log4CatsLoggerAdapterSuite extends munit.CatsEffectSuite {

  test("Implicit conversion with Logger") {
    val adapterLogger: LoggerAdapter[Logger] = implicitly[LoggerAdapter[Logger]]
    val tkLogger                             = adapterLogger.toToolkit(NoOpLogger[IO])

    assertIO_(
      tkLogger.info("msg") >> tkLogger.error(error"BOOM!")("msg")
    )
  }
}
