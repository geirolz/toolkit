package com.geirolz.app.toolkit.fly4s

import fly4s.core.data.MigrateResult

case class Fly4sAppMessages(
  applyingMigrations: String                   = "Applying migrations to the database...",
  failedToApplyMigrations: String              = s"Unable to apply database migrations to database.",
  successfullyApplied: MigrateResult => String = res => s"Applied ${res.migrationsExecuted} migrations to database."
)
object Fly4sAppMessages:
  given Fly4sAppMessages = Fly4sAppMessages()
