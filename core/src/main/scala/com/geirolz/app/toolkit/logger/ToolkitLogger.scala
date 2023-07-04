package com.geirolz.app.toolkit.logger

import cats.effect.kernel.Async
import cats.kernel.Order
import cats.{~>, Show}
import com.geirolz.app.toolkit.SimpleAppInfo
import com.geirolz.app.toolkit.console.AnsiValue
import com.geirolz.app.toolkit.console.AnsiValue.AnsiText

import java.io.PrintStream

trait ToolkitLogger[F[_]] {
  def error(message: => String): F[Unit]
  def error(ex: Throwable)(message: => String): F[Unit]
  def warn(message: => String): F[Unit]
  def warn(ex: Throwable)(message: => String): F[Unit]
  def info(message: => String): F[Unit]
  def info(ex: Throwable)(message: => String): F[Unit]
  def debug(message: => String): F[Unit]
  def debug(ex: Throwable)(message: => String): F[Unit]
  def trace(message: => String): F[Unit]
  def trace(ex: Throwable)(message: => String): F[Unit]
  def mapK[G[_]](nat: F ~> G): ToolkitLogger[G] = ToolkitLogger.mapK(this)(nat)
}
object ToolkitLogger {

  import cats.implicits.*

  sealed trait Level {

    def index: Int = this match {
      case Level.Error => 4
      case Level.Warn  => 3
      case Level.Info  => 2
      case Level.Debug => 1
      case Level.Trace => 0
    }

    override def toString: String = this match {
      case Level.Error => "ERROR"
      case Level.Warn  => "WARN"
      case Level.Info  => "INFO"
      case Level.Debug => "DEBUG"
      case Level.Trace => "Trace"
    }
  }
  object Level {
    case object Error extends Level
    case object Warn extends Level
    case object Info extends Level
    case object Debug extends Level
    case object Trace extends Level

    implicit val show: Show[Level]   = Show.fromToString
    implicit val order: Order[Level] = Order.by(_.index)
  }

  def console[F[_]: Async](appInfo: SimpleAppInfo[?], minLevel: Level = Level.Warn): ToolkitLogger[F] = new ToolkitLogger[F] {
    override def error(message: => String): F[Unit]                = log(Level.Error, message)
    override def error(ex: Throwable)(message: => String): F[Unit] = log(Level.Error, message, Some(ex))
    override def warn(message: => String): F[Unit]                 = log(Level.Warn, message)
    override def warn(ex: Throwable)(message: => String): F[Unit]  = log(Level.Warn, message, Some(ex))
    override def info(message: => String): F[Unit]                 = log(Level.Info, message)
    override def info(ex: Throwable)(message: => String): F[Unit]  = log(Level.Info, message, Some(ex))
    override def debug(message: => String): F[Unit]                = log(Level.Debug, message)
    override def debug(ex: Throwable)(message: => String): F[Unit] = log(Level.Debug, message, Some(ex))
    override def trace(message: => String): F[Unit]                = log(Level.Trace, message)
    override def trace(ex: Throwable)(message: => String): F[Unit] = log(Level.Trace, message, Some(ex))

    private def log(level: Level, message: => String, ex: Option[Throwable] = None): F[Unit] =
      Async[F].whenA(level >= minLevel) {

        val ps: PrintStream = level match {
          case Level.Error => System.err
          case Level.Trace => System.out
        }

        val color: AnsiValue = level match {
          case Level.Error => AnsiValue.F.RED
          case Level.Warn  => AnsiValue.F.YELLOW
          case Level.Info  => AnsiValue.F.WHITE
          case Level.Debug => AnsiValue.F.MAGENTA
          case Level.Trace => AnsiValue.F.CYAN
        }

        val formattedMsg: AnsiText =
          color(s"[${appInfo.name.toString.toLowerCase}] $level - $message")

        Async[F].delay(ps.println(formattedMsg)).flatMap { _ =>
          ex match {
            case Some(e) => Async[F].delay { e.printStackTrace(ps) }
            case None    => Async[F].unit
          }
        }
      }
  }

  def mapK[F[_], G[_]](i: ToolkitLogger[F])(nat: F ~> G): ToolkitLogger[G] = new ToolkitLogger[G] {
    override def error(message: => String): G[Unit]                = nat(i.error(message))
    override def error(ex: Throwable)(message: => String): G[Unit] = nat(i.error(ex)(message))
    override def warn(message: => String): G[Unit]                 = nat(i.warn(message))
    override def warn(ex: Throwable)(message: => String): G[Unit]  = nat(i.warn(ex)(message))
    override def info(message: => String): G[Unit]                 = nat(i.info(message))
    override def info(ex: Throwable)(message: => String): G[Unit]  = nat(i.info(ex)(message))
    override def debug(message: => String): G[Unit]                = nat(i.debug(message))
    override def debug(ex: Throwable)(message: => String): G[Unit] = nat(i.debug(ex)(message))
    override def trace(message: => String): G[Unit]                = nat(i.trace(message))
    override def trace(ex: Throwable)(message: => String): G[Unit] = nat(i.trace(ex)(message))
  }
}
