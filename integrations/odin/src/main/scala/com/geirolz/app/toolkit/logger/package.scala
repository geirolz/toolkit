package com.geirolz.app.toolkit

import io.odin.{consoleLogger, Logger}

package object logger {

  implicit def odinLoggerAdapter[ODIN_LOGGER[F[_]] <: Logger[F]]: LoggerAdapter[ODIN_LOGGER] =
    new LoggerAdapter[ODIN_LOGGER] {
      override def toToolkit[F[_]](u: ODIN_LOGGER[F]): ToolkitLogger[F] =
        new ToolkitLogger[F] {
          override def info(message: => String): F[Unit]                 = u.info(message)
          override def info(ex: Throwable)(message: => String): F[Unit]  = u.info(message, ex)
          override def warn(message: => String): F[Unit]                 = u.warn(message)
          override def warn(ex: Throwable)(message: => String): F[Unit]  = u.warn(message, ex)
          override def error(message: => String): F[Unit]                = u.error(message)
          override def error(ex: Throwable)(message: => String): F[Unit] = u.error(message, ex)
          override def debug(message: => String): F[Unit]                = u.debug(message)
          override def debug(ex: Throwable)(message: => String): F[Unit] = u.debug(message, ex)
          override def trace(message: => String): F[Unit]                = u.trace(message)
          override def trace(ex: Throwable)(message: => String): F[Unit] = u.trace(message, ex)
        }
    }
}
