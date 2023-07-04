package com.geirolz.app.toolkit.logger

import cats.Applicative

sealed trait NoopLogger[F[_]] extends ToolkitLogger[F]
object NoopLogger {
  def apply[F[_]: Applicative]: NoopLogger[F] = new NoopLogger[F] {
    override def error(message: => String): F[Unit]                = Applicative[F].unit
    override def error(ex: Throwable)(message: => String): F[Unit] = Applicative[F].unit
    override def warn(message: => String): F[Unit]                 = Applicative[F].unit
    override def warn(ex: Throwable)(message: => String): F[Unit]  = Applicative[F].unit
    override def info(message: => String): F[Unit]                 = Applicative[F].unit
    override def info(ex: Throwable)(message: => String): F[Unit]  = Applicative[F].unit
    override def debug(message: => String): F[Unit]                = Applicative[F].unit
    override def debug(ex: Throwable)(message: => String): F[Unit] = Applicative[F].unit
    override def trace(message: => String): F[Unit]                = Applicative[F].unit
    override def trace(ex: Throwable)(message: => String): F[Unit] = Applicative[F].unit
  }
}
