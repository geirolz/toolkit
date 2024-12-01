import sbt.*
import scala.language.postfixOps
import org.typelevel.scalacoptions.ScalaVersion

object PrjDependencies {

  object Versions {
    type Version = String
    val cats: Version              = "2.12.0"
    val catsEffect: Version        = "3.5.6"
    val circe: Version             = "0.14.9"
    val circeGenericExtra: Version = "0.14.3"
    val pureConfig: Version        = "0.17.7"
    val fly4s: Version             = "1.1.0"
    val munit: Version             = "1.0.2"
    val munitEffect: Version       = "2.0.0"
    val slf4: Version              = "2.0.16"
    val log4cats: Version          = "2.7.0"
    val odin: Version              = "0.15.0"
    val http4s: Version            = "0.23.29"
    val fs2: Version               = "3.11.0"
    val scalacheck: Version        = "1.18.1"
  }

  lazy val common: Seq[ModuleID] = Seq(
    // runtime
    "org.typelevel" %% "cats-core" % Versions.cats,

    // test
    "org.scalameta"  %% "munit"             % Versions.munit       % Test,
    "org.scalameta"  %% "munit-scalacheck"  % Versions.munit       % Test,
    "org.typelevel"  %% "munit-cats-effect" % Versions.munitEffect % Test,
    "org.scalacheck" %% "scalacheck"        % Versions.scalacheck  % Test
  )

  object Core {
    lazy val dedicated: Seq[ModuleID] = Seq(
      // runtime
      "org.typelevel" %% "cats-effect" % Versions.catsEffect
    )
  }

  object Config {
    lazy val dedicated: Seq[ModuleID] = Nil
  }

  object Testing {
    lazy val dedicated: Seq[ModuleID] = Seq(
      // runtime
      "org.typelevel" %% "cats-effect" % Versions.catsEffect
    )
  }

  object Examples {

    lazy val dedicated: Seq[ModuleID] = Seq(
      // http
      "org.http4s" %% "http4s-dsl"          % Versions.http4s,
      "org.http4s" %% "http4s-ember-server" % Versions.http4s,

      // streaming
      "co.fs2" %% "fs2-core" % Versions.fs2,

      // logging
      "org.typelevel" %% "log4cats-slf4j" % Versions.log4cats,
      "org.slf4j"      % "slf4j-api"      % Versions.slf4,
      "org.slf4j"      % "slf4j-simple"   % Versions.slf4,

      // config
      "com.github.pureconfig" %% "pureconfig-http4s" % Versions.pureConfig,
      "com.github.pureconfig" %% "pureconfig-ip4s"   % Versions.pureConfig,

      // json
      "io.circe" %% "circe-core"    % Versions.circe,
      "io.circe" %% "circe-refined" % Versions.circe,
      "io.circe" %% "circe-generic" % Versions.circe
    )
  }

  object Integrations {

    object Log4cats {
      lazy val dedicated: Seq[ModuleID] = List(
        "org.typelevel" %% "log4cats-core" % Versions.log4cats,
        "org.typelevel" %% "log4cats-noop" % Versions.log4cats % Test
      )
    }

    object Odin {
      lazy val dedicated: Seq[ModuleID] = List(
        "dev.scalafreaks" %% "odin-core" % Versions.odin
      )
    }

    object Pureconfig {
      lazy val dedicated: Seq[ModuleID] = List(
        "com.github.pureconfig" %% "pureconfig-core" % Versions.pureConfig
      )
    }

    object Fly4s {
      lazy val dedicated: Seq[ModuleID] = List(
        "com.github.geirolz" %% "fly4s" % Versions.fly4s
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
