package com.geirolz.app.toolkit.testing

import cats.Show

case class TestConfig(value: String)
object TestConfig {

  def defaultTest: TestConfig = TestConfig("test_config")

  implicit val show: Show[TestConfig] = Show.fromToString
}
