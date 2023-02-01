# app-toolkit
[![Build Status](https://github.com/geirolz/app-toolkit/actions/workflows/cicd.yml/badge.svg)](https://github.com/geirolz/app-toolkit/actions)
[![codecov](https://img.shields.io/codecov/c/github/geirolz/app-toolkit)](https://codecov.io/gh/geirolz/app-toolkit)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/db3274b55e0c4031803afb45f58d4413)](https://www.codacy.com/manual/david.geirola/app-toolkit?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=geirolz/app-toolkit&amp;utm_campaign=Badge_Grade)
[![Sonatype Nexus (Releases)](https://img.shields.io/nexus/r/com.github.geirolz/app-toolkit-core_2.13?server=https%3A%2F%2Foss.sonatype.org)](https://mvnrepository.com/artifact/com.github.geirolz/app-toolkit-core)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
[![Mergify Status](https://img.shields.io/endpoint.svg?url=https://api.mergify.com/v1/badges/geirolz/app-toolkit&style=flat)](https://mergify.io)
[![GitHub license](https://img.shields.io/github/license/geirolz/app-toolkit)](https://github.com/geirolz/app-toolkit/blob/main/LICENSE)

A small toolkit to build functional app with managed resources

```sbt
libraryDependencies += "com.github.geirolz" %% "app-toolkit-core" % "@VERSION@"
```

Check the full example [here](https://github.com/geirolz/app-toolkit/tree/main/example)

- `dependsOn` let you define the app dependencies expressed by a `Resource[F, DEPENDENCIES]`
- `provideOne` let you define the app logic expressed by an `F[?]`
- `provide` let you define the app provided services expressed by a `List[F[?]]` which will be run in parallel
- `provideF` let you define the app provided services expressed by a `F[List[F[?]]]` which will be run in parallel

```scala
package com.geirolz.example.app

import cats.effect.{ExitCode, IO, IOApp}
import com.geirolz.app.toolkit.{App, AppResources}
import com.geirolz.app.toolkit.logger.ToolkitLogger
import com.geirolz.app.toolkit.error._
import com.geirolz.example.app.service.UserService
import com.geirolz.example.app.model.*

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    App[IO]
      .withResourcesLoader(
        AppResources
          .loader[IO, AppInfo](AppInfo.fromBuildInfo)
          .withLogger(ToolkitLogger.console[IO](_))
          .withConfigLoader(_ => IO(ConfigSource.default.loadOrThrow[AppConfig]))
      )
      .dependsOn(AppDependencyServices.make(_))
      .provide(deps =>
        List(
          // HTTP server
          AppHttpServer.make(deps.config).useForever,

          // Kafka consumer
          deps.dependencies.kafkaConsumer
            .consumeFrom("test-topic")
            .evalTap(record => deps.logger.info(s"Received record $record"))
            .compile
            .drain
        )
      )
      .use(app =>
        app
          .preRun(_.logger.info("CUSTOM PRE-RUN"))
          .onFinalize(_.logger.info("CUSTOM END"))
          .run
          .as(ExitCode.Success)
      )
}
```


### Integrations
#### pureconfig 
```sbt
libraryDependencies += "com.github.geirolz" %% "app-toolkit-config-pureconfig" % "@VERSION@"
```

#### log4cats
```sbt
libraryDependencies += "com.github.geirolz" %% "app-toolkit-log4cats" % "@VERSION@"
```

#### odin
```sbt
libraryDependencies += "com.github.geirolz" %% "app-toolkit-odin" % "@VERSION@"
```