package com.geirolz.app.toolkit.logger

import cats.{Monoid, Show}
import com.geirolz.app.toolkit.logger.AnsiValue.AnsiRichText

sealed trait AnsiValue {

  val value: String

  def apply(msg: String): AnsiRichText =
    s"$value$msg${AnsiValue.Style.RESET}"

  def withForeground(color: AnsiValue.Foreground): AnsiValue =
    withValue(color)

  def withBackground(color: AnsiValue.Background): AnsiValue =
    withValue(color)

  def withStyle(style: AnsiValue.Style): AnsiValue =
    withValue(style)

  private[logger] def withValue(value: AnsiValue): AnsiValue =
    (this, value) match {
      case (_: AnsiValue.Foreground, b: AnsiValue.Foreground) => b
      case (_: AnsiValue.Background, b: AnsiValue.Background) => b
      case (_: AnsiValue.Style, b: AnsiValue.Style)           => b
      case (_: AnsiValue.Rich, b: AnsiValue.Rich)             => b
      case (a: AnsiValue.Rich, b: AnsiValue)                  => a.withEvalValue(b)
      case (a, b: AnsiValue.Rich)                             => b.withEvalValue(a)
      case (a, b) => AnsiValue.Rich().withEvalValue(a).withEvalValue(b)
    }

  override def toString: String = value
}
object AnsiValue extends AnsiValueInstances with AnsiValueSyntax {

  type AnsiRichText = String

  val empty: AnsiValue = AnsiValue.Rich()

  case class Rich private (
    foreground: Option[Foreground],
    background: Option[Background],
    style: Option[Style]
  ) extends AnsiValue {

    private[AnsiValue] def withEvalValue(value: AnsiValue): AnsiValue.Rich = {
      value match {
        case value: AnsiValue.Rich => value
        case value: Foreground     => copy(foreground = Some(value))
        case value: Background     => copy(background = Some(value))
        case value: Style          => copy(style = Some(value))
      }
    }

    override val value: AnsiRichText = List(
      style,
      background,
      foreground
    ).flatten.mkString("")
  }
  object Rich {
    private[AnsiValue] def apply(
      foregroundColor: Option[Foreground] = None,
      backgroundColor: Option[Background] = None,
      style: Option[Style]                = None
    ): AnsiValue.Rich = new Rich(foregroundColor, backgroundColor, style)
  }

  case class Foreground private (value: String) extends AnsiValue
  object Foreground {

    private[Foreground] def apply(value: String): AnsiValue.Foreground =
      new Foreground(value)

    /** Foreground color for ANSI black
      *
      * @group color-black
      */
    final val BLACK: AnsiValue.Foreground = Foreground(scala.Console.BLACK)

    /** Foreground color for ANSI red
      *
      * @group color-red
      */
    final val RED: AnsiValue.Foreground = Foreground(scala.Console.RED)

    /** Foreground color for ANSI green
      *
      * @group color-green
      */
    final val GREEN: AnsiValue.Foreground = Foreground(scala.Console.GREEN)

    /** Foreground color for ANSI yellow
      *
      * @group color-yellow
      */
    final val YELLOW: AnsiValue.Foreground = Foreground(scala.Console.YELLOW)

    /** Foreground color for ANSI blue
      *
      * @group color-blue
      */
    final val BLUE: AnsiValue.Foreground = Foreground(scala.Console.BLUE)

    /** Foreground color for ANSI magenta
      *
      * @group color-magenta
      */
    final val MAGENTA: AnsiValue.Foreground = Foreground(scala.Console.MAGENTA)

    /** Foreground color for ANSI cyan
      *
      * @group color-cyan
      */
    final val CYAN: AnsiValue.Foreground = Foreground(scala.Console.CYAN)

    /** Foreground color for ANSI white
      *
      * @group color-white
      */
    final val WHITE: AnsiValue.Foreground = Foreground(scala.Console.WHITE)
  }

