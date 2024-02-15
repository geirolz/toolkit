package com.geirolz.app.toolkit.novalues

import cats.Show

sealed trait NoConfig
object NoConfig:
  final val value: NoConfig = new NoConfig {}
  given Show[NoConfig]      = Show.show(_ => "[NO CONFIG]")
