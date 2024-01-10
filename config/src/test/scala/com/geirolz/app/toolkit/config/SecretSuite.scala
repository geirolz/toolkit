package com.geirolz.app.toolkit.config

import com.geirolz.app.toolkit.config.Secret.{DeObfuser, Obfuser, ObfuserTuple, SecretNoLongerValid}
import org.scalacheck.Arbitrary
import org.scalacheck.Prop.forAll

import scala.reflect.ClassTag
import scala.util.{Failure, Try}

class SecretSuite extends munit.ScalaCheckSuite {

  testObfuserTupleFor[String]
  testObfuserTupleFor[Int]
  testObfuserTupleFor[Short]
  testObfuserTupleFor[Char]
  testObfuserTupleFor[Byte]
  testObfuserTupleFor[Float]
  testObfuserTupleFor[Double]
  testObfuserTupleFor[Boolean]
  testObfuserTupleFor[BigInt]
  testObfuserTupleFor[BigDecimal]

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

  private def testObfuserTupleFor[T: Arbitrary: ObfuserTuple](implicit c: ClassTag[T]): Unit = {

    property(s"Secret equals for type ${c.runtimeClass.getSimpleName} always return false") {
      forAll { (value: T) =>
        assert(Secret(value) != Secret(value))
      }
    }

    // use
    property(s"Secret obfuscate and de-obfuscate type ${c.runtimeClass.getSimpleName} properly - use") {
      forAll { (value: T) =>
        assert(
          Secret(value)
            .use[Try, Unit](result => {
              assertEquals(
                obtained = result,
                expected = value
              )
            })
            .isSuccess
        )
      }
    }

    // useAndDestroy
    property(s"Secret obfuscate and de-obfuscate type ${c.runtimeClass.getSimpleName} properly - useAndDestroy") {
      forAll { (value: T) =>
        val secret: Secret[T] = Secret(value)

        assert(
          secret
            .useAndDestroy[Try, Unit] { result =>
              assertEquals(
                obtained = result,
                expected = value
              )
            }
            .isSuccess
        )

        assertEquals(
          obtained = secret.useAndDestroy[Try, Int](_.hashCode()),
          expected = Failure(SecretNoLongerValid())
        )
        assertEquals(
          obtained = secret.isDestroyed,
          expected = true
        )
      }
    }
  }
}
