package com.geirolz.app.toolkit

import org.typelevel.log4cats.Logger

package object logger {

  implicit def log4CatsLoggerAdapter[LOG4S_LOGGER[F[_]] <: Logger[F]]: LoggerAdapter[LOG4S_LOGGER] =
    new LoggerAdapter[LOG4S_LOGGER] {
      override def toToolkit[F[_]](appLogger: LOG4S_LOGGER[F]): ToolkitLogger[F] =
        new ToolkitLogger[F] {
          override def info(message: => String): F[Unit] =
            appLogger.info(message)
          override def error(message: => String): F[Unit] =
            appLogger.error(message)
          override def error(ex: Throwable)(message: => String): F[Unit] =
            appLogger.error(ex)(message)
        }
    }
}
