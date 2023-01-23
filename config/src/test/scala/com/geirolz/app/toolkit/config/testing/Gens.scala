package com.geirolz.app.toolkit.config.testing

import org.scalacheck.Gen

object Gens {

  def strGen(size: Int): Gen[String] = Gen.listOfN(size, Gen.alphaChar).map(_.mkString)
}
