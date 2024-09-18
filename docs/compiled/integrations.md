# Toolkit integrations

This document provides an overview of the integrations between Toolkit and other popular libraries.

These integrations aim to enhance the functionality and capabilities of your applications by leveraging the features and
strengths of both Toolkit and the respective libraries.

Below is a list of some notable integrations:

- [Pureconfig](#pureconfig)
- [Log4cats](#log4cats)
- [Odin](#odin)
- [Fly4s](#fly4s)

We are always open to expanding the list of integrations to further empower Toolkit users.
If you have suggestions for new integrations or would like to contribute to an existing integration,
we welcome your input!

Please feel free to [open an issue](https://github.com/your-username/toolkit/issues) with your suggestions or submit a
pull request.

Let's collaborate and create even more powerful integrations with Toolkit!

---

## [Pureconfig](https://github.com/pureconfig/pureconfig)

The integration module with PureConfig provides a convenient loader for loading configurations using the PureConfig
library within your Toolkit-based applications.

PureConfig is a powerful configuration management library that supports various configuration formats like HOCON, JSON,
and YAML.

Include this module in your project by adding the following dependency:

```sbt
libraryDependencies += "com.github.geirolz" %% "toolkit-pureconfig" % "0.1.0-RC3"
```

Import the integration module in your application:

```scala
import com.geirolz.app.toolkit.config.pureconfig.*
```

Which allows you to use `withConfigLoader` with `pureconfigLoader[F, CONF]` to load the config from
a `ConfigSource.default` or other sources

```scala
import cats.Show
import cats.effect.IO
import com.geirolz.app.toolkit.{App, SimpleAppInfo}
import com.geirolz.app.toolkit.config.pureconfig.*
import java.time.LocalDateTime

case class TestConfig(value: String)

object TestConfig:
  given Show[TestConfig] = Show.fromToString
  given pureconfig.ConfigReader[TestConfig] =
    pureconfig.ConfigReader.forProduct1("value")(TestConfig.apply)


App[IO]
  .withInfo(
    SimpleAppInfo.string(
      name = "toolkit",
      version = "0.0.1",
      scalaVersion = "2.13.10",
      sbtVersion = "1.8.0",
      builtOn = LocalDateTime.now()
    )
  )
  .withConfig(pureconfigLoader[IO, TestConfig])
  .withoutDependencies
  .provideOne(IO.unit)
  .run()
  .void
```

## [Log4cats](https://github.com/typelevel/log4cats)

⚠️ _This module is mandatory if you want to use `log4cats`._

The integration module with Log4cats provides seamless integration between Toolkit and the Log4cats library,
allowing you to easily create and configure loggers within your Toolkit-based applications.

Log4cats is a powerful logging library that offers flexible logging capabilities and supports various backend
implementations.

With the integration module, you can leverage the rich features of Log4cats to enhance your application's logging
functionality.
Whether you need simple console logging or sophisticated logging to remote services, Log4cats offers a range of
configuration options to suit your needs.

Include this module in your project by adding the following dependency:

```sbt
libraryDependencies += "com.github.geirolz" %% "toolkit-log4cats" % "0.1.0-RC3"
```

And just use the `withLogger` method to configure the logger.

## [Odin](https://github.com/valskalla/odin)

⚠️ _This module is mandatory if you want to use `odin`._

The integration module with Odin provides seamless integration between Toolkit and the Odin logging library,
allowing you to create and configure powerful loggers within your Toolkit-based applications.
Odin is a versatile logging library that offers expressive logging features.

With the integration module, you can leverage Odin's advanced logging capabilities to enhance your application's
logging functionality.

Whether you need to log messages to the console, files, or other logging destinations,
Odin provides flexible configuration options to meet your logging requirements.

Include this module in your project by adding the following dependency:

```sbt
libraryDependencies += "com.github.geirolz" %% "toolkit-odin" % "0.1.0-RC3"
```

And just use the `withLogger` method to configure the logger.

## [Fly4s](https://github.com/geirolz/fly4s)

The integration module with Flyway provides seamless integration between Toolkit and the Fly4s/Flyway database migration
tool.
Fly4s simplifies and automates the process of running database migrations, ensuring that your database schema
stays up-to-date with your application's evolution.

With the integration module, you can easily incorporate Fly4s/Flyway into your Toolkit-based applications to manage
database migrations.

The module offers convenient methods to run database migrations before executing the provided services,
ensuring that your application operates with the correct database schema.

Import this module in your project by adding the following dependency:

```sbt
libraryDependencies += "com.github.geirolz" %% "toolkit-fly4s" % "0.1.0-RC3"
```

Import the tasks

```scala
import com.geirolz.app.toolkit.fly4s.*
```

Which allows you to use `beforeProvidingMigrateDatabaseWithConfig` on `App` to migrate the database before running the
app.
To have access to the whole app dependencies you can use `beforeProvidingMigrateDatabaseWith` instead while to have
access to
the whole app dependencies to provide a custom `Fly4s` instance you can use `beforeProvidingMigrateDatabase`.

```scala
import cats.Show
import cats.effect.IO
import com.geirolz.app.toolkit.fly4s.*
import com.geirolz.app.toolkit.*
import java.time.LocalDateTime

case class TestConfig(dbUrl: String, dbUser: Option[String], dbPassword: Option[Array[Char]])

object TestConfig:
  given Show[TestConfig] = Show.fromToString

App[IO]
  .withInfo(
    SimpleAppInfo.string(
      name = "toolkit",
      version = "0.0.1",
      scalaVersion = "2.13.10",
      sbtVersion = "1.8.0",
      builtOn = LocalDateTime.now()
    )
  )
  .withConfigPure(
    TestConfig(
      dbUrl = "jdbc:postgresql://localhost:5432/toolkit",
      dbUser = Some("postgres"),
      dbPassword = Some("postgres".toCharArray)
    )
  )
  .withoutDependencies
  .beforeProviding(
    migrateDatabaseWith(
      url = ctx.config.dbUrl,
      user = ctx.config.dbUser,
      password = ctx.config.dbPassword
    )
  )
  .provideOne(IO.unit)
  .run()
  .void
```