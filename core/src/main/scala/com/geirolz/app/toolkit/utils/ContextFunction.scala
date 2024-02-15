package com.geirolz.app.toolkit.utils

extension [A, B](f: A => B)
  def asContextFunction: A ?=> B =
    f(summon[A])
