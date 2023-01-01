package com.geirolz.app.toolkit.logger

import cats.Applicative

sealed trait NoopLogger[F[_]] extends ToolkitLogger[F]
object NoopLogger {
  def apply[F[_]: Applicative]: NoopLogger[F] = new NoopLogger[F] {
    override def info(message: => String): F[Unit]                 = Applicative[F].unit
    override def error(ex: Throwable)(message: => String): F[Unit] = Applicative[F].unit
  }
}
