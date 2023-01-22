package com.geirolz.app.toolkit.config

import cats.{Eq, Show}
import com.geirolz.app.toolkit.config.Secret.{BiOffuser, DeOffuser, Offuser}

import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer

final class Secret[T](private val offuscatedValue: Array[Byte]) {

  def use(implicit deOffuser: DeOffuser[T]): T =
    deOffuser(offuscatedValue)

  override def equals(obj: Any): Boolean = false
  override def toString: String          = Secret.placeHolder
}
object Secret extends Instances {

  val placeHolder = "** MASKED **"

  def apply[T: Offuser](value: T): Secret[T] =
    new Secret(implicitly[Offuser[T]].apply(value))

  // ---------------- OFFUSER ----------------
  trait Offuser[P] extends (P => Array[Byte])
  object Offuser { def apply[P](f: P => Array[Byte]): Offuser[P] = p => f(p) }

  trait DeOffuser[P] extends (Array[Byte] => P)
  object DeOffuser { def apply[P](f: Array[Byte] => P): DeOffuser[P] = bytes => f(bytes) }

  case class BiOffuser[P](offuser: Offuser[P], deOffuser: DeOffuser[P]) {
    def contramap[U](fO: U => P, fD: P => U): BiOffuser[U] =
      BiOffuser[U](
        offuser   = Offuser.apply(offuser.compose(fO)),
        deOffuser = DeOffuser(deOffuser.andThen(fD))
      )
  }
  object BiOffuser {
    def allocateByteBuffer[P](capacity: Int)(
      bOffuser: ByteBuffer => (P => ByteBuffer),
      bDeOffuser: ByteBuffer => P
    ): BiOffuser[P] =
      BiOffuser(
        offuser   = plain => bOffuser(ByteBuffer.allocate(capacity)).apply(plain).array(),
        deOffuser = bytes => bDeOffuser(ByteBuffer.wrap(bytes))
      )
  }
}
sealed trait Instances {

  implicit val stringBiOffuser: BiOffuser[String] =
    BiOffuser(_.getBytes(StandardCharsets.UTF_8), off => new String(off, StandardCharsets.UTF_8))

  implicit val byteBiOffuser: BiOffuser[Byte] =
    BiOffuser(i => Array(i), bytes => bytes.head)

  implicit val charBiOffuser: BiOffuser[Char] =
    BiOffuser.allocateByteBuffer(2)(_.putChar, _.getChar)

  implicit val intBiOffuser: BiOffuser[Int] =
    BiOffuser.allocateByteBuffer(4)(_.putInt, _.getInt)

  implicit val shortBiOffuser: BiOffuser[Short] =
    BiOffuser.allocateByteBuffer(2)(_.putShort, _.getShort)

  implicit val floatBiOffuser: BiOffuser[Float] =
    BiOffuser.allocateByteBuffer(4)(_.putFloat, _.getFloat)

  implicit val doubleBiOffuser: BiOffuser[Double] =
    BiOffuser.allocateByteBuffer(8)(_.putDouble, _.getDouble)

  implicit val boolBiOffuser: BiOffuser[Boolean] =
    BiOffuser(
      i => if (i) Array(1) else Array(0),
      bytes =>
        bytes.head match {
          case 1 => true
          case 0 => false
        }
    )

  implicit val bigIntBiOffuser: BiOffuser[BigInt] =
    BiOffuser(_.toByteArray, BigInt(_))

  implicit val bigDecimalBiOffuser: BiOffuser[BigDecimal] =
    stringBiOffuser.contramap(_.toString, str => BigDecimal(str))

  implicit def unzipBiOffuserToOffuser[P: BiOffuser]: Offuser[P] =
    implicitly[BiOffuser[P]].offuser

  implicit def unzipBiOffuserToDeOffuser[P: BiOffuser]: DeOffuser[P] =
    implicitly[BiOffuser[P]].deOffuser

  implicit def eq[T]: Eq[Secret[T]] =
    (_, _) => false

  implicit def show[T]: Show[Secret[T]] =
    _ => Secret.placeHolder
}
