package com.geirolz.app

import com.geirolz.app.toolkit.logger.{LoggerAdapter, ToolkitLogger}
import org.typelevel.log4cats.Logger

package object toolkit {

  implicit def log4CatsLoggerAdapter[LOG4S_LOGGER[F[_]] <: Logger[F[_]]]
    : LoggerAdapter[LOG4S_LOGGER] =
    new LoggerAdapter[LOG4S_LOGGER] {
      override def toToolkit[F[_]](appLogger: LOG4S_LOGGER[F]): ToolkitLogger[F] =
        new ToolkitLogger[F] {
          override def info(message: => String): F[Unit] =
            appLogger.info(message)
          override def error(ex: Throwable)(message: => String): F[Unit] =
            appLogger.error(ex)(message)
        }
    }
}
