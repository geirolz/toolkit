package com.geirolz.app.toolkit.error

class ErrorSyntaxSuite extends munit.FunSuite:

  test("error build exception from string context") {
    error"BOOM!".printStackTrace()
  }

  test("asError build exception from string") {
    "BOOM!".asError.printStackTrace()
  }
