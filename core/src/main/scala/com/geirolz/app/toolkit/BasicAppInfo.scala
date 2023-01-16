package com.geirolz.app.toolkit

import cats.Show
import cats.implicits.showInterpolator

import java.time.LocalDateTime

trait BasicAppInfo[T] {
  val name: T
  val version: T
  val scalaVersion: T
  val sbtVersion: T
  val builtOn: LocalDateTime
  val buildRefName: T
}
object BasicAppInfo {

  def genBuildRefName[T: Show](name: T, version: T, builtOn: LocalDateTime): String =
    show"$name:$version-$builtOn"

  implicit val showLocalDataTime: Show[LocalDateTime] =
    Show.fromToString[LocalDateTime]
}
