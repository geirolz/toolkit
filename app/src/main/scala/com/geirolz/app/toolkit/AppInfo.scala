package com.geirolz.app.toolkit

import cats.Show
import cats.implicits.showInterpolator

import java.time.LocalDateTime

trait AppInfo[T] {
  val name: T
  val version: T
  val scalaVersion: T
  val sbtVersion: T
  val builtOn: LocalDateTime
  val buildRefName: T
}
object AppInfo {

  def genBuildRefName[T: Show](appInfo: AppInfo[T]): String =
    show"${appInfo.name}:${appInfo.version}-${appInfo.builtOn}"

  implicit val showLocalDataTime: Show[LocalDateTime] =
    Show.fromToString[LocalDateTime]
}
