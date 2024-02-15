package com.geirolz.app.toolkit.logger

import cats.kernel.Order
import cats.{~>, Show}

trait Logger[F[_]]:
  def error(message: => String): F[Unit]
  def error(ex: Throwable)(message: => String): F[Unit]
  def failure(message: => String): F[Unit]
  def failure(ex: Throwable)(message: => String): F[Unit]
  def warn(message: => String): F[Unit]
  def warn(ex: Throwable)(message: => String): F[Unit]
  def info(message: => String): F[Unit]
  def info(ex: Throwable)(message: => String): F[Unit]
  def debug(message: => String): F[Unit]
  def debug(ex: Throwable)(message: => String): F[Unit]
  def trace(message: => String): F[Unit]
  def trace(ex: Throwable)(message: => String): F[Unit]
  def mapK[G[_]](nat: F ~> G): Logger[G] = Logger.mapK(this)(nat)

object Logger:

  import cats.implicits.*

  export NoopLogger.apply as noop
  export ConsoleLogger.apply as console

  sealed trait Level:
    def index: Int =
      this match
        case Level.Error   => 5
        case Level.Failure => 4
        case Level.Warn    => 3
        case Level.Info    => 2
        case Level.Debug   => 1
        case Level.Trace   => 0

    override def toString: String =
      this match
        case Level.Error   => "ERROR"
        case Level.Failure => "FAILURE"
        case Level.Warn    => "WARN"
        case Level.Info    => "INFO"
        case Level.Debug   => "DEBUG"
        case Level.Trace   => "TRACE"

  object Level:
    case object Error extends Level
    case object Failure extends Level
    case object Warn extends Level
    case object Info extends Level
    case object Debug extends Level
    case object Trace extends Level

    given Show[Level]  = Show.fromToString
    given Order[Level] = Order.by(_.index)

  def mapK[F[_], G[_]](i: Logger[F])(nat: F ~> G): Logger[G] =
    new Logger[G]:
      override def error(message: => String): G[Unit]                  = nat(i.error(message))
      override def error(ex: Throwable)(message: => String): G[Unit]   = nat(i.error(ex)(message))
      override def failure(message: => String): G[Unit]                = nat(i.failure(message))
      override def failure(ex: Throwable)(message: => String): G[Unit] = nat(i.failure(ex)(message))
      override def warn(message: => String): G[Unit]                   = nat(i.warn(message))
      override def warn(ex: Throwable)(message: => String): G[Unit]    = nat(i.warn(ex)(message))
      override def info(message: => String): G[Unit]                   = nat(i.info(message))
      override def info(ex: Throwable)(message: => String): G[Unit]    = nat(i.info(ex)(message))
      override def debug(message: => String): G[Unit]                  = nat(i.debug(message))
      override def debug(ex: Throwable)(message: => String): G[Unit]   = nat(i.debug(ex)(message))
      override def trace(message: => String): G[Unit]                  = nat(i.trace(message))
      override def trace(ex: Throwable)(message: => String): G[Unit]   = nat(i.trace(ex)(message))
