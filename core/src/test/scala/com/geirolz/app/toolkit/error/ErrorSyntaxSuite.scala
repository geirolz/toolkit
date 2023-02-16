package com.geirolz.app.toolkit.error

class ErrorSyntaxSuite extends munit.FunSuite {

  test("MultiError") {
    ex"BOOM!".printStackTrace()
  }
}
