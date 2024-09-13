package com.geirolz.example.app

import cats.effect.{IO, Resource}

import scala.io.Source

case class AppResources(
  hostTableValues: List[String]
)

object AppResources:
  def load: IO[AppResources] =
    Resource
      .fromAutoCloseable(IO(Source.fromResource("host-table.txt")))
      .use(file => IO(AppResources(file.getLines().toList)))
