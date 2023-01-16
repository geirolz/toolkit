package com.geirolz.app.toolkit.novalues

import cats.Show

sealed trait NoConfig
object NoConfig {
  val value: NoConfig               = new NoConfig {}
  implicit val show: Show[NoConfig] = Show.show(_ => "[NO CONFIG]")
}
