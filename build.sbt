import org.typelevel.scalacoptions.{ScalaVersion, ScalacOptions}
import sbt.project

lazy val prjName        = "toolkit"
lazy val prjDescription = "A small toolkit to build functional app with managed resources"
lazy val prjOrg         = "com.github.geirolz"
lazy val supportedScalaVersions: Set[ScalaVersion] = Set(
  ScalaVersion.V3_5_0
)

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
      libraryDependencies ++= PrjDependencies.Docs.dedicated,
      // config
      scalacOptions --= Seq("-Werror", "-Xfatal-warnings"),
      mdocIn  := file("docs/source"),
      mdocOut := file("docs/compiled"),
      mdocVariables := Map(
        "VERSION"  -> previousStableVersion.value.getOrElse("<version>"),
        "DOC_OUT"  -> mdocOut.value.getPath,
        "PRJ_NAME" -> prjName,
        "ORG"      -> prjOrg
      )
    )

lazy val core: Project =
  module("core")(
    folder    = "./core",
    publishAs = Some(prjName)
  ).settings(
    libraryDependencies ++= PrjDependencies.Core.dedicated
  ).dependsOn(testing)

lazy val testing: Project =
  module("testing")(
    folder    = "./testing",
    publishAs = Some(subProjectName("testing"))
  ).settings(
    libraryDependencies ++= PrjDependencies.Testing.dedicated
  )

lazy val examples: Project = {
  val appPackage: String = "com.geirolz.example.app"
  module("examples")(
    folder = "./examples"
  ).enablePlugins(BuildInfoPlugin)
    .settings(
      noPublishSettings,
      Compile / mainClass := Some(s"$appPackage.AppMain"),
      libraryDependencies ++= PrjDependencies.Examples.dedicated,
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
      libraryDependencies ++= PrjDependencies.Integrations.Log4cats.dedicated
    )

lazy val odin: Project =
  module("odin")(
    folder    = s"$integrationsFolder/odin",
    publishAs = Some(subProjectName("odin"))
  ).dependsOn(core)
    .settings(
      libraryDependencies ++= PrjDependencies.Integrations.Odin.dedicated
    )

lazy val pureconfig: Project =
  module("pureconfig")(
    folder    = s"$integrationsFolder/pureconfig",
    publishAs = Some(subProjectName("pureconfig"))
  ).dependsOn(core)
    .settings(
      libraryDependencies ++= PrjDependencies.Integrations.Pureconfig.dedicated
    )

lazy val fly4s: Project =
  module("fly4s")(
    folder    = s"$integrationsFolder/fly4s",
    publishAs = Some(subProjectName("fly4s"))
  ).dependsOn(core)
    .settings(
      libraryDependencies ++= PrjDependencies.Integrations.Fly4s.dedicated
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
  organization := prjOrg,
  // scala
  crossScalaVersions := supportedScalaVersions.map(v => s"${v.major}.${v.minor}.${v.patch}").toSeq,
  scalaVersion       := supportedScalaVersions.headOption.map(v => s"${v.major}.${v.minor}.${v.patch}").get,
  scalacOptions ++= ScalacOptions.tokensForVersion(
    scalaVersion          = supportedScalaVersions.head,
    proposedScalacOptions = PrjCompilerOptions.common
  ),
  versionScheme := Some("early-semver"),
  // dependencies
  resolvers ++= PrjResolvers.all,
  libraryDependencies ++= Seq(
    PrjDependencies.common,
    PrjDependencies.Plugins.compilerPlugins
  ).flatten
)

//=============================== ALIASES ===============================
addCommandAlias("check", "scalafmtAll;clean;coverage;test;coverageAggregate")
addCommandAlias("gen-doc", "mdoc;copyReadMe;")
addCommandAlias("coverage-test", "coverage;test;coverageReport")
