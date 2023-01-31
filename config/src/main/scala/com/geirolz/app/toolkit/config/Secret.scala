package com.geirolz.app.toolkit.config

import cats.{Eq, Show}
import com.geirolz.app.toolkit.config.Secret.{
  DeOffuser,
  NoLongerValidSecret,
  Offuser,
  OffuserTuple,
  Seed
}

import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import java.util
import java.util.Objects
import scala.collection.BuildFrom
import scala.util.hashing.Hashing
import scala.util.Random

/** The [[Secret]] class represent a secret value of type `T`.
  *
  * The value is implicitly offuscated when creating the [[Secret]] instance using an implicit
  * [[Offuser]] instance which, by default, transform the value into a shuffled `Array[Byte]`.
  *
  * The offuscated value is de-offuscated using an implicit [[DeOffuser]] instance every time the
  * method `use` is invoked which returns the original value un-shuffling the bytes and converting
  * them back to `T`.
  *
  * Example
  * {{{
  *   val secretString: Secret[String]                      = Secret("my_password")
  *   val secretValue: Either[NoLongerValidSecret, String]  = secretString.use
  * }}}
  */
final class Secret[T](private var offuscatedValue: Array[Byte], seed: Seed) {

  private var destroyed: Boolean = false

  def unsafeUse(implicit deOffuser: DeOffuser[T]): T =
    use match {
      case Left(ex)     => throw ex
      case Right(value) => value
    }

  def use(implicit deOffuser: DeOffuser[T]): Either[NoLongerValidSecret, T] = {
    if (isDestroyed)
      Left(NoLongerValidSecret())
    else
      Right(deOffuser(offuscatedValue, seed))
  }

  def useAndDestroy(implicit deOffuser: DeOffuser[T]): Either[NoLongerValidSecret, T] =
    use.map(value => {
      destroy()
      value
    })

  def destroy(): Unit =
    if (!destroyed) {
      util.Arrays.fill(offuscatedValue, 0.toByte)
      destroyed = true
      System.gc()
    }

  def isDestroyed: Boolean = destroyed

  override def equals(obj: Any): Boolean = false

  override def toString: String = Secret.placeHolder

  override def hashCode(): Int =
    if (isDestroyed) -1 else Objects.hash(seed)
}

object Secret extends Instances {

  val placeHolder = "** MASKED **"

  case class NoLongerValidSecret() extends RuntimeException("This key is no longer valid")

  def apply[T: Offuser](value: T, seed: Seed = Random.nextLong()): Secret[T] =
    new Secret(Offuser[T].apply(value, seed), seed)

  // ---------------- OFFUSER ----------------
  private[Secret] type Seed = Long
  trait Offuser[P] extends ((P, Seed) => Array[Byte])
  object Offuser {
    def apply[P: Offuser]: Offuser[P] = implicitly[Offuser[P]]

    def of[P](f: (P, Seed) => Array[Byte]): Offuser[P] = (p, s) => f(p, s)

    def shuffle[P](f: P => Array[Byte]): Offuser[P] =
      Offuser.of((plain, seed) => {
        Secret.shuffleWithSeed(seed)(f(plain)).toArray
      })
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

  case class OffuserTuple[P](offuser: Offuser[P], deOffuser: DeOffuser[P]) {
    def bimap[U](fO: U => P, fD: P => U): OffuserTuple[U] =
      OffuserTuple[U](
        offuser   = Offuser.of((plain, seed) => offuser(fO(plain), seed)),
        deOffuser = DeOffuser.of((bytes, seed) => fD(deOffuser(bytes, seed)))
      )
  }
  object OffuserTuple {
    def allocateByteBuffer[P](capacity: Int)(
      bOffuser: ByteBuffer => P => ByteBuffer,
      bDeOffuser: ByteBuffer => P
    ): OffuserTuple[P] =
      OffuserTuple(
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

  implicit val stringBiOffuser: OffuserTuple[String] =
    OffuserTuple(
      Offuser.shuffle(_.getBytes(StandardCharsets.UTF_8)),
      DeOffuser.unshuffle(off => new String(off, StandardCharsets.UTF_8))
    )

  implicit val byteBiOffuser: OffuserTuple[Byte] =
    OffuserTuple(Offuser.shuffle(i => Array(i)), DeOffuser.unshuffle(_.head))

  implicit val charBiOffuser: OffuserTuple[Char] =
    OffuserTuple.allocateByteBuffer(2)(_.putChar, _.getChar)

  implicit val intBiOffuser: OffuserTuple[Int] =
    OffuserTuple.allocateByteBuffer(4)(_.putInt, _.getInt)

  implicit val shortBiOffuser: OffuserTuple[Short] =
    OffuserTuple.allocateByteBuffer(2)(_.putShort, _.getShort)

  implicit val floatBiOffuser: OffuserTuple[Float] =
    OffuserTuple.allocateByteBuffer(4)(_.putFloat, _.getFloat)

  implicit val doubleBiOffuser: OffuserTuple[Double] =
    OffuserTuple.allocateByteBuffer(8)(_.putDouble, _.getDouble)

  implicit val boolBiOffuser: OffuserTuple[Boolean] =
    OffuserTuple(
      Offuser.shuffle(i => if (i) Array(1) else Array(0)),
      DeOffuser.unshuffle(_.head match {
        case 1 => true
        case 0 => false
      })
    )

  implicit val bigIntBiOffuser: OffuserTuple[BigInt] =
    OffuserTuple(Offuser.shuffle(_.toByteArray), DeOffuser.unshuffle(BigInt(_)))

  implicit val bigDecimalBiOffuser: OffuserTuple[BigDecimal] =
    stringBiOffuser.bimap(_.toString, str => BigDecimal(str))

  implicit def unzipBiOffuserToOffuser[P: OffuserTuple]: Offuser[P] =
    implicitly[OffuserTuple[P]].offuser

  implicit def unzipBiOffuserToDeOffuser[P: OffuserTuple]: DeOffuser[P] =
    implicitly[OffuserTuple[P]].deOffuser

  implicit def hashing[T]: Hashing[Secret[T]] =
    Hashing.fromFunction(_.hashCode())

  implicit def eq[T]: Eq[Secret[T]] =
    (_, _) => false

  implicit def show[T]: Show[Secret[T]] =
    _ => Secret.placeHolder
}
