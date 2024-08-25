import sbt.project

lazy val prjName                = "toolkit"
lazy val prjDescription         = "A small toolkit to build functional app with managed resources"
lazy val org                    = "com.github.geirolz"
lazy val scala33                = "3.3.3"
lazy val scala34                = "3.5.0"
lazy val supportedScalaVersions = List(scala33, scala34)

//## global project to no publish ##
val copyReadMe = taskKey[Unit]("Copy generated README to main folder.")
lazy val root: Project = project
  .in(file("."))
  .settings(
    inThisBuild(
      List(
        homepage := Some(url(s"https://github.com/geirolz/$prjName")),
        licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
        developers := List(
          Developer(
            "DavidGeirola",
            "David Geirola",
            "david.geirola@gmail.com",
            url("https://github.com/geirolz")
          )
        )
      )
    )
  )
  .settings(baseSettings)
  .settings(noPublishSettings)
  .settings(
    crossScalaVersions := Nil
  )
  .settings(
    copyReadMe := IO.copyFile(file("docs/compiled/README.md"), file("README.md"))
  )
  .aggregate(core, docs, testing, log4cats, odin, pureconfig, fly4s)

lazy val docs: Project =
  project
    .in(file("docs"))
    .enablePlugins(MdocPlugin)
    .dependsOn(core, log4cats, odin, pureconfig, fly4s)
    .settings(
      baseSettings,
      noPublishSettings,
      libraryDependencies ++= ProjectDependencies.Docs.dedicated,
      // config
      scalacOptions --= Seq("-Werror", "-Xfatal-warnings"),
      mdocIn  := file("docs/source"),
      mdocOut := file("docs/compiled"),
      mdocVariables := Map(
        "VERSION"  -> previousStableVersion.value.getOrElse("<version>"),
        "DOC_OUT"  -> mdocOut.value.getPath,
        "PRJ_NAME" -> prjName,
        "ORG"      -> org
      )
    )

lazy val core: Project =
  module("core")(
    folder    = "./core",
    publishAs = Some(prjName)
  ).settings(
    libraryDependencies ++= ProjectDependencies.Core.dedicated
  ).dependsOn(testing)

lazy val testing: Project =
  module("testing")(
    folder    = "./testing",
    publishAs = Some(subProjectName("testing"))
  ).settings(
    libraryDependencies ++= ProjectDependencies.Testing.dedicated
  )

lazy val examples: Project = {
  val appPackage: String = "com.geirolz.example.app"
  module("examples")(
    folder = "./examples"
  ).enablePlugins(BuildInfoPlugin)
    .settings(
      noPublishSettings,
      Compile / mainClass := Some(s"$appPackage.AppMain"),
      libraryDependencies ++= ProjectDependencies.Examples.dedicated,
      buildInfoKeys ++= List[BuildInfoKey](
        name,
        description,
        version,
        scalaVersion,
        sbtVersion,
        buildInfoBuildNumber
      ),
      buildInfoOptions ++= List(
        BuildInfoOption.BuildTime,
        BuildInfoOption.PackagePrivate,
        BuildInfoOption.ConstantValue
      ),
      buildInfoPackage := appPackage
    )
    .dependsOn(core, log4cats, pureconfig)
}

// integrations
lazy val integrationsFolder: String = "./integrations"
lazy val log4cats: Project =
  module("log4cats")(
    folder    = s"$integrationsFolder/log4cats",
    publishAs = Some(subProjectName("log4cats"))
  ).dependsOn(core)
    .settings(
      libraryDependencies ++= ProjectDependencies.Integrations.Log4cats.dedicated
    )

lazy val odin: Project =
  module("odin")(
    folder    = s"$integrationsFolder/odin",
    publishAs = Some(subProjectName("odin"))
  ).dependsOn(core)
    .settings(
      libraryDependencies ++= ProjectDependencies.Integrations.Odin.dedicated
    )

lazy val pureconfig: Project =
  module("pureconfig")(
    folder    = s"$integrationsFolder/pureconfig",
    publishAs = Some(subProjectName("pureconfig"))
  ).dependsOn(core)
    .settings(
      libraryDependencies ++= ProjectDependencies.Integrations.Pureconfig.dedicated
    )

lazy val fly4s: Project =
  module("fly4s")(
    folder    = s"$integrationsFolder/fly4s",
    publishAs = Some(subProjectName("fly4s"))
  ).dependsOn(core)
    .settings(
      libraryDependencies ++= ProjectDependencies.Integrations.Fly4s.dedicated
    )

//=============================== MODULES UTILS ===============================
def module(modName: String)(
  folder: String,
  publishAs: Option[String]       = None,
  mimaCompatibleWith: Set[String] = Set.empty
): Project = {
  val keys       = modName.split("-")
  val modDocName = keys.mkString(" ")
  val publishSettings = publishAs match {
    case Some(pubName) =>
      Seq(
        moduleName     := pubName,
        publish / skip := false
      )
    case None => noPublishSettings
  }
  val mimaSettings = Seq(
    mimaFailOnNoPrevious := false,
    mimaPreviousArtifacts := mimaCompatibleWith.map { version =>
      organization.value % s"${moduleName.value}_${scalaBinaryVersion.value}" % version
    }
  )

  Project(modName, file(folder))
    .settings(
      name := s"$prjName $modDocName",
      mimaSettings,
      publishSettings,
      baseSettings
    )
}

def subProjectName(modPublishName: String): String = s"$prjName-$modPublishName"

//=============================== SETTINGS ===============================
lazy val noPublishSettings: Seq[Def.Setting[_]] = Seq(
  publish              := {},
  publishLocal         := {},
  publishArtifact      := false,
  publish / skip       := true,
  mimaFailOnNoPrevious := false
)

lazy val baseSettings: Seq[Def.Setting[_]] = Seq(
  // project
  name         := prjName,
  description  := prjDescription,
  organization := org,
  // scala
  crossScalaVersions := supportedScalaVersions,
  scalaVersion       := supportedScalaVersions.head,
  scalacOptions ++= scalacSettings(scalaVersion.value),
  versionScheme := Some("early-semver"),
  // dependencies
  resolvers ++= ProjectResolvers.all,
  libraryDependencies ++= Seq(
    ProjectDependencies.common,
    ProjectDependencies.Plugins.compilerPlugins
  ).flatten
)

def scalacSettings(scalaVersion: String): Seq[String] =
  Seq(
    "-encoding",
    "utf-8", // Specify character encoding used by source files.
    "-explain",
    "-deprecation",
    "-feature", // Emit warning and location for usages of features that should be imported explicitly.
    "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
    "-language:higherKinds", // Allow higher-kinded types
    "-language:implicitConversions", // Allow definition of implicit functions called views
    "-language:dynamics",
    "-Ykind-projector",
    "-explain-types", // Explain type errors in more detail.
    "-Xfatal-warnings" // Fail the compilation if there are any warnings.
  )

//=============================== ALIASES ===============================
addCommandAlias("check", "scalafmtAll;clean;coverage;test;coverageAggregate")
addCommandAlias("gen-doc", "mdoc;copyReadMe;")
addCommandAlias("coverage-test", "coverage;test;coverageReport")
