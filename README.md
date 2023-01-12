# app-toolkit
A small toolkit to build functional app with managed resources

Check the full example [here](https://github.com/geirolz/app-toolkit/tree/main/example) 

- `dependsOn` let you define the app dependencies expressed by a `Resource[F, DEPENDENCIES]`
- `logic` let you define the app logic expressed by an `F[Unit]`
- `provideOne` let you define the app logic expressed by an `Resource[F, Unit]`
- `provide` let you define the app provided services expressed by a `List[Resource[F, Unit]]` which will be run in parallel
- `provideF` let you define the app provided services expressed by a `F[List[Resource[F, Unit]]]` which will be run in parallel

```scala
package com.geirolz.example.app

import cats.effect.{ExitCode, IO, IOApp}
import com.geirolz.app.toolkit.{App, AppResources}
import com.geirolz.app.toolkit.logger.ToolkitLogger
import com.geirolz.app.toolkit.ErrorSyntax.RuntimeExpressionStringCtx
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
      .provideOne(deps => AppHttpService.make(deps.resources.config))
      .use(app =>
        app
          .preRun(_.logger.info("CUSTOM PRE-RUN"))
          .onFinalize(_.logger.info("CUSTOM END"))
          .runForever
          .as(ExitCode.Success)
      )
}
```