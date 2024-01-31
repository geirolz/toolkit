package com.geirolz.app.toolkit.logger

import org.typelevel.log4cats.Logger as Log4catsLogger

given [LOG4S_LOGGER[F[_]] <: Log4catsLogger[F]]: LoggerAdapter[LOG4S_LOGGER] =
  new LoggerAdapter[LOG4S_LOGGER]:
    override def toToolkit[F[_]](u: LOG4S_LOGGER[F]): Logger[F] =
      new Logger[F]:
        override def error(message: => String): F[Unit]                  = u.error(message)
        override def error(ex: Throwable)(message: => String): F[Unit]   = u.error(ex)(message)
        override def failure(message: => String): F[Unit]                = u.error(message)
        override def failure(ex: Throwable)(message: => String): F[Unit] = u.error(ex)(message)
        override def warn(message: => String): F[Unit]                   = u.warn(message)
        override def warn(ex: Throwable)(message: => String): F[Unit]    = u.warn(ex)(message)
        override def info(message: => String): F[Unit]                   = u.info(message)
        override def info(ex: Throwable)(message: => String): F[Unit]    = u.info(ex)(message)
        override def debug(message: => String): F[Unit]                  = u.debug(message)
        override def debug(ex: Throwable)(message: => String): F[Unit]   = u.debug(ex)(message)
        override def trace(message: => String): F[Unit]                  = u.trace(message)
        override def trace(ex: Throwable)(message: => String): F[Unit]   = u.trace(ex)(message)
