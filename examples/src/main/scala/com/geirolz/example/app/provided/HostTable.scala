package com.geirolz.example.app.provided

import cats.effect.IO

trait HostTable[F[_]]:
  def findByName(hostname: String): F[Option[String]]

object HostTable:
  def fromString(values: List[String]): HostTable[IO] = new HostTable[IO]:
    private val map: Map[String, String] =
      values.flatMap {
        case s"$hostname:$ip" => Some(hostname -> ip)
        case _                => None
      }.toMap

    def findByName(hostname: String): IO[Option[String]] =
      IO.pure(map.get(hostname))
