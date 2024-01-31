package com.geirolz.app.toolkit.fly4s.testing

import cats.Show

case class TestConfig(dbUrl: String, dbUser: Option[String], dbPassword: Option[Array[Char]])
object TestConfig:
  given Show[TestConfig] = Show.fromToString
