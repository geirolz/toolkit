package com.geirolz.app.toolkit.testing

import com.geirolz.app.toolkit.SimpleAppInfo

import java.time.LocalDateTime

case class TestAppInfo(
  name: String,
  description: String,
  version: String,
  scalaVersion: String,
  sbtVersion: String,
  javaVersion: Option[String],
  builtOn: LocalDateTime
) extends SimpleAppInfo[String] {
  override val buildRefName: String = SimpleAppInfo.genRefNameString(
    name    = name,
    version = version,
    builtOn = builtOn
  )
}

object TestAppInfo {
  val value: TestAppInfo = TestAppInfo(
    name         = "AppTest",
    description  = "An app test",
    version      = "1.0.0",
    scalaVersion = "2.13.0",
    sbtVersion   = "1.2.8",
    javaVersion  = None,
    builtOn      = LocalDateTime.now()
  )
}
