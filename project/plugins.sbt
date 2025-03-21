ThisBuild / libraryDependencies += "org.typelevel" %% "scalac-options" % "0.1.8"

addSbtPlugin("org.scalameta"  % "sbt-scalafmt"           % "2.5.4")
addSbtPlugin("org.scoverage"  % "sbt-scoverage"          % "2.3.1")
addSbtPlugin("com.github.sbt" % "sbt-ci-release"         % "1.9.3")
addSbtPlugin("org.scalameta"  % "sbt-mdoc"               % "2.6.4")
addSbtPlugin("com.eed3si9n"   % "sbt-buildinfo"          % "0.13.1")
addSbtPlugin("org.jmotor.sbt" % "sbt-dependency-updates" % "1.2.9")
addSbtPlugin("com.typesafe"   % "sbt-mima-plugin"        % "1.1.4")
