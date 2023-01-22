package com.geirolz.app.toolkit.config

import com.geirolz.app.toolkit.config.Secret.BiOffuser
import org.scalacheck.Arbitrary
import org.scalacheck.Prop.forAll

import scala.reflect.ClassTag

class SecretSuite extends munit.ScalaCheckSuite {

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
