package com.geirolz.app.toolkit.config

import cats.{Eq, Show}
import com.geirolz.app.toolkit.config.Secret.{DeObfuser, NoLongerValidSecret, Obfuser, ObfuserTuple, Seed}

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util
import java.util.Objects
import scala.util.Random
import scala.util.hashing.Hashing

/** The `Secret` class represent a secret value of type `T`.
  *
  * The value is implicitly obfuscated when creating the `Secret` instance using an implicit `Obfuser` instance which, by default, transform the value
  * into a shuffled xor-ed `Array[Byte]`.
  *
  * The obfuscated value is de-obfuscated using an implicit `DeObfuser` instance every time the method `use` is invoked which returns the original
  * value un-shuffling the bytes and converting them back to `T` re-apply the xor.
  *
  * Example
  * {{{
  *   val secretString: Secret[String]                      = Secret("my_password")
  *   val secretValue: Either[NoLongerValidSecret, String]  = secretString.use
  * }}}
  */
final class Secret[T](private var obfuscatedValue: Array[Byte], seed: Seed) {

  private var destroyed: Boolean = false

  def unsafeUse(implicit deObfuser: DeObfuser[T]): T =
    use match {
      case Left(ex)     => throw ex
      case Right(value) => value
    }

  def use(implicit deObfuser: DeObfuser[T]): Either[NoLongerValidSecret, T] = {
    if (isDestroyed)
      Left(NoLongerValidSecret())
    else
      Right(deObfuser(obfuscatedValue, seed))
  }

  def useAndDestroy(implicit deObfuser: DeObfuser[T]): Either[NoLongerValidSecret, T] =
    use.map(value => {
      destroy()
      value
    })

  def destroy(): Unit =
    if (!destroyed) {
      util.Arrays.fill(obfuscatedValue, 0.toByte)
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

  val placeHolder = "** SECRET **"

  case class NoLongerValidSecret() extends RuntimeException("This key is no longer valid")

  def apply[T: Obfuser](value: T, seed: Seed = Random.nextLong()): Secret[T] =
    new Secret(Obfuser[T].apply(value, seed), seed)

  // ---------------- OBFUSER ----------------
  private[Secret] type Seed = Long
  trait Obfuser[P] extends ((P, Seed) => Array[Byte])
  object Obfuser {
    def apply[P: Obfuser]: Obfuser[P] =
      implicitly[Obfuser[P]]

    def of[P](f: (P, Seed) => Array[Byte]): Obfuser[P] =
      (p, s) => f(p, s)

    def default[P](f: P => Array[Byte]): Obfuser[P] =
      Obfuser.of((plain, seed) => shuffleAndXorBytes(seed, f(plain)))

    def shuffleAndXorBytes(seed: Seed, bytes: Array[Byte]): Array[Byte] = {
      val random: Random = new Random(seed)
      val key: Byte      = random.nextLong().toByte
      random.shuffle(bytes.toSeq).map(b => (b ^ key).toByte).toArray
    }
  }

  trait DeObfuser[P] extends ((Array[Byte], Seed) => P)
  object DeObfuser {
    def apply[P: DeObfuser]: DeObfuser[P] =
      implicitly[DeObfuser[P]]

    def of[P](f: (Array[Byte], Seed) => P): DeObfuser[P] =
      (b, s) => f(b, s)

    def default[P](f: Array[Byte] => P): DeObfuser[P] =
      DeObfuser.of((bytes, seed) => f(unshuffleAndXorBytes(seed, bytes)))

    def unshuffleAndXorBytes(seed: Seed, shuffled: Array[Byte]): Array[Byte] = {
      val random: Random                = new Random(seed)
      val key: Byte                     = random.nextLong().toByte
      val shuffled_perm: Seq[Int]       = random.shuffle(1 to shuffled.length)
      val zipped_ls: Array[(Byte, Int)] = shuffled.zip(shuffled_perm)

      zipped_ls.sortBy(_._2).map(t => (t._1 ^ key).toByte)
    }
  }

  case class ObfuserTuple[P](obfuser: Obfuser[P], deObfuser: DeObfuser[P]) {
    def bimap[U](fO: U => P, fD: P => U): ObfuserTuple[U] =
      ObfuserTuple[U](
        obfuser   = Obfuser.of((plain, seed) => obfuser(fO(plain), seed)),
        deObfuser = DeObfuser.of((bytes, seed) => fD(deObfuser(bytes, seed)))
      )
  }
  object ObfuserTuple {
    def allocateByteBuffer[P](capacity: Int)(
      bObfuser: ByteBuffer => P => ByteBuffer,
      bDeObfuser: ByteBuffer => P
    ): ObfuserTuple[P] =
      ObfuserTuple(
        obfuser   = Obfuser.default(p => bObfuser(ByteBuffer.allocate(capacity)).apply(p).array()),
        deObfuser = DeObfuser.default(b => bDeObfuser(ByteBuffer.wrap(b)))
      )
  }
}
sealed trait Instances {

  implicit val stringBiObfuser: ObfuserTuple[String] =
    ObfuserTuple(
      Obfuser.default(_.getBytes(StandardCharsets.UTF_8)),
      DeObfuser.default(off => new String(off, StandardCharsets.UTF_8))
    )

  implicit val byteBiObfuser: ObfuserTuple[Byte] =
    ObfuserTuple(Obfuser.default(i => Array(i)), DeObfuser.default(_.head))

  implicit val charBiObfuser: ObfuserTuple[Char] =
    ObfuserTuple.allocateByteBuffer(2)(_.putChar, _.getChar)

  implicit val intBiObfuser: ObfuserTuple[Int] =
    ObfuserTuple.allocateByteBuffer(4)(_.putInt, _.getInt)

  implicit val shortBiObfuser: ObfuserTuple[Short] =
    ObfuserTuple.allocateByteBuffer(2)(_.putShort, _.getShort)

  implicit val floatBiObfuser: ObfuserTuple[Float] =
    ObfuserTuple.allocateByteBuffer(4)(_.putFloat, _.getFloat)

  implicit val doubleBiObfuser: ObfuserTuple[Double] =
    ObfuserTuple.allocateByteBuffer(8)(_.putDouble, _.getDouble)

  implicit val boolBiObfuser: ObfuserTuple[Boolean] =
    ObfuserTuple(
      Obfuser.default(i => if (i) Array(1) else Array(0)),
      DeObfuser.default(_.head match {
        case 1 => true
        case 0 => false
      })
    )

  implicit val bigIntBiObfuser: ObfuserTuple[BigInt] =
    ObfuserTuple(Obfuser.default(_.toByteArray), DeObfuser.default(BigInt(_)))

  implicit val bigDecimalBiObfuser: ObfuserTuple[BigDecimal] =
    stringBiObfuser.bimap(_.toString, str => BigDecimal(str))

  implicit def unzipBiObfuserToObfuser[P: ObfuserTuple]: Obfuser[P] =
    implicitly[ObfuserTuple[P]].obfuser

  implicit def unzipBiObfuserTodeObfuser[P: ObfuserTuple]: DeObfuser[P] =
    implicitly[ObfuserTuple[P]].deObfuser

  implicit def hashing[T]: Hashing[Secret[T]] =
    Hashing.fromFunction(_.hashCode())

  implicit def eq[T]: Eq[Secret[T]] =
    (_, _) => false

  implicit def show[T]: Show[Secret[T]] =
    _ => Secret.placeHolder
}
