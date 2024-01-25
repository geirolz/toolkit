package com.geirolz.app.toolkit.logger

trait LoggerAdapter[LOGGER[_[_]]]:
  def toToolkit[F[_]](appLogger: LOGGER[F]): ToolkitLogger[F]

object LoggerAdapter:
  inline def apply[LOGGER[_[_]]: LoggerAdapter]: LoggerAdapter[LOGGER] = 
    summon[LoggerAdapter[LOGGER]]

  given [L[K[_]] <: ToolkitLogger[K]]: LoggerAdapter[L] =
    new LoggerAdapter[L]:
      override def toToolkit[F[_]](u: L[F]): ToolkitLogger[F] =
        new ToolkitLogger[F]:
          override def error(message: => String): F[Unit]                = u.error(message)
          override def error(ex: Throwable)(message: => String): F[Unit] = u.error(ex)(message)
          override def warn(message: => String): F[Unit]                 = u.warn(message)
          override def warn(ex: Throwable)(message: => String): F[Unit]  = u.warn(ex)(message)
          override def info(message: => String): F[Unit]                 = u.info(message)
          override def info(ex: Throwable)(message: => String): F[Unit]  = u.info(ex)(message)
          override def debug(message: => String): F[Unit]                = u.debug(message)
          override def debug(ex: Throwable)(message: => String): F[Unit] = u.debug(ex)(message)
          override def trace(message: => String): F[Unit]                = u.trace(message)
          override def trace(ex: Throwable)(message: => String): F[Unit] = u.trace(ex)(message)
