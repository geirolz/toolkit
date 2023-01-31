package com.geirolz.app.toolkit.console

import cats.{Monoid, Show}
import cats.effect.std.Console
import cats.implicits.showInterpolator
import com.geirolz.app.toolkit.console.AnsiValue.AnsiText

/** An ADT witch describes the ANSI values typed.
  *   - `AnsiValue.F` includes foreground colors
  *   - `AnsiValue.B` includes background colors
  *   - `AnsiValue.S` includes styles
  *
  * You can combine multiple values with the combinators or with `Monoid` instance
  *
  * {{{
  *   val value: AnsiValue = AnsiValue(
  *      foreground = AnsiValue.F.RED,
  *      foreground = AnsiValue.B.BLACK,
  *      style      = AnsiValue.S.BLINK
  *   )
  *
  *   //or
  *   AnsiValue.F.RED
  *      .withBackground(AnsiValue.B.BLACK)
  *      .withStyle(AnsiValue.S.BLINK)
  * }}}
  */
sealed trait AnsiValue {

  val value: String

  def apply[T](msg: T)(implicit s: Show[T] = Show.fromToString[T]): AnsiText =
    show"$value$msg${AnsiValue.S.RESET}"

  lazy val foreground: AnsiValue.F = this match {
    case AnsiValue.Rich(fg, _, _) => fg
    case bg: AnsiValue.F          => bg
    case _                        => AnsiValue.F.NONE
  }

  lazy val background: AnsiValue.B = this match {
    case AnsiValue.Rich(_, bg, _) => bg
    case bg: AnsiValue.B          => bg
    case _                        => AnsiValue.B.NONE
  }

  lazy val style: AnsiValue.S = this match {
    case AnsiValue.Rich(_, _, s) => s
    case s: AnsiValue.S          => s
    case _                       => AnsiValue.S.NONE
  }

  def withForeground(fg: AnsiValue.F): AnsiValue =
    withValue(fg)

  def withoutForeground: AnsiValue =
    withForeground(AnsiValue.F.NONE)

  def withBackground(bg: AnsiValue.B): AnsiValue =
    withValue(bg)

  def withoutBackground: AnsiValue =
    withBackground(AnsiValue.B.NONE)

  def withStyle(s: AnsiValue.S): AnsiValue =
    withValue(s)

  def withoutStyle: AnsiValue =
    withStyle(AnsiValue.S.NONE)

  def withValue(value: AnsiValue): AnsiValue =
    (this, value) match {
      case (_: AnsiValue.F, b: AnsiValue.F)       => b
      case (_: AnsiValue.B, b: AnsiValue.B)       => b
      case (_: AnsiValue.S, b: AnsiValue.S)       => b
      case (_: AnsiValue.Rich, b: AnsiValue.Rich) => b
      case (a: AnsiValue.Rich, b: AnsiValue)      => a.withEvalValue(b)
      case (a, b: AnsiValue.Rich)                 => b.withEvalValue(a)
      case (a, b) => AnsiValue.Rich().withEvalValue(a).withEvalValue(b)
    }

  override def toString: String = value
}
object AnsiValue extends AnsiValueInstances with AnsiValueSyntax {

  type AnsiText = String

  final lazy val empty: AnsiValue       = AnsiValue.Rich()
  private final val emptyString: String = ""

  def apply(
    foreground: AnsiValue.F = AnsiValue.F.NONE,
    background: AnsiValue.B = AnsiValue.B.NONE,
    style: AnsiValue.S      = AnsiValue.S.NONE
  ): AnsiValue =
    AnsiValue.Rich(foreground, background, style)

  private[console] case class Rich private (
    fg: AnsiValue.F,
    bg: AnsiValue.B,
    s: AnsiValue.S
  ) extends AnsiValue {

    private[AnsiValue] def withEvalValue(value: AnsiValue): AnsiValue.Rich = {
      value match {
        case value: AnsiValue.Rich => value
        case value: AnsiValue.F    => copy(fg = value)
        case value: AnsiValue.B    => copy(bg = value)
        case value: AnsiValue.S    => copy(s = value)
      }
    }

    override val value: AnsiText = List(s, bg, fg).mkString
  }
  object Rich {
    private[AnsiValue] def apply(
      foreground: AnsiValue.F = AnsiValue.F.NONE,
      background: AnsiValue.B = AnsiValue.B.NONE,
      style: AnsiValue.S      = AnsiValue.S.NONE
    ): AnsiValue.Rich = new Rich(foreground, background, style)
  }

  case class F private (value: String) extends AnsiValue
  object F {

    private[F] def apply(value: String): AnsiValue.F =
      new F(value)

    final val NONE: AnsiValue.F = F(AnsiValue.emptyString)

    /** Foreground color for ANSI black
      *
      * @group color-black
      */
    final val BLACK: AnsiValue.F = F(scala.Console.BLACK)

    /** Foreground color for ANSI red
      *
      * @group color-red
      */
    final val RED: AnsiValue.F = F(scala.Console.RED)

    /** Foreground color for ANSI green
      *
      * @group color-green
      */
    final val GREEN: AnsiValue.F = F(scala.Console.GREEN)

    /** Foreground color for ANSI yellow
      *
      * @group color-yellow
      */
    final val YELLOW: AnsiValue.F = F(scala.Console.YELLOW)

