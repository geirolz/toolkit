import sbt._

import scala.language.postfixOps

object ProjectDependencies {

  private val catsVersion        = "2.9.0"
  private val catsEffectVersion  = "3.4.8"
  private val refinedVersion     = "0.10.1"
  private val circeVersion       = "0.14.5"
  private val pureConfigVersion  = "0.17.2"
  private val munitVersion       = "0.7.29"
  private val munitEffectVersion = "1.0.7"
  private val slf4Version        = "2.0.6"
  private val log4catsVersion    = "2.5.0"
  private val odinVersion        = "0.13.0"
  private val http4sVersion      = "0.23.18"
  private val fs2Version         = "3.6.1"
  private val scalacheck         = "1.17.0"

  lazy val common: Seq[ModuleID] = Seq(
    // runtime
    "org.typelevel" %% "cats-core" % catsVersion,

    // test
    "org.scalameta" %% "munit" % munitVersion % Test,
    "org.scalameta" %% "munit-scalacheck" % munitVersion % Test,
    "org.typelevel" %% "munit-cats-effect-3" % munitEffectVersion % Test,
    "org.scalacheck" %% "scalacheck" % scalacheck % Test
  )

  object Core {
    lazy val dedicated: Seq[ModuleID] = Seq(
      // runtime
      "org.typelevel" %% "cats-effect" % catsEffectVersion
    )
  }

  object Config {
    lazy val dedicated: Seq[ModuleID] = Nil
  }

  object Examples {
    lazy val dedicated_2_13: Seq[ModuleID] = Seq(
      // http
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,

      // streaming
      "co.fs2" %% "fs2-core" % fs2Version,

      // logging
      "org.typelevel" %% "log4cats-slf4j" % log4catsVersion,
      "org.slf4j" % "slf4j-api" % slf4Version,
      "org.slf4j" % "slf4j-simple" % slf4Version,

      // config
      "com.github.pureconfig" %% "pureconfig-generic" % pureConfigVersion,
      "com.github.pureconfig" %% "pureconfig-http4s" % pureConfigVersion,

      // json
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic-extras" % circeVersion,
      "io.circe" %% "circe-refined" % circeVersion
    )

    lazy val dedicated_3_2: Seq[ModuleID] = Nil
  }

  object Integrations {

    object Log4cats {
      lazy val dedicated: Seq[ModuleID] = List(
        "org.typelevel" %% "log4cats-core" % log4catsVersion,
        "org.typelevel" %% "log4cats-noop" % log4catsVersion % Test
      )
    }

    object Odin {
      lazy val dedicated: Seq[ModuleID] = List(
        "com.github.valskalla" %% "odin-core" % odinVersion
      )
    }

    object ConfigPureConfig {
      lazy val dedicated: Seq[ModuleID] = List(
        "com.github.pureconfig" %% "pureconfig-core" % pureConfigVersion
      )
    }
  }
  object Plugins {
    val compilerPluginsFor2_13: Seq[ModuleID] = Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
      compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
    )

    val compilerPluginsFor3: Seq[ModuleID] = Nil
  }

  object Docs {
    lazy val dedicated_2_13: Seq[ModuleID] = Examples.dedicated_2_13
    lazy val dedicated_3_2: Seq[ModuleID]  = Examples.dedicated_3_2
  }
}
