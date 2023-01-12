package com.geirolz.example.app

import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server

object AppHttpService {

  import cats.effect.*
  import org.http4s.*
  import org.http4s.dsl.io.*

  def make(config: AppConfig): Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(config.server.host)
      .withPort(config.server.port)
      .withHttpApp(
        HttpRoutes
          .of[IO] { case GET -> Root / "hello" / name =>
            Ok(s"Hello, $name.")
          }
          .orNotFound
      )
      .build
}
