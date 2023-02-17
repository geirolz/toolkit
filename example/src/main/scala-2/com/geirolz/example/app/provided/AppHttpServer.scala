package com.geirolz.example.app.provided

import com.geirolz.example.app.AppConfig
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server

object AppHttpServer {

  import cats.effect.*
  import org.http4s.*
  import org.http4s.dsl.io.*

  def resource(config: AppConfig): Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(config.httpServer.host)
      .withPort(config.httpServer.port)
      .withHttpApp(
        HttpRoutes
          .of[IO] { case GET -> Root / "hello" / name =>
            Ok(s"Hello, $name.")
          }
          .orNotFound
      )
      .build
}
