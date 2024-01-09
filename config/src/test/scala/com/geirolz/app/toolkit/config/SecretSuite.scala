package com.geirolz.app.toolkit.config

import com.geirolz.app.toolkit.config.Secret.{DeObfuser, Obfuser, ObfuserTuple}
import org.scalacheck.Arbitrary
import org.scalacheck.Prop.forAll

import scala.reflect.ClassTag

class SecretSuite extends munit.ScalaCheckSuite {

  test("shuffleAndXorBytes works properly returning the same value for the same seed and value") {
    val seed: Long    = 1111
    val value: String = "12345678"
    assertEquals(
      obtained = new String(Obfuser.shuffleAndXorBytes(seed, value.getBytes)),
      expected = new String(Obfuser.shuffleAndXorBytes(seed, value.getBytes))
    )
  }

  test("shuffleAndXorBytes works properly obfuscating and de-obfuscating a String value") {
    val seed: Long                = 1111
    val value: String             = "12345678"
    val obfuscated: Array[Byte]   = Obfuser.shuffleAndXorBytes(seed, value.getBytes)
    val deObfuscated: Array[Byte] = DeObfuser.unshuffleAndXorBytes(seed, obfuscated)

    assertEquals(
      obtained = new String(deObfuscated),
      expected = value
    )
  }

  testBiObfuser[String]
  testBiObfuser[Int]
  testBiObfuser[Short]
  testBiObfuser[Char]
  testBiObfuser[Byte]
  testBiObfuser[Float]
  testBiObfuser[Double]
  testBiObfuser[Boolean]
  testBiObfuser[BigInt]
  testBiObfuser[BigDecimal]

  private def testBiObfuser[T: Arbitrary: ObfuserTuple](implicit c: ClassTag[T]): Unit = {

    property(s"BiObfuser equals for type ${c.runtimeClass.getSimpleName} always return false") {
      forAll { (value: T) =>
        assert(Secret(value) != Secret(value))
      }
    }

    property(s"BiObfuser obfuscate and deobfuscate type ${c.runtimeClass.getSimpleName} properly") {
      forAll { (value: T) =>
        assertEquals(
          obtained = Secret(value).use,
          expected = Right(value)
        )
      }
    }
  }
}
