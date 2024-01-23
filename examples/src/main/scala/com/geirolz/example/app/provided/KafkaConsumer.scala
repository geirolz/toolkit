package com.geirolz.example.app.provided

import cats.effect.IO
import com.comcast.ip4s.Hostname
import com.geirolz.example.app.provided.KafkaConsumer.KafkaRecord

import scala.annotation.unused
import scala.concurrent.duration.DurationInt

trait KafkaConsumer[F[_]]:
  def consumeFrom(@unused name: String): fs2.Stream[F, KafkaRecord]

object KafkaConsumer:

  case class KafkaRecord(value: String)

  def fake(@unused host: Hostname): KafkaConsumer[IO] =
    (name: String) =>
      fs2.Stream
        .eval(IO.randomUUID.map(t => KafkaRecord(t.toString)).flatTap(_ => IO.sleep(5.seconds)))
        .repeat
