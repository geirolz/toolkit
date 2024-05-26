import sbt.*

import scala.language.postfixOps

object ProjectDependencies {

  private val catsVersion              = "2.10.0"
  private val catsEffectVersion        = "3.5.4"
  private val circeVersion             = "0.14.7"
  private val circeGenericExtraVersion = "0.14.3"
  private val pureConfigVersion        = "0.17.6"
  private val fly4sVersion             = "1.0.4"
  private val munitVersion             = "1.0.0"
  private val munitEffectVersion       = "2.0.0"
  private val slf4Version              = "2.0.13"
  private val log4catsVersion          = "2.7.0"
  private val odinVersion              = "0.13.0"
  private val http4sVersion            = "0.23.27"
  private val fs2Version               = "3.10.2"
  private val scalacheck               = "1.17.1"

  lazy val common: Seq[ModuleID] = Seq(
    // runtime
    "org.typelevel" %% "cats-core" % catsVersion,

    // test
    "org.scalameta"  %% "munit"             % munitVersion       % Test,
    "org.scalameta"  %% "munit-scalacheck"  % munitVersion       % Test,
    "org.typelevel"  %% "munit-cats-effect" % munitEffectVersion % Test,
    "org.scalacheck" %% "scalacheck"        % scalacheck         % Test
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

  object Testing {
    lazy val dedicated: Seq[ModuleID] = Seq(
      // runtime
      "org.typelevel" %% "cats-effect" % catsEffectVersion
    )
  }

  object Examples {

    lazy val dedicated: Seq[ModuleID] = Seq(
      // http
      "org.http4s" %% "http4s-dsl"          % http4sVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,

      // streaming
      "co.fs2" %% "fs2-core" % fs2Version,

      // logging
      "org.typelevel" %% "log4cats-slf4j" % log4catsVersion,
      "org.slf4j"      % "slf4j-api"      % slf4Version,
      "org.slf4j"      % "slf4j-simple"   % slf4Version,

      // config
      "com.github.pureconfig" %% "pureconfig-http4s" % pureConfigVersion,
      "com.github.pureconfig" %% "pureconfig-ip4s"   % pureConfigVersion,

      // json
      "io.circe" %% "circe-core"    % circeVersion,
      "io.circe" %% "circe-refined" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion
    )
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

    object Pureconfig {
      lazy val dedicated: Seq[ModuleID] = List(
        "com.github.pureconfig" %% "pureconfig-core" % pureConfigVersion
      )
    }

    object Fly4s {
      lazy val dedicated: Seq[ModuleID] = List(
        "com.github.geirolz" %% "fly4s" % fly4sVersion
      )
    }
  }

  object Plugins {
    val compilerPlugins: Seq[ModuleID] = Nil
  }

  object Docs {
    lazy val dedicated: Seq[ModuleID] = Examples.dedicated
  }
}
