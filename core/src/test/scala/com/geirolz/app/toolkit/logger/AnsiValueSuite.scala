package com.geirolz.app.toolkit.logger

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.geirolz.app.toolkit.console.AnsiValue

class AnsiValueSuite extends munit.FunSuite:

  import AnsiValue.*

  test("Foreground") {
    Console.println(AnsiValue.F.RED("MY MESSAGE"))
  }

  test("Background") {
    Console.println(AnsiValue.B.RED("MY MESSAGE"))
  }

  test("Style") {
    Console.println(AnsiValue.S.UNDERLINED("MY MESSAGE"))
  }

  test("Rich with builder") {

    val ansiValue: AnsiValue =
      AnsiValue.empty
        .withForeground(AnsiValue.F.YELLOW)
        .withBackground(AnsiValue.B.BLUE)
        .withStyle(AnsiValue.S.UNDERLINED)

    Console.println(ansiValue("MY MESSAGE"))
  }

  test("Rich") {

    val ansiValue: AnsiValue =
      AnsiValue(
        foreground = AnsiValue.F.YELLOW,
        background = AnsiValue.B.BLUE,
        style      = AnsiValue.S.UNDERLINED
      )

    Console.println(ansiValue("MY MESSAGE"))
  }

  test("Test syntax") {
    given IORuntime = cats.effect.unsafe.IORuntime.global
    "MY MESSAGE"
      .ansi(fg = _.RED, bg = _.BLUE, s = _.UNDERLINED)
      .println[IO]
      .unsafeRunSync()
  }
