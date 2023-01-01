package com.geirolz.app.toolkit.logger

case class AnsiColor(value: String) extends AnyVal {

  def apply(msg: String): String =
    s"$value$msg${AnsiColor.ANSI_RESET}"

  override def toString: String = value
}
object AnsiColor {
  val ANSI_RESET: AnsiColor  = AnsiColor("\u001B[0m")
  val ANSI_BLACK: AnsiColor  = AnsiColor("\u001B[30m")
  val ANSI_RED: AnsiColor    = AnsiColor("\u001B[31m")
  val ANSI_GREEN: AnsiColor  = AnsiColor("\u001B[32m")
  val ANSI_YELLOW: AnsiColor = AnsiColor("\u001B[33m")
  val ANSI_BLUE: AnsiColor   = AnsiColor("\u001B[34m")
  val ANSI_PURPLE: AnsiColor = AnsiColor("\u001B[35m")
  val ANSI_CYAN: AnsiColor   = AnsiColor("\u001B[36m")
  val ANSI_WHITE: AnsiColor  = AnsiColor("\u001B[37m")
}
