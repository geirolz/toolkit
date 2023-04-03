package com.geirolz.example.app

sealed trait AppError
object AppError {
  case class UnknownError(message: String) extends AppError
}
