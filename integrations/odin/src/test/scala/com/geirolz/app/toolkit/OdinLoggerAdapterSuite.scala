package com.geirolz.app.toolkit

import cats.effect.IO
import com.geirolz.app.toolkit.logger.LoggerAdapter
import com.geirolz.app.toolkit.ErrorSyntax.RuntimeExpressionStringCtx
import io.odin.Logger

class OdinLoggerAdapterSuite extends munit.CatsEffectSuite {

  test("Implicit conversion with Logger") {
    val adapterLogger: LoggerAdapter[Logger] = implicitly[LoggerAdapter[Logger]]
    val tkLogger                             = adapterLogger.toToolkit(Logger.noop[IO])

    assertIO_(
      tkLogger.info("msg") >> tkLogger.error(error"BOOM!")("msg")
    )
  }
}
