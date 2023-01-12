package com.geirolz.example.app.service

import cats.effect.IO
import com.geirolz.example.app.model.{User, UserId}
import com.geirolz.example.app.repository.UserRepository
import com.geirolz.example.app.repository.model.UserEntity

trait UserService[F[_]] {
  def addUser(user: User): F[Unit]
  def findUser(userId: UserId): F[Option[User]]
}
object UserService {

  def apply(userRepository: UserRepository[IO]): UserService[IO] = new UserService[IO] {
    override def addUser(user: User): IO[Unit] =
      userRepository
        .insertUser(UserEntity(user.id, user.name))

    override def findUser(userId: UserId): IO[Option[User]] =
      userRepository
        .fetchUser(userId)
        .map(_.map(e => User(e.id, e.name)))
  }
}