    /** Foreground color for ANSI blue
      *
      * @group color-blue
      */
    final val BLUE: AnsiValue.F = F(scala.Console.BLUE)

    /** Foreground color for ANSI magenta
      *
      * @group color-magenta
      */
    final val MAGENTA: AnsiValue.F = F(scala.Console.MAGENTA)

    /** Foreground color for ANSI cyan
      *
      * @group color-cyan
      */
    final val CYAN: AnsiValue.F = F(scala.Console.CYAN)

    /** Foreground color for ANSI white
      *
      * @group color-white
      */
    final val WHITE: AnsiValue.F = F(scala.Console.WHITE)
  }

  case class B private (value: String) extends AnsiValue
  object B {

    private[B] def apply(value: String): AnsiValue.B =
      new B(
        value
      )

    final val NONE: AnsiValue.B = B(AnsiValue.emptyString)

    /** Background color for ANSI black
      *
      * @group color-black
      */
    final val BLACK: AnsiValue.B = B(scala.Console.BLACK_B)

    /** Background color for ANSI red
      *
      * @group color-red
      */
    final val RED = B(scala.Console.RED_B)

    /** Background color for ANSI green
      *
      * @group color-green
      */
    final val GREEN: AnsiValue.B = B(scala.Console.GREEN_B)

    /** Background color for ANSI yellow
      *
      * @group color-yellow
      */
    final val YELLOW: AnsiValue.B = B(scala.Console.YELLOW_B)

    /** Background color for ANSI blue
      *
      * @group color-blue
      */
    final val BLUE: AnsiValue.B = B(scala.Console.BLUE_B)

    /** Background color for ANSI magenta
      *
      * @group color-magenta
      */
    final val MAGENTA: AnsiValue.B = B(scala.Console.MAGENTA_B)

    /** Background color for ANSI cyan
      *
      * @group color-cyan
      */
    final val CYAN: AnsiValue.B = B(scala.Console.CYAN_B)

    /** Background color for ANSI white
      *
      * @group color-white
      */
    final val WHITE: AnsiValue.B = B(scala.Console.WHITE)
  }

  case class S private (value: String) extends AnsiValue
  object S {

    private[S] def apply(value: String): AnsiValue.S = new S(value)

    final val NONE: AnsiValue.S = S(AnsiValue.emptyString)

    /** Reset ANSI styles
      *
      * @group style-control
      */
    final val RESET: AnsiValue.S = S(scala.Console.RESET)

    /** ANSI bold
      *
      * @group style-control
      */
    final val BOLD: AnsiValue.S = S(scala.Console.BOLD)

    /** ANSI underlines
      *
      * @group style-control
      */
    final val UNDERLINED: AnsiValue.S = S(scala.Console.UNDERLINED)

    /** ANSI blink
      *
      * @group style-control
      */
    final val BLINK: AnsiValue.S = S(scala.Console.BLINK)

    /** ANSI reversed
      *
      * @group style-control
      */
    final val REVERSED: AnsiValue.S = S(scala.Console.REVERSED)

    /** ANSI invisible
      *
      * @group style-control
      */
    final val INVISIBLE: AnsiValue.S = S(scala.Console.INVISIBLE)
  }
}
private[console] sealed trait AnsiValueInstances {

  implicit val monoid: Monoid[AnsiValue] = new Monoid[AnsiValue] {
    override def empty: AnsiValue                               = AnsiValue.empty
    override def combine(x: AnsiValue, y: AnsiValue): AnsiValue = x.withValue(y)
  }

  implicit val show: Show[AnsiValue] = Show.fromToString
}
private[console] sealed trait AnsiValueSyntax {

  implicit class AnsiTextOps(t: AnsiText) {

    def print[F[_]: Console]: F[Unit] = Console[F].print(t)

    def println[F[_]: Console]: F[Unit] = Console[F].println(t)

    def error[F[_]: Console]: F[Unit] = Console[F].error(t)

    def errorln[F[_]: Console]: F[Unit] = Console[F].errorln(t)
  }

  implicit class AnyShowableOps[T](t: T)(implicit s: Show[T] = Show.fromToString[T]) {

    def ansiValue(value: AnsiValue): AnsiText = value(t)

    def ansi(
      fg: AnsiValue.F.type => AnsiValue.F = _.NONE,
      bg: AnsiValue.B.type => AnsiValue.B = _.NONE,
      s: AnsiValue.S.type => AnsiValue.S  = _.NONE
    ): AnsiText =
      ansiValue(
        AnsiValue(
          foreground = fg(AnsiValue.F),
          background = bg(AnsiValue.B),
          style      = s(AnsiValue.S)
        )
      )

    def ansiFg(fg: AnsiValue.F.type => AnsiValue.F): AnsiText       = ansi(fg = fg)
    def ansiBg(bg: AnsiValue.B.type => AnsiValue.B): AnsiText       = ansi(bg = bg)
    def ansiStyle(style: AnsiValue.S.type => AnsiValue.S): AnsiText = ansi(s = style)
    def ansiBold: AnsiText                                          = ansiStyle(_.BOLD)
    def ansiUnderlined: AnsiText                                    = ansiStyle(_.UNDERLINED)
    def ansiBlink: AnsiText                                         = ansiStyle(_.BLINK)
    def ansiReversed: AnsiText                                      = ansiStyle(_.REVERSED)
    def ansiInvisible: AnsiText                                     = ansiStyle(_.INVISIBLE)
  }
}
