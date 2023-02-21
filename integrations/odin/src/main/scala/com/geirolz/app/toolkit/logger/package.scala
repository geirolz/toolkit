package com.geirolz.app.toolkit

import io.odin.Logger

package object logger {

  implicit def odinLoggerAdapter[ODIN_LOGGER[F[_]] <: Logger[F]]: LoggerAdapter[ODIN_LOGGER] =
    new LoggerAdapter[ODIN_LOGGER] {
      override def toToolkit[F[_]](odinLogger: ODIN_LOGGER[F]): ToolkitLogger[F] =
        new ToolkitLogger[F] {
          override def info(message: => String): F[Unit] =
            odinLogger.info(message)
          override def error(message: => String): F[Unit] =
            odinLogger.error(message)
          override def error(ex: Throwable)(message: => String): F[Unit] =
            odinLogger.error(message, ex)
        }
    }
}
