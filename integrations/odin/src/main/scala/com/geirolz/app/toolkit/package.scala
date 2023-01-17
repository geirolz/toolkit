package com.geirolz.app

import com.geirolz.app.toolkit.logger.{LoggerAdapter, ToolkitLogger}
import io.odin.Logger

package object toolkit {

  implicit def odinLoggerAdapter[ODIN_LOGGER[_[_]] <: Logger[_[_]]]: LoggerAdapter[ODIN_LOGGER] =
    new LoggerAdapter[ODIN_LOGGER] {
      override def toToolkit[F[_]](appLogger: ODIN_LOGGER[F]): ToolkitLogger[F] =
        new ToolkitLogger[F] {
          override def info(message: => String): F[Unit] =
            appLogger.info(message)
          override def error(ex: Throwable)(message: => String): F[Unit] =
            appLogger.error(message, ex)
        }
    }
}
