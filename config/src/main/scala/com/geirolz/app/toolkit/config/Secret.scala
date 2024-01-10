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
  *   val secretString: Secret[String]                      = Secret("my_password")
  *   val secretValue: Either[NoLongerValidSecret, String]  = secretString.use
  * }}}
  */
final class Secret[T](private var obfuscatedValue: Array[Byte], seed: Seed) {

  private var destroyed: Boolean = false

  /** Unsafe version of `Secret#use` method. Avoid it if possible.
    *
    * This method should be used only when the secret is still valid, otherwise it will throw a `NoLongerValidSecret` exception.
    *
    * @throws SecretNoLongerValid
    *   if the secret is no longer valid
    * @param deObfuser
    *   the implicit de-obfuser to use to de-obfuscate the value
    * @return
    *   the original value of type `T` de-obfuscated using the implicit `DeObfuser` instance
    */
  def unsafeUse(implicit deObfuser: DeObfuser[T]): T =
    useE.fold(throw _, identity)

  /** Access the original value of type `T` de-obfuscated using the implicit `DeObfuser` instance.
    *
    * This method is safe because it returns the original checking if the secret is still valid. If the secret is no longer valid it will raise a
    * `NoLongerValidSecret` exception through monadic `F`.
    *
    * @param deObfuser
    *   the implicit de-obfuser to use to de-obfuscate the value
    * @param F
    *   MonadThrow typeclass instance
    * @tparam F
    *   Effect type
    * @return
    *   the original value of type `T` de-obfuscated using the implicit `DeObfuser` instance wrapped in `F`
    */
  def use[F[_]: MonadThrow](implicit deObfuser: DeObfuser[T]): F[T] =
    MonadThrow[F].fromEither(useE)

  /** Access the original value of type `T` de-obfuscated using the implicit `DeObfuser` instance.
    *
    * This method is safe because it returns the original checking if the secret is still valid. If the secret is no longer valid it will raise a
    * `NoLongerValidSecret` exception through monadic `F`.
    *
    * Once the secret is used it will be destroyed invoking `destroy` method.
    *
    * @param deObfuser
    *   the implicit de-obfuser to use to de-obfuscate the value
    * @param F
    *   MonadThrow typeclass instance
    * @tparam F
    *   Effect type
    * @return
    *   the original value of type `T` de-obfuscated using the implicit `DeObfuser` instance wrapped in `F`
    */
  def useAndDestroy[F[_]: MonadThrow](implicit deObfuser: DeObfuser[T]): F[T] =
    MonadThrow[F].fromEither(useAndDestroyE)

  /** Access the original value of type `T` de-obfuscated using the implicit `DeObfuser` instance.
    *
    * This method is safe because it returns the original checking if the secret is still valid. If the secret is no longer valid it will raise a
    * `NoLongerValidSecret` exception through monadic `F`.
    *
    * @param deObfuser
    *   the implicit de-obfuser to use to de-obfuscate the value
    * @return
    *   `Left` if the secret is destroyed, `Right` with T value de-obfuscate otherwise
    */
  def useE(implicit deObfuser: DeObfuser[T]): Either[SecretNoLongerValid, T] =
    if (isDestroyed)
      Left(SecretNoLongerValid())
    else
      Right(deObfuser(obfuscatedValue, seed))

  /** This method is safe because it returns the original checking if the secret is still valid. If the secret is no longer valid it will return a
    * `NoLongerValidSecret` as `Left` side of the `Either`.
    *
    * Once the secret is used it will be destroyed invoking `destroy` method.
    *
    * @param deObfuser
    *   the implicit de-obfuser to use to de-obfuscate the value
    * @return
    *   `Left` if the secret is destroyed, `Right` with T value de-obfuscate otherwise
    */
  def useAndDestroyE(implicit deObfuser: DeObfuser[T]): Either[SecretNoLongerValid, T] =
    useE.map { value => destroy(); value }

  /** Destroy the secret value by filling the obfuscated value with 0.
    *
    * This method is idempotent.
    *
    * @note
    *   Once the secret is destroyed it can't be used anymore. If you try to use it using `use`, `useAndDestroy` and other methods, it will raise a
    *   `NoLongerValidSecret` exception.
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
