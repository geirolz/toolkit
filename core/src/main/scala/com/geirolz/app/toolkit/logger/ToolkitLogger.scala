package com.geirolz.app.toolkit.logger

import cats.effect.kernel.Async
import cats.~>
import com.geirolz.app.toolkit.SimpleAppInfo
import com.geirolz.app.toolkit.console.AnsiValue

trait ToolkitLogger[F[_]] {
  def info(message: => String): F[Unit]
  def error(message: => String): F[Unit]
  def error(ex: Throwable)(message: => String): F[Unit]
  def mapK[G[_]](nat: F ~> G): ToolkitLogger[G] = ToolkitLogger.mapK(this)(nat)
}
object ToolkitLogger {

  private sealed trait Level {
    def asString: String = this match {
      case Level.Info  => "INFO"
      case Level.Error => "ERROR"
    }
  }
  private object Level {
    case object Info extends Level
    case object Error extends Level
  }

  def console[F[_]: Async](appInfo: SimpleAppInfo[?]): ToolkitLogger[F] = new ToolkitLogger[F] {

    override def info(message: => String): F[Unit] =
      Async[F].delay(Console.println(normalize(Level.Info, message)))

    override def error(message: => String): F[Unit] =
      Async[F].delay(Console.err.println(normalize(Level.Error, message)))

    override def error(ex: Throwable)(message: => String): F[Unit] =
      Async[F].delay {
        Console.err.println(normalize(Level.Error, message))
        ex.printStackTrace(Console.err)
      }

    private def normalize(lvl: Level, msg: String): String = {

      val color: AnsiValue = lvl match {
        case Level.Info  => AnsiValue.F.YELLOW
        case Level.Error => AnsiValue.F.RED
      }

      color(s"[${appInfo.name.toString.toLowerCase}] ${lvl.asString} - $msg")
    }

  }

  def mapK[F[_], G[_]](i: ToolkitLogger[F])(nat: F ~> G): ToolkitLogger[G] = new ToolkitLogger[G] {
    override def info(message: => String): G[Unit]                 = nat(i.info(message))
    override def error(message: => String): G[Unit]                = nat(i.error(message))
    override def error(ex: Throwable)(message: => String): G[Unit] = nat(i.error(ex)(message))
  }
}
