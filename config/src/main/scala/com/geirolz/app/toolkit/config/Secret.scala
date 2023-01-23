package com.geirolz.app.toolkit.config

import cats.{Eq, Show}
import com.geirolz.app.toolkit.config.Secret.{BiOffuser, DeOffuser, Offuser, Seed}

import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import scala.collection.BuildFrom
import scala.util.hashing.Hashing
import scala.util.Random

/** The [[Secret]] class represent a secret value of type `T`.
  *
  * The value is implicitly offuscated when creating the [[Secret]] instance using an implicit
  * [[Offuser]] instance which, by default, transform the value into an `Array[Byte]`.
  *
  * The offuscated value is deoffuscated using an implicit [[DeOffuser]] instance every time the
  * method `use` is invoked which returns the original value.
  *
  * Example
  * {{{
  *   val secretString: Secret[String] = Secret("my_password")
  *   val secretValue: String          = secretString.use
  * }}}
  */
final class Secret[T](offuscatedValue: Array[Byte], seed: Seed) {
  def use(implicit deOffuser: DeOffuser[T]): T = deOffuser(offuscatedValue, seed)
  override def equals(obj: Any): Boolean       = false
  override def toString: String                = Secret.placeHolder
}
object Secret extends Instances {

  val placeHolder = "** MASKED **"

  def apply[T: Offuser](value: T, seed: Seed = Random.nextLong()): Secret[T] =
    new Secret(Offuser[T].apply(value, seed), seed)

  def veiled[T: Offuser](value: T): Secret[T] = ???

  // ---------------- OFFUSER ----------------
  private[Secret] type Seed = Long
  trait Offuser[P] extends ((P, Seed) => Array[Byte])
  object Offuser {
    def apply[P: Offuser]: Offuser[P] = implicitly[Offuser[P]]

    def of[P](f: (P, Seed) => Array[Byte]): Offuser[P] = (p, s) => f(p, s)

    def shuffle[P](f: P => Array[Byte]): Offuser[P] =
      Offuser.of((p, s) => Secret.shuffleWithSeed(s)(f(p)).toArray)
  }

  trait DeOffuser[P] extends ((Array[Byte], Seed) => P)
  object DeOffuser {
    def apply[P: DeOffuser]: DeOffuser[P] = implicitly[DeOffuser[P]]

    def of[P](f: (Array[Byte], Seed) => P): DeOffuser[P] = (b, s) => f(b, s)

    def unshuffle[P](f: Array[Byte] => P): DeOffuser[P] =
      DeOffuser.of((bytes, seed) => {
        val perm          = Range(1, bytes.length + 1)
        val shuffled_perm = Secret.shuffleWithSeed(seed)(perm)
        val zipped_ls     = bytes.zip(shuffled_perm)
        f(zipped_ls.sortBy(_._2).map(_._1))
      })
  }

  case class BiOffuser[P](offuser: Offuser[P], deOffuser: DeOffuser[P]) {
    def contramap[U](fO: U => P, fD: P => U): BiOffuser[U] =
      BiOffuser[U](
        offuser   = Offuser.of((plain, seed) => offuser(fO(plain), seed)),
        deOffuser = DeOffuser.of((bytes, seed) => fD(deOffuser(bytes, seed)))
      )
  }
  object BiOffuser {
    def allocateByteBuffer[P](capacity: Int)(
      bOffuser: ByteBuffer => P => ByteBuffer,
      bDeOffuser: ByteBuffer => P
    ): BiOffuser[P] =
      BiOffuser(
        offuser   = Offuser.shuffle(p => bOffuser(ByteBuffer.allocate(capacity)).apply(p).array()),
        deOffuser = DeOffuser.unshuffle(b => bDeOffuser(ByteBuffer.wrap(b)))
      )
  }

  private def shuffleWithSeed[T, C](
    seed: Seed
  )(xs: IterableOnce[T])(implicit bf: BuildFrom[xs.type, T, C]): C = synchronized {
    Random.setSeed(seed)
    Random.shuffle(xs)
  }
}
sealed trait Instances {

  implicit val stringBiOffuser: BiOffuser[String] =
    BiOffuser(
      Offuser.shuffle(_.getBytes(StandardCharsets.UTF_8)),
      DeOffuser.unshuffle(off => new String(off, StandardCharsets.UTF_8))
    )

  implicit val byteBiOffuser: BiOffuser[Byte] =
    BiOffuser(Offuser.shuffle(i => Array(i)), DeOffuser.unshuffle(_.head))

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
      Offuser.shuffle(i => if (i) Array(1) else Array(0)),
      DeOffuser.unshuffle(_.head match {
        case 1 => true
        case 0 => false
      })
    )

  implicit val bigIntBiOffuser: BiOffuser[BigInt] =
    BiOffuser(Offuser.shuffle(_.toByteArray), DeOffuser.unshuffle(BigInt(_)))

  implicit val bigDecimalBiOffuser: BiOffuser[BigDecimal] =
    stringBiOffuser.contramap(_.toString, str => BigDecimal(str))

  implicit def unzipBiOffuserToOffuser[P: BiOffuser]: Offuser[P] =
    implicitly[BiOffuser[P]].offuser

  implicit def unzipBiOffuserToDeOffuser[P: BiOffuser]: DeOffuser[P] =
    implicitly[BiOffuser[P]].deOffuser

  implicit def hashing[T]: Hashing[Secret[T]] =
    Hashing.default

  implicit def eq[T]: Eq[Secret[T]] =
    (_, _) => false

  implicit def show[T]: Show[Secret[T]] =
    _ => Secret.placeHolder
}
