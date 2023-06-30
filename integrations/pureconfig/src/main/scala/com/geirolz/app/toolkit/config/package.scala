package com.geirolz.app.toolkit

import pureconfig.ConfigReader

package object config {

  implicit def configReaderForSecret[T: ConfigReader: Secret.Offuser]: ConfigReader[Secret[T]] =
    implicitly[ConfigReader[T]].map(t => Secret[T](t))
}
