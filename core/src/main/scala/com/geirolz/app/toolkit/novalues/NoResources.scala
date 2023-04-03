package com.geirolz.app.toolkit.novalues

sealed trait NoResources
object NoResources {
  final val value: NoResources = new NoResources {}
}
