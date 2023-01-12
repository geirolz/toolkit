package com.geirolz.example.app

import cats.effect.{ExitCode, IO, IOApp}
import com.geirolz.app.toolkit.{App, AppResources}
import com.geirolz.app.toolkit.logger.ToolkitLogger
import com.geirolz.example.app.service.UserService
import com.geirolz.example.app.model.*

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    App[IO]
      .withResourcesLoader(
        AppResources
          .loader[IO, AppInfo](AppInfo.fromBuildInfo)
          .withLogger(ToolkitLogger.console[IO](_))
      )
      .dependsOn(AppDependencyServices.make(_))
      .logic(deps => {

        val logger                       = deps.resources.logger
        val userService: UserService[IO] = deps.dependencies.userService

        for {
          userToSave <- IO.pure(User(UserId(1), "Foo"))
          _          <- userService.addUser(userToSave)
          _          <- logger.info(s"Saved user [$userToSave]")
          user       <- userService.findUser(UserId(1))
          _          <- logger.info(s"Fetching result [$user]")
        } yield ()
      })
      .use(_.run.as(ExitCode.Success))
}
