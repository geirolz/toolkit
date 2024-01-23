package com.geirolz.app.toolkit.error

import cats.data.NonEmptyList

class MultiExceptionSuite extends munit.FunSuite:

  test("Test printStackTrace") {
    val ex = MultiException.fromNel(
      NonEmptyList.of(
        new RuntimeException("EX1"),
        new RuntimeException("EX2"),
        new RuntimeException("EX3")
      )
    )

    ex.printStackTrace()
  }

  test("Test getStackTraces - Has size == 3") {
    val ex = MultiException.fromNel(
      NonEmptyList.of(
        new RuntimeException("EX1"),
        new RuntimeException("EX2"),
        new RuntimeException("EX3")
      )
    )

    assertEquals(
      obtained = ex.getStackTraces.size,
      expected = 3
    )
  }

  test("Test getStackTrace - is Empty") {
    val ex = MultiException.fromNel(
      NonEmptyList.of(
        new RuntimeException("EX1"),
        new RuntimeException("EX2"),
        new RuntimeException("EX3")
      )
    )

    assert(ex.getStackTrace.isEmpty)
  }

  test("Test setStackTrace - should throw UnsupportedOperationException".fail) {
    val ex = MultiException.fromNel(
      NonEmptyList.of(
        new RuntimeException("EX1"),
        new RuntimeException("EX2"),
        new RuntimeException("EX3")
      )
    )

    ex.setStackTrace(Array.empty)
  }

  test("printStackTrace") {
    val ex = MultiException.fromNel(
      NonEmptyList.of(
        new RuntimeException("EX1"),
        new RuntimeException("EX2"),
        new RuntimeException("EX3")
      )
    )

    ex.printStackTrace()
  }
