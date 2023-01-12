package com.geirolz.example.app.repository

import cats.effect.IO
import com.geirolz.example.app.model.UserId
import com.geirolz.example.app.repository.model.UserEntity

trait UserRepository[F[_]] {
  def insertUser(user: UserEntity): F[Unit]
  def fetchUser(userId: UserId): F[Option[UserEntity]]
}
object UserRepository {

  def inMemory: IO[UserRepository[IO]] =
    IO.ref(Map.empty[UserId, UserEntity])
      .map(store =>
        new UserRepository[IO] {
          override def insertUser(user: UserEntity): IO[Unit] =
            store.update(db => db + (user.id -> user))

          override def fetchUser(userId: UserId): IO[Option[UserEntity]] =
            store.get.map(_.get(userId))
        }
      )

}
