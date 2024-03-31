package com.geirolz.example.app

import cats.effect.{IO, Resource}

import scala.io.Source

case class AppResources(
  hostTableValues: List[String]
)

object AppResources:
  def resource: Resource[IO, AppResources] =
    Resource
      .fromAutoCloseable(IO(Source.fromResource("host-table.txt")))
      .map(_.getLines().toList)
      .map(AppResources(_))