  case class Background private (value: String) extends AnsiValue
  object Background {

    private[Background] def apply(value: String): AnsiValue.Background =
      new Background(
        value
      )

    /** Background color for ANSI black
      *
      * @group color-black
      */
    final val BLACK: AnsiValue.Background = Background(scala.Console.BLACK_B)

    /** Background color for ANSI red
      *
      * @group color-red
      */
    final val RED = Background(scala.Console.RED_B)

    /** Background color for ANSI green
      *
      * @group color-green
      */
    final val GREEN: AnsiValue.Background = Background(scala.Console.GREEN_B)

    /** Background color for ANSI yellow
      *
      * @group color-yellow
      */
    final val YELLOW: AnsiValue.Background = Background(scala.Console.YELLOW_B)

    /** Background color for ANSI blue
      *
      * @group color-blue
      */
    final val BLUE: AnsiValue.Background = Background(scala.Console.BLUE_B)

    /** Background color for ANSI magenta
      *
      * @group color-magenta
      */
    final val MAGENTA: AnsiValue.Background = Background(scala.Console.MAGENTA_B)

    /** Background color for ANSI cyan
      *
      * @group color-cyan
      */
    final val CYAN: AnsiValue.Background = Background(scala.Console.CYAN_B)

    /** Background color for ANSI white
      *
      * @group color-white
      */
    final val WHITE: AnsiValue.Background = Background(scala.Console.WHITE)
  }

  case class Style private (value: String) extends AnsiValue
  object Style {

    private[Style] def apply(value: String): AnsiValue.Style = new Style(value)

    /** Reset ANSI styles
      *
      * @group style-control
      */
    final val RESET: AnsiValue.Style = Style(scala.Console.RESET)

    /** ANSI bold
      *
      * @group style-control
      */
    final val BOLD: AnsiValue.Style = Style(scala.Console.BOLD)

    /** ANSI underlines
      *
      * @group style-control
      */
    final val UNDERLINED: AnsiValue.Style = Style(scala.Console.UNDERLINED)

    /** ANSI blink
      *
      * @group style-control
      */
    final val BLINK: AnsiValue.Style = Style(scala.Console.BLINK)

    /** ANSI reversed
      *
      * @group style-control
      */
    final val REVERSED: AnsiValue.Style = Style(scala.Console.REVERSED)

    /** ANSI invisible
      *
      * @group style-control
      */
    final val INVISIBLE: AnsiValue.Style = Style(scala.Console.INVISIBLE)
  }
}
private[logger] sealed trait AnsiValueInstances {

  implicit val monoid: Monoid[AnsiValue] = new Monoid[AnsiValue] {
    override def empty: AnsiValue = AnsiValue.empty
    override def combine(x: AnsiValue, y: AnsiValue): AnsiValue =
      (x, y) match {
        case (_: AnsiValue.Foreground, b: AnsiValue.Foreground) => b
        case (_: AnsiValue.Background, b: AnsiValue.Background) => b
        case (_: AnsiValue.Style, b: AnsiValue.Style)           => b
        case (a, b) => AnsiValue.empty.withValue(a).withValue(b)
      }
  }

  implicit val show: Show[AnsiValue] = Show.fromToString
}
private[logger] sealed trait AnsiValueSyntax {

  implicit class StringOps(str: String) {
    def consoleColor(ansiColor: AnsiValue.Foreground): String      = ansiColor(str)
    def consoleBackground(ansiColor: AnsiValue.Background): String = ansiColor(str)
    def consoleStyleBold: String                                   = AnsiValue.Style.BOLD(str)
    def consoleStyleUnderlined: String                             = AnsiValue.Style.UNDERLINED(str)
    def consoleStyleBlink: String                                  = AnsiValue.Style.BLINK(str)
    def consoleStyleReversed: String                               = AnsiValue.Style.REVERSED(str)
    def consoleStyleInvisible: String                              = AnsiValue.Style.INVISIBLE(str)
  }
}
