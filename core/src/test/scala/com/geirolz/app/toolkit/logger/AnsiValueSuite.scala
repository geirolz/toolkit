package com.geirolz.app.toolkit.logger

import com.geirolz.app.toolkit.console.AnsiValue

class AnsiValueSuite extends munit.FunSuite {

  test("Test Foreground") {
    Console.println(AnsiValue.F.RED("MY MESSAGE"))
  }

  test("Test Background") {
    Console.println(AnsiValue.B.RED("MY MESSAGE"))
  }

  test("Test Style") {
    Console.println(AnsiValue.S.UNDERLINED("MY MESSAGE"))
  }

  test("Test Rich with builder") {

    val ansiValue: AnsiValue =
      AnsiValue.empty
        .withForeground(AnsiValue.F.YELLOW)
        .withBackground(AnsiValue.B.BLUE)
        .withStyle(AnsiValue.S.UNDERLINED)

    Console.println(ansiValue("MY MESSAGE"))
  }

  test("Test Rich") {

    val ansiValue: AnsiValue =
      AnsiValue(
        foreground = AnsiValue.F.YELLOW,
        background = AnsiValue.B.BLUE,
        style      = AnsiValue.S.UNDERLINED
      )

    Console.println(ansiValue("MY MESSAGE"))
  }
}
