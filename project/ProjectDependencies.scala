import sbt._

object ProjectDependencies {

  private val catsVersion        = "2.9.0"
  private val catsEffectVersion  = "3.4.4"
  private val refinedVersion     = "0.10.1"
  private val circeVersion       = "0.14.3"
  private val pureConfigVersion  = "0.17.2"
  private val munitVersion       = "0.7.29"
  private val munitEffectVersion = "1.0.7"
  private val slf4Version        = "2.0.5"
  private val log4catsVersion    = "2.5.0"
  private val http4sVersion      = "0.23.17"

  lazy val common: Seq[ModuleID] = Seq(
    // runtime
    "org.typelevel" %% "cats-core" % catsVersion,

    // test
    "org.scalameta" %% "munit" % munitVersion % Test,
    "org.typelevel" %% "munit-cats-effect-3" % munitEffectVersion % Test
  )

  object Core {
    lazy val dedicated: Seq[ModuleID] = Seq(
      // runtime
      "org.typelevel" %% "cats-effect" % catsEffectVersion,

      // json
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic-extras" % circeVersion,
      "io.circe" %% "circe-refined" % circeVersion,

      // logging
      "org.typelevel" %% "log4cats-slf4j" % log4catsVersion,
      "org.slf4j" % "slf4j-api" % slf4Version,
      "org.slf4j" % "slf4j-simple" % slf4Version
    )
  }

  object Config {
    lazy val dedicated: Seq[ModuleID] = Seq(
      "com.github.pureconfig" %% "pureconfig-core" % pureConfigVersion
    )
  }

  object Examples {
    lazy val dedicated: Seq[ModuleID] = Seq(
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "com.github.pureconfig" %% "pureconfig-generic" % pureConfigVersion,
      "com.github.pureconfig" %% "pureconfig-http4s" % pureConfigVersion
    )
  }

  object Plugins {
    val compilerPluginsFor2_13: Seq[ModuleID] = Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
      compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
    )

    val compilerPluginsFor3: Seq[ModuleID] = Nil
  }

  object Docs {
    lazy val dedicated: Seq[ModuleID] = Nil
  }
}
