package com.geirolz.app.toolkit.config

import cats.{Eq, MonadThrow, Show}
import com.geirolz.app.toolkit.config.Secret.{DeObfuser, Obfuser, ObfuserTuple, SecretNoLongerValid, Seed}

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util
import scala.util.Random
import scala.util.control.NoStackTrace
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
  *   val secretString: Secret[String]  = Secret("my_password")
  *   val database: F[Database]         = secretString.use(password => initDb(password))
  * }}}
  */
final class Secret[T](private var obfuscatedValue: Array[Byte], seed: Seed) {

  import cats.syntax.all.*

  private var destroyed: Boolean = false

  /** Avoid this method if possible. Unsafely apply `f` with the de-obfuscated value WITHOUT destroying it.
    *
    * If the secret is destroyed it will raise a `NoLongerValidSecret` exception.
    *
    * @throws NoLongerValidSecret
    *   if the secret is destroyed
    */
  def unsafeUse[U](f: T => U)(implicit deObfuser: DeObfuser[T]): U =
    use[Either[Throwable, *], U](f).fold(throw _, identity)

  /** Apply `f` with the de-obfuscated value WITHOUT destroying it.
    *
    * If the secret is destroyed it will raise a `NoLongerValidSecret` exception.
    *
    * Once the secret is destroyed it can't be used anymore. If you try to use it using `use`, `useAndDestroy`, `evalUse`, `evalUseAndDestroy` and
    * other methods, it will raise a `NoLongerValidSecret` exception.
    */
  def use[F[_]: MonadThrow, U](f: T => U)(implicit deObfuser: DeObfuser[T]): F[U] =
    evalUse[F, U](f.andThen(_.pure[F]))

  /** Alias for `use` with `Either[Throwable, *]`
    */
  def useE[U](f: T => U)(implicit deObfuser: DeObfuser[T]): Either[Throwable, U] =
    use[Either[Throwable, *], U](f)

  /** Alias for `useAndDestroy` with `Either[Throwable, *]`
    */
  def useAndDestroyE[U](f: T => U)(implicit deObfuser: DeObfuser[T]): Either[Throwable, U] =
    useAndDestroy[Either[Throwable, *], U](f)

  /** Apply `f` with the de-obfuscated value and then destroy the secret value by invoking `destroy` method.
    *
    * Once the secret is destroyed it can't be used anymore. If you try to use it using `use`, `useAndDestroy`, `evalUse`, `evalUseAndDestroy` and
    * other methods, it will raise a `NoLongerValidSecret` exception.
    */
  def useAndDestroy[F[_]: MonadThrow, U](f: T => U)(implicit deObfuser: DeObfuser[T]): F[U] =
    evalUseAndDestroy[F, U](f.andThen(_.pure[F]))

  /** Apply `f` with the de-obfuscated value WITHOUT destroying it.
    *
    * If the secret is destroyed it will raise a `NoLongerValidSecret` exception.
    *
    * Once the secret is destroyed it can't be used anymore. If you try to use it using `use`, `useAndDestroy`, `evalUse`, `evalUseAndDestroy` and
    * other methods, it will raise a `NoLongerValidSecret` exception.
    */
  def evalUse[F[_]: MonadThrow, U](f: T => F[U])(implicit deObfuser: DeObfuser[T]): F[U] =
    if (destroyed)
      MonadThrow[F].raiseError(SecretNoLongerValid())
    else
      f(deObfuser(obfuscatedValue, seed))

  /** Apply `f` with the de-obfuscated value and then destroy the secret value by invoking `destroy` method.
    *
    * Once the secret is destroyed it can't be used anymore. If you try to use it using `use`, `useAndDestroy`, `evalUse`, `evalUseAndDestroy` and
    * other methods, it will raise a `NoLongerValidSecret` exception.
    */
  def evalUseAndDestroy[F[_]: MonadThrow, U](f: T => F[U])(implicit deObfuser: DeObfuser[T]): F[U] =
    evalUse(f).map { u => destroy(); u }

