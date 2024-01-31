package com.geirolz.app.toolkit

import cats.effect.{ExitCode, IO, IOApp}

object IOApp:
  trait Toolkit extends IOApp:
    export com.geirolz.app.toolkit.ctx
    val app: App[IO, ?, ?, ?, ?, ?, ?]
    def run(args: List[String]): IO[ExitCode] = app.run(args)
