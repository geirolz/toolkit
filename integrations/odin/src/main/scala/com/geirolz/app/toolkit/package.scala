package com.geirolz.app

import com.geirolz.app.toolkit.logger.{LoggerAdapter, ToolkitLogger}
import io.odin.Logger

package object toolkit {

  implicit def odinLoggerAdapter[ODIN_LOGGER[F[_]] <: Logger[F[_]]]: LoggerAdapter[ODIN_LOGGER] =
    new LoggerAdapter[ODIN_LOGGER] {
      override def toToolkit[F[_]](odinLogger: ODIN_LOGGER[F]): ToolkitLogger[F] =
        new ToolkitLogger[F] {
          override def info(message: => String): F[Unit] =
            odinLogger.info(message)
          override def error(ex: Throwable)(message: => String): F[Unit] =
            odinLogger.error(message, ex)
        }
    }
}
