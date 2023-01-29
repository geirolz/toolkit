package com.geirolz.app.toolkit.logger

case class AnsiColor(value: String) extends AnyVal {

  def apply(msg: String): String =
    s"$value$msg${AnsiColor.RESET}"

  override def toString: String = value
}
object AnsiColor extends AnsiColorSyntax {

  /** Foreground color for ANSI black
    *
    * @group color-black
    */
  final val BLACK = AnsiColor("u001b[30m")

  /** Foreground color for ANSI red
    *
    * @group color-red
    */
  final val RED = AnsiColor("u001b[31m")

  /** Foreground color for ANSI green
    *
    * @group color-green
    */
  final val GREEN = AnsiColor("u001b[32m")

  /** Foreground color for ANSI yellow
    *
    * @group color-yellow
    */
  final val YELLOW = AnsiColor("u001b[33m")

  /** Foreground color for ANSI blue
    *
    * @group color-blue
    */
  final val BLUE = AnsiColor("u001b[34m")

  /** Foreground color for ANSI magenta
    *
    * @group color-magenta
    */
  final val MAGENTA = AnsiColor("u001b[35m")

  /** Foreground color for ANSI cyan
    *
    * @group color-cyan
    */
  final val CYAN = AnsiColor("u001b[36m")

  /** Foreground color for ANSI white
    *
    * @group color-white
    */
  final val WHITE = AnsiColor("u001b[37m")

  /** Background color for ANSI black
    *
    * @group color-black
    */
  final val BLACK_B = AnsiColor("u001b[40m")

  /** Background color for ANSI red
    *
    * @group color-red
    */
  final val RED_B = AnsiColor("u001b[41m")

  /** Background color for ANSI green
    *
    * @group color-green
    */
  final val GREEN_B = AnsiColor("u001b[42m")

  /** Background color for ANSI yellow
    *
    * @group color-yellow
    */
  final val YELLOW_B = AnsiColor("u001b[43m")

  /** Background color for ANSI blue
    *
    * @group color-blue
    */
  final val BLUE_B = AnsiColor("u001b[44m")

  /** Background color for ANSI magenta
    *
    * @group color-magenta
    */
  final val MAGENTA_B = AnsiColor("u001b[45m")

  /** Background color for ANSI cyan
    *
    * @group color-cyan
    */
  final val CYAN_B = AnsiColor("u001b[46m")

  /** Background color for ANSI white
    *
    * @group color-white
    */
  final val WHITE_B = AnsiColor("u001b[47m")

  /** Reset ANSI styles
    *
    * @group style-control
    */
  final val RESET = AnsiColor("u001b[0m")

  /** ANSI bold
    *
    * @group style-control
    */
  final val BOLD = AnsiColor("u001b[1m")

  /** ANSI underlines
    *
    * @group style-control
    */
  final val UNDERLINED = AnsiColor("u001b[4m")

  /** ANSI blink
    *
    * @group style-control
    */
  final val BLINK = AnsiColor("u001b[5m")

  /** ANSI reversed
    *
    * @group style-control
    */
  final val REVERSED = AnsiColor("u001b[7m")

  /** ANSI invisible
    *
    * @group style-control
    */
  final val INVISIBLE = AnsiColor("u001b[8m")
}
private[logger] sealed trait AnsiColorSyntax {

  implicit class StringOps(str: String) {
    def color(ansiColor: AnsiColor): String = ansiColor(str)
    def consoleStyleBold: String            = AnsiColor.BOLD(str)
    def consoleStyleUnderlined: String      = AnsiColor.UNDERLINED(str)
    def consoleStyleBlink: String           = AnsiColor.BLINK(str)
    def consoleStyleReversed: String        = AnsiColor.REVERSED(str)
    def consoleStyleInvisible: String       = AnsiColor.INVISIBLE(str)
  }
}
