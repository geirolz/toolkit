package com.geirolz.app.toolkit.logger

import cats.effect.kernel.Async
import cats.~>
import com.geirolz.app.toolkit.SimpleAppInfo
import com.geirolz.app.toolkit.console.AnsiValue

import java.io.PrintStream

trait ToolkitLogger[F[_]] {
  val loggerLevel: ToolkitLogger.Level
  def info(message: => String): F[Unit]
  def info(ex: Throwable)(message: => String): F[Unit]
  def warn(message: => String): F[Unit]
  def warn(ex: Throwable)(message: => String): F[Unit]
  def error(message: => String): F[Unit]
  def error(ex: Throwable)(message: => String): F[Unit]
  def debug(message: => String): F[Unit]
  def debug(ex: Throwable)(message: => String): F[Unit]
  def mapK[G[_]](nat: F ~> G): ToolkitLogger[G] = ToolkitLogger.mapK(this)(nat)
}
object ToolkitLogger {

  sealed trait Level {

    def index: Int = this match {
      case Level.Info  => 0
      case Level.Warn  => 1
      case Level.Error => 2
      case Level.Debug => 3
    }

    def asString: String = this match {
      case Level.Info  => "INFO"
      case Level.Warn  => "WARN"
      case Level.Error => "ERROR"
      case Level.Debug => "DEBUG"
    }
  }
  object Level {
    case object Info extends Level
    case object Warn extends Level
    case object Error extends Level
    case object Debug extends Level
  }

  def console[F[_]: Async](appInfo: SimpleAppInfo[?], loggingLevel: Level = Level.Error): ToolkitLogger[F] = new ToolkitLogger[F] {

    override val loggerLevel: Level                                = loggingLevel
    override def info(message: => String): F[Unit]                 = log(Level.Info, Console.out)(message)
    override def info(ex: Throwable)(message: => String): F[Unit]  = logEx(Level.Info, Console.out)(ex, message)
    override def warn(message: => String): F[Unit]                 = log(Level.Warn, Console.out)(message)
    override def warn(ex: Throwable)(message: => String): F[Unit]  = logEx(Level.Warn, Console.out)(ex, message)
    override def error(message: => String): F[Unit]                = log(Level.Error, Console.err)(message)
    override def error(ex: Throwable)(message: => String): F[Unit] = logEx(Level.Error, Console.err)(ex, message)
    override def debug(message: => String): F[Unit]                = log(Level.Debug, Console.out)(message)
    override def debug(ex: Throwable)(message: => String): F[Unit] = logEx(Level.Debug, Console.out)(ex, message)

    private def log(level: Level, ps: PrintStream)(message: => String): F[Unit] =
      doIfNeeded(level) {
        Async[F].delay(ps.println(normalize(level, message)))
      }

    private def logEx(level: Level, ps: PrintStream)(ex: Throwable, message: => String): F[Unit] =
      doIfNeeded(level) {
        Async[F].delay {
          ps.println(normalize(level, message))
          ex.printStackTrace(Console.err)
        }
      }

    private def doIfNeeded(level: Level)(f: F[Unit]): F[Unit] =
      if (level.index <= loggerLevel.index) f else Async[F].unit

    private def normalize(lvl: Level, msg: String): String = {

      val color: AnsiValue = lvl match {
        case Level.Info  => AnsiValue.F.WHITE
        case Level.Warn  => AnsiValue.F.YELLOW
        case Level.Error => AnsiValue.F.RED
        case Level.Debug => AnsiValue.F.MAGENTA
      }

      color(s"[${appInfo.name.toString.toLowerCase}] ${lvl.asString} - $msg")
    }
  }

  def mapK[F[_], G[_]](i: ToolkitLogger[F])(nat: F ~> G): ToolkitLogger[G] = new ToolkitLogger[G] {
    override val loggerLevel: Level                                = i.loggerLevel
    override def info(message: => String): G[Unit]                 = nat(i.info(message))
    override def info(ex: Throwable)(message: => String): G[Unit]  = nat(i.info(ex)(message))
    override def warn(message: => String): G[Unit]                 = nat(i.warn(message))
    override def warn(ex: Throwable)(message: => String): G[Unit]  = nat(i.warn(ex)(message))
    override def error(message: => String): G[Unit]                = nat(i.error(message))
    override def error(ex: Throwable)(message: => String): G[Unit] = nat(i.error(ex)(message))
    override def debug(message: => String): G[Unit]                = nat(i.debug(message))
    override def debug(ex: Throwable)(message: => String): G[Unit] = nat(i.debug(ex)(message))

  }
}