  /** Destroy the secret value by filling the obfuscated value with 0.
    *
    * This method is idempotent.
    *
    * Once the secret is destroyed it can't be used anymore. If you try to use it using `use`, `useAndDestroy`, `evalUse`, `evalUseAndDestroy` and
    * other methods, it will raise a `NoLongerValidSecret` exception.
    */
  def destroy(): Unit =
    if (!destroyed) {
      util.Arrays.fill(obfuscatedValue, 0.toByte)
      obfuscatedValue = null
      destroyed       = true
    }

  /** Check if the secret is destroyed
    * @return
    *   `true` if the secret is destroyed, `false` otherwise
    */
  def isDestroyed: Boolean = destroyed

  /** @return
    *   always returns `false` to avoid leaking information
    */
  override def equals(obj: Any): Boolean = false

  /** @return
    *   always returns a static place holder string "** SECRET **" to avoid leaking information
    */
  override def toString: String = Secret.placeHolder

  /** @return
    *   always returns `-1` to avoid leaking information
    */
  override def hashCode(): Int = -1
}

object Secret extends Instances {

  val placeHolder = "** MASKED **"

  case class SecretNoLongerValid() extends RuntimeException("This secret value is no longer valid") with NoStackTrace

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

  implicit val stringObfuserTuple: ObfuserTuple[String] =
    ObfuserTuple(
      Obfuser.default(_.getBytes(StandardCharsets.UTF_8)),
      DeObfuser.default(off => new String(off, StandardCharsets.UTF_8))
    )

  implicit val byteObfuserTuple: ObfuserTuple[Byte] =
    ObfuserTuple(Obfuser.default(i => Array(i)), DeObfuser.default(_.head))

  implicit val charObfuserTuple: ObfuserTuple[Char] =
    ObfuserTuple.allocateByteBuffer(2)(_.putChar, _.getChar)

  implicit val intObfuserTuple: ObfuserTuple[Int] =
    ObfuserTuple.allocateByteBuffer(4)(_.putInt, _.getInt)

  implicit val shortObfuserTuple: ObfuserTuple[Short] =
    ObfuserTuple.allocateByteBuffer(2)(_.putShort, _.getShort)

  implicit val floatObfuserTuple: ObfuserTuple[Float] =
    ObfuserTuple.allocateByteBuffer(4)(_.putFloat, _.getFloat)

  implicit val doubleObfuserTuple: ObfuserTuple[Double] =
    ObfuserTuple.allocateByteBuffer(8)(_.putDouble, _.getDouble)

  implicit val boolObfuserTuple: ObfuserTuple[Boolean] =
    ObfuserTuple(
      Obfuser.default(i => if (i) Array(1) else Array(0)),
      DeObfuser.default(_.head match {
        case 1 => true
        case 0 => false
      })
    )

  implicit val bigIntObfuserTuple: ObfuserTuple[BigInt] =
    ObfuserTuple(Obfuser.default(_.toByteArray), DeObfuser.default(BigInt(_)))

  implicit val bigDecimalObfuserTuple: ObfuserTuple[BigDecimal] =
    stringObfuserTuple.bimap(_.toString, str => BigDecimal(str))

  implicit def unzipObfuserTupleToObfuser[P: ObfuserTuple]: Obfuser[P] =
    implicitly[ObfuserTuple[P]].obfuser

  implicit def unzipObfuserTupleTodeObfuser[P: ObfuserTuple]: DeObfuser[P] =
    implicitly[ObfuserTuple[P]].deObfuser

  implicit def hashing[T]: Hashing[Secret[T]] =
    Hashing.fromFunction(_.hashCode())

  implicit def eq[T]: Eq[Secret[T]] =
    (_, _) => false

  implicit def show[T]: Show[Secret[T]] =
    _ => Secret.placeHolder
}
