package com.geirolz.example.app

import cats.Show
import com.geirolz.app.toolkit.SimpleAppInfo

import java.time.{Instant, LocalDateTime, ZoneOffset}

class AppInfo private (
  val name: String,
  val version: String,
  val scalaVersion: String,
  val sbtVersion: String,
  val buildRefName: String,
  val builtOn: LocalDateTime
) extends SimpleAppInfo[String]
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
      buildRefName = SimpleAppInfo.genRefNameString(
        name    = BuildInfo.name,
        version = BuildInfo.version,
        builtOn = builtOn
      ),
      builtOn = builtOn
    )
  }

  implicit val show: Show[AppInfo] = Show.fromToString
}
