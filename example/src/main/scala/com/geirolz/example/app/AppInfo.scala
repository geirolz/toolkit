package com.geirolz.example.app

import cats.Show
import com.geirolz.app.toolkit.BasicAppInfo

import java.time.{Instant, LocalDateTime, ZoneOffset}

class AppInfo private (
  val name: String,
  val version: String,
  val scalaVersion: String,
  val sbtVersion: String,
  val builtOn: LocalDateTime,
  val buildRefName: String
) extends BasicAppInfo[String]
object AppInfo {

  val fromBuildInfo: AppInfo = {
    val builtOn: LocalDateTime = LocalDateTime.ofInstant(
      Instant.ofEpochMilli(BuildInfo.builtAtMillis),
      ZoneOffset.UTC
    )
    new AppInfo(
      name         = BuildInfo.name,
      version      = BuildInfo.version,
      scalaVersion = BuildInfo.scalaVersion,
      sbtVersion   = BuildInfo.sbtVersion,
      builtOn      = builtOn,
      buildRefName = BasicAppInfo.genBuildRefName(
        name    = BuildInfo.name,
        version = BuildInfo.version,
        builtOn = builtOn
      )
    )
  }

  implicit val show: Show[AppInfo] = Show.fromToString
}
