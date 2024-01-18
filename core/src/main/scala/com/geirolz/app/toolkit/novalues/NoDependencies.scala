package com.geirolz.app.toolkit.novalues

sealed trait NoDependencies
object NoDependencies:
  final val value: NoDependencies = new NoDependencies {}
