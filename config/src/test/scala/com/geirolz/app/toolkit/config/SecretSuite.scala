package com.geirolz.app.toolkit.config

import com.geirolz.app.toolkit.config.Secret.BiOffuser
import com.geirolz.app.toolkit.config.testing.{Gens, Timed}
import org.scalacheck.Arbitrary
import org.scalacheck.Prop.forAll

import scala.reflect.ClassTag

class SecretSuite extends munit.ScalaCheckSuite {

  property(s"Secret is fast enough") {
    forAll(Gens.strGen(10000)) { s =>
      assert(
        Timed(Secret.veiled(s).use)._1.toMillis < 10
      )
    }
  }

  testBiOffuser[String]
  testBiOffuser[Int]
  testBiOffuser[Short]
  testBiOffuser[Char]
  testBiOffuser[Byte]
  testBiOffuser[Float]
  testBiOffuser[Double]
  testBiOffuser[Boolean]
  testBiOffuser[BigInt]
  testBiOffuser[BigDecimal]

  private def testBiOffuser[T: Arbitrary: BiOffuser](implicit
    c: ClassTag[T]
  ): Unit = {

    property(s"BiOffuser equals for type ${c.runtimeClass.getSimpleName} always return false") {
      forAll { (value: T) =>
        assert(Secret(value) != Secret(value))
      }
    }

    property(s"BiOffuser offuscate and deoffuscate type ${c.runtimeClass.getSimpleName} properly") {
      forAll { (value: T) =>
        assertEquals(
          obtained = Secret(value).use,
          expected = value
        )
      }
    }
  }
}
