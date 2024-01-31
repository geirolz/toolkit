package com.geirolz.app.toolkit.logger

import cats.effect.kernel.Async
import com.geirolz.app.toolkit.SimpleAppInfo
import com.geirolz.app.toolkit.console.AnsiValue
import com.geirolz.app.toolkit.console.AnsiValue.AnsiText
import com.geirolz.app.toolkit.logger.Logger.Level

import java.io.PrintStream
import cats.syntax.all.given

sealed trait ConsoleLogger[F[_]] extends Logger[F]
object ConsoleLogger:

  final val defaultColorMapping: Level => AnsiValue.F =
    case Level.Error   => AnsiValue.F.RED
    case Level.Failure => AnsiValue.F.BRIGHT_RED
    case Level.Warn    => AnsiValue.F.YELLOW
    case Level.Info    => AnsiValue.F.WHITE
    case Level.Debug   => AnsiValue.F.MAGENTA
    case Level.Trace   => AnsiValue.F.CYAN

  final val defaultMsgFormatter: (SimpleAppInfo[?], Level, String) => String =
    (info, level, message) => s"[${info.name.toString.toLowerCase}] $level - $message"

  def apply[F[_]: Async](
    appInfo: SimpleAppInfo[?],
    minLevel: Level,
    errorPrintStream: PrintStream                             = System.err,
    outPrintStream: PrintStream                               = System.out,
    colorMapping: Level => AnsiValue.F                        = defaultColorMapping,
    msgFormatter: (SimpleAppInfo[?], Level, String) => String = defaultMsgFormatter
  ): ConsoleLogger[F] =
    new ConsoleLogger[F]:
      override def error(message: => String): F[Unit]                  = log(Level.Error, message)
      override def error(ex: Throwable)(message: => String): F[Unit]   = log(Level.Error, message, Some(ex))
      override def failure(message: => String): F[Unit]                = log(Level.Failure, message)
      override def failure(ex: Throwable)(message: => String): F[Unit] = log(Level.Failure, message, Some(ex))
      override def warn(message: => String): F[Unit]                   = log(Level.Warn, message)
      override def warn(ex: Throwable)(message: => String): F[Unit]    = log(Level.Warn, message, Some(ex))
      override def info(message: => String): F[Unit]                   = log(Level.Info, message)
      override def info(ex: Throwable)(message: => String): F[Unit]    = log(Level.Info, message, Some(ex))
      override def debug(message: => String): F[Unit]                  = log(Level.Debug, message)
      override def debug(ex: Throwable)(message: => String): F[Unit]   = log(Level.Debug, message, Some(ex))
      override def trace(message: => String): F[Unit]                  = log(Level.Trace, message)
      override def trace(ex: Throwable)(message: => String): F[Unit]   = log(Level.Trace, message, Some(ex))

      private def log(level: Level, message: => String, ex: Option[Throwable] = None): F[Unit] =
        Async[F].whenA(level >= minLevel) {

          val ps: PrintStream =
            level match
              case Level.Error | Level.Failure => errorPrintStream
              case _                           => outPrintStream

          val formattedMsg: AnsiText =
            colorMapping(level)(msgFormatter(appInfo, level, message))

          Async[F].delay(ps.println(formattedMsg)).flatMap { _ =>
            ex match
              case Some(e) =>
                Async[F].delay {
                  e.printStackTrace(ps)
                }
              case None => Async[F].unit
          }
        }
