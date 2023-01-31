package com.geirolz.app.toolkit.logger

class AnsiValueSuite extends munit.FunSuite {

  test("Test Foreground") {
    val ansiValue: AnsiValue = AnsiValue.Foreground.RED
    Console.println(ansiValue("MY MESSAGE"))
  }

  test("Test Background") {
    val ansiValue: AnsiValue = AnsiValue.Background.RED
    Console.println(ansiValue("MY MESSAGE"))
  }

  test("Test Style") {
    val ansiValue: AnsiValue = AnsiValue.Style.UNDERLINED
    Console.println(ansiValue("MY MESSAGE"))
  }

  test("Test Rich") {

    val ansiValue: AnsiValue =
      AnsiValue.empty
        .withForeground(AnsiValue.Foreground.YELLOW)
        .withBackground(AnsiValue.Background.BLUE)
        .withStyle(AnsiValue.Style.UNDERLINED)

    Console.println(ansiValue("MY MESSAGE"))
  }
}
