package com.geirolz.example.app

import cats.effect.{IO, Resource}
import com.geirolz.app.toolkit.AppResources
import com.geirolz.app.toolkit.logger.ToolkitLogger
import com.geirolz.example.app.repository.UserRepository
import com.geirolz.example.app.service.UserService

import scala.annotation.unused

case class AppDependencyServices(
  userService: UserService[IO]
)
object AppDependencyServices {

  def make(
    @unused resources: AppResources[AppInfo, ToolkitLogger[IO], AppConfig]
  ): Resource[IO, AppDependencyServices] =
    for {

      // ----------------- REPOSITORY ---------------
      userRepository <- Resource.eval(UserRepository.inMemory)

    } yield AppDependencyServices(
      userService = UserService(userRepository)
    )
}
