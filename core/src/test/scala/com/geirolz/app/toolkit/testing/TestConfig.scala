package com.geirolz.app.toolkit.testing

import cats.Show

case class TestConfig(value: String)
object TestConfig:
  def defaultTest: TestConfig = TestConfig("test_config")
  given Show[TestConfig]      = Show.fromToString
