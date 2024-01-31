package com.geirolz.app.toolkit.novalues

sealed trait NoDependencies
object NoDependencies:
  private[toolkit] final val value: NoDependencies = new NoDependencies {}
