package com.geirolz.app.toolkit.config

import com.geirolz.app.toolkit.config.Secret.{ObfuserTuple, SecretNoLongerValid}
import org.scalacheck.Arbitrary
import org.scalacheck.Prop.forAll

import scala.reflect.ClassTag
import scala.util.{Failure, Try}

class SecretSuite extends munit.ScalaCheckSuite {

  testObfuserTupleFor[String]
  testObfuserTupleFor[Int]
  testObfuserTupleFor[Long]
  testObfuserTupleFor[Short]
  testObfuserTupleFor[Char]
  testObfuserTupleFor[Byte]
  testObfuserTupleFor[Float]
  testObfuserTupleFor[Double]
  testObfuserTupleFor[Boolean]
  testObfuserTupleFor[BigInt]
  testObfuserTupleFor[BigDecimal]

  test("Simple Secret String") {
    Secret("TEST").useAndDestroyE(_ => ())
  }

  test("Simple Secret with long String") {
    Secret(
      """|C#iur0#UsxTWzUZ5QPn%KGo$922SMvc5zYLqrcdE6SU6ZpFQrk3&W
        |1c48obb&Rngv9twgMHTuXG@hRb@FZg@u!uPoG%dxTab0QtTab0Qta
        |c5zYU6ZpRngv9twgMHTuXGFdxTab0QtTab0QtaKGo$922SMvc5zYL
        |KGo$922SMvc5zYLqrcdEKGo$922SMvc5zYLqrcdE6SU6ZpFQrk36S
        |U6ZpFQrk31hRbc48obb1c48obb&Rngv9twgMHTuXG@hRb@FZg@u!u
        |PoG%dxTab0QtTab0QtaKGo$922SMvc5zYLqrcdEKGo$922SMvc5zY
        |LqrcdE6SdxTab0QtTab0QtaKGo$922SMvc5zYLqrcdEKGo$922SMv
        |c5zYU6ZpRngv9twgMHTuXGFdxTab0QtTab0QtaKGo$922SMvc5zYL
        |1c48obb&Rngv9twgMHTuXG@hRb@FZg@u!uPoG%dxTab0QtTab0Qta
        |c5zYU6ZpRngv9twgMHTuXGFdxTab0QtTab0QtaKGo$922SMvc5zYL
        |KGo$922SMvc5zYLqrcdEKGo$922SMvc5zYLqrcdE6SU6ZpFQrk36S
        |U6ZpFQrk31hRbc48obb1c48obb&Rngv9twgMHTuXG@hRb@FZg@u!u
        |PoG%dxTab0QtTab0QtaKGo$922SMvc5zYLqrcdEKGo$922SMvc5zY
        |LqrcdE6SdxTab0QtTab0QtaKGo$922SMvc5zYLqrcdEKGo$922SMv
        |c5zYU6ZpRngv9twgMHTuXGFdxTab0QtTab0QtaKGo$922SMvc5zYL
        |qrcdEKGo$922SMvc5zYU6ZpFQrk31hRbc48obb1c48obbQrqgk36S
        |PoG%dxTab0QtTab0QtaKGo$922SMvc5zYLqrcdEKGo$922SMvc5zY
        |LqrcdE6SdxTab0QtTab0QtaKGo$922SMvc5zYLqrcdEKGo$922SMv
        |c5zYU6ZpRngv9twgMHTuXGFdxTab0QtTab0QtaKGo$922SMvc5zYL
        |1c48obb&Rngv9twgMHTuXG@hRb@FZg@u!uPoG%dxTab0QtTab0Qta
        |c5zYU6ZpRngv9twgMHTuXGFdxTab0QtTab0QtaKGo$922SMvc5zYL
        |KGo$922SMvc5zYLqrcdEKGo$922SMvc5zYLqrcdE6SU6ZpFQrk36S
        |U6ZpFQrk31hRbc48obb1c48obb&Rngv9twgMHTuXG@hRb@FZg@u!u
        |PoG%dxTab0QtTab0QtaKGo$922SMvc5zYLqrcdEKGo$922SMvc5zY
        |LqrcdE6SdxTab0QtTab0QtaKGo$922SMvc5zYLqrcdEKGo$922SMv
        |c5zYU6ZpRngv9twgMHTuXGFdxTab0QtTab0QtaKGo$922SMvc5zYL
        |qrcdEKGo$922SMvc5zYU6ZpFQrk31hRbc48obb1c48obbQrqgk36S
        |qrcdEKGo$922SMvc5zYU6ZpFQrk31hRbc48obb1c48obbQrqgk36S
        |""".stripMargin
    ).useAndDestroyE(_ => ())
  }

  private def testObfuserTupleFor[T: Arbitrary: ObfuserTuple](implicit c: ClassTag[T]): Unit = {

    val typeName = c.runtimeClass.getSimpleName.capitalize

    property(s"Secret[$typeName] succesfully obfuscate") {
      forAll { (value: T) =>
        Secret(value)
        assert(cond = true)
      }
    }

    property(s"Secret[$typeName] equals works as expected") {
      forAll { (value: T) =>
        assertEquals(Secret(value), Secret(value))
      }
    }

    property(s"Secret[$typeName] hashCode is different from the value one") {
      forAll { (value: T) =>
        assert(Secret(value).hashCode() != value.hashCode())
      }
    }

    // use
    property(s"Secret[$typeName] obfuscate and de-obfuscate properly - use") {
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
    property(s"Secret[$typeName] obfuscate and de-obfuscate properly - useAndDestroy") {
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
