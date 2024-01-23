# Toolkit

[![Build Status](https://github.com/geirolz/toolkit/actions/workflows/cicd.yml/badge.svg)](https://github.com/geirolz/toolkit/actions)
[![codecov](https://img.shields.io/codecov/c/github/geirolz/toolkit)](https://codecov.io/gh/geirolz/toolkit)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/db3274b55e0c4031803afb45f58d4413)](https://www.codacy.com/manual/david.geirola/toolkit?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=geirolz/toolkit&amp;utm_campaign=Badge_Grade)
[![Sonatype Nexus (Releases)](https://img.shields.io/nexus/r/com.github.geirolz/toolkit_2.13?server=https%3A%2F%2Foss.sonatype.org)](https://mvnrepository.com/artifact/com.github.geirolz/toolkit)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
[![Mergify Status](https://img.shields.io/endpoint.svg?url=https://api.mergify.com/v1/badges/geirolz/toolkit&style=flat)](https://mergify.io)
[![GitHub license](https://img.shields.io/github/license/geirolz/toolkit)](https://github.com/geirolz/toolkit/blob/main/LICENSE)

<div align="center">
 <img src="images/logo.png" alt="logo" width="50%"/>
</div>

Toolkit is a lightweight and non-intrusive open-source library designed to simplify the development of typed and
declarative applications in Scala.

It offers a functional approach to building applications by managing resources and
dependencies, allowing developers to focus on the core aspects of their application logic.

Read this article about this library:
[Semantic of a functional app in Scala](https://www.reddit.com/r/scala/comments/14g3uxo/semantic_of_a_functional_app_in_scala/)

Please, drop a ⭐️ if you are interested in this project and you want to support it.

- [Features](#features)
- [Notes](#notes)
- [Getting Started](#getting-started)
- [Integrations](#integrations)
- [Adopters](#adopters)
- [Contributing](#contributing)
- [License](#license)
- [Acknowledgements](#acknowledgements)
- [Contact](#contact)

## Features

- **Resource Management:** Toolkit simplifies the management of application resources, such as configuration
  settings, logging, and custom resources. By abstracting away the resource handling, it reduces boilerplate code and
  provides a clean and concise syntax for managing resources.

- **Dependency Injection:** The library provides a straightforward way to handle application dependencies. It allows you
  to define and inject application-specific services and repositories, encouraging modular and testable code.

- **Declarative Syntax:** Toolkit promotes a declarative coding style, where you can describe the structure and
  behavior of your application in a clear and concise manner. This leads to code that is easier to understand, reason
  about, and maintain.

## Notes

- All dependencies and resources are released at the end of the app execution as defined as `Resource[F, *]`.
- If you need to release a resource before the end of the app execution you should use `Resource.use` or equivalent to
  build what you need as dependency.
- If you need to run an infinite task using `provide*` you should use `F.never` or equivalent to keep the task running.

## Getting Started

To get started with Toolkit, follow these steps:

1. **Installation:** Include the library as a dependency in your Scala project. You can find the latest version and
   installation instructions in the [Toolkit GitHub repository](https://github.com/geirolz/toolkit).

```sbt
libraryDependencies += "com.github.geirolz" %% "toolkit" % "@VERSION@"
```

2. **Define Your Application:** Create a new Scala objects or classes that represents your application dependencies and
   resources.

```scala mdoc:silent
import cats.Show
import cats.effect.{Resource, IO}
import com.geirolz.app.toolkit.{App, SimpleAppInfo}
import com.geirolz.app.toolkit.logger.ToolkitLogger
import com.geirolz.app.toolkit.novalues.NoResources

// Define config
case class Config(host: String, port: Int)

object Config {
  implicit val show: Show[Config] = Show.fromToString
}

// Define service dependencies
case class AppDependencyServices(kafkaConsumer: KafkaConsumer[IO])

object AppDependencyServices {
  def resource(res: AppResources[SimpleAppInfo[String], ToolkitLogger[IO], Config, NoResources]): Resource[IO, AppDependencyServices] =
    Resource.pure(AppDependencyServices(KafkaConsumer.fake))
}

// A stubbed kafka consumer
trait KafkaConsumer[F[_]] {
  def consumeFrom(name: String): fs2.Stream[F, KafkaConsumer.KafkaRecord]
}

object KafkaConsumer {

  import scala.concurrent.duration.DurationInt

  case class KafkaRecord(value: String)

  def fake: KafkaConsumer[IO] =
    (name: String) =>
      fs2.Stream
        .eval(IO.randomUUID.map(t => KafkaRecord(t.toString)).flatTap(_ => IO.sleep(5.seconds)))
        .repeat
}
```

3. **Build Your Application:** Build your application using the Toolkit DSL and execute it. Toolkit
   takes care of managing resources, handling dependencies, and orchestrating the execution of your application logic.

```scala mdoc:silent
import cats.effect.{ExitCode, IO, IOApp}
import com.geirolz.app.toolkit.{App, SimpleAppInfo}
import com.geirolz.app.toolkit.logger.ToolkitLogger

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    App[IO]
      .withInfo(
        SimpleAppInfo.string(
          name = "toolkit",
          version = "0.0.1",
          scalaVersion = "2.13.10",
          sbtVersion = "1.8.0"
        )
      )
      .withPureLogger(ToolkitLogger.console[IO](_))
      .withConfigF(_ => IO.pure(Config("localhost", 8080)))
      .dependsOn(AppDependencyServices.resource(_))
      .beforeProviding(_.logger.info("CUSTOM PRE-PROVIDING"))
      .provideOne(deps =>
        // Kafka consumer
        deps.dependencies.kafkaConsumer
          .consumeFrom("test-topic")
          .evalTap(record => deps.logger.info(s"Received record $record"))
          .compile
          .drain
      )
      .onFinalizeSeq(_.logger.info("CUSTOM END"))
      .run(args)
}
```

Check a full example [here](https://github.com/geirolz/toolkit/tree/main/examples)

For detailed usage examples and API documentation, please refer to
the [Toolkit Wiki](https://github.com/geirolz/toolkit/wiki).

## Integrations

For a comprehensive list of integrations between Toolkit and other popular libraries,
please refer to the [integrations.md](docs/compiled/integrations.md) file.

It provides an overview of the integrations available, including libraries such as PureConfig, Log4cats, Odin, and more.

Each integration showcases the benefits and features it brings to your Toolkit-based applications,
enabling you to enhance functionality and streamline development. Explore the integrations to leverage the power of
Toolkit
in combination with other powerful libraries.

## Adopters

If you are using Toolkit in your company, please let me know and I'll add it to the list! It means a lot to me.

## Contributing

We welcome contributions from the open-source community to make Toolkit even better. If you have any bug reports,
feature requests, or suggestions, please submit them via GitHub issues. Pull requests are also welcome.

Before contributing, please read
our [Contribution Guidelines](https://github.com/geirolz/toolkit/blob/main/CONTRIBUTING.md) to understand the
development process and coding conventions.

Please remember te following:

- Run `sbt scalafmtAll` before submitting a PR.
- Run `sbt gen-doc` to update the documentation.

## License

Toolkit is released under the [Apache License 2.0](https://github.com/geirolz/toolkit/blob/main/LICENSE).
Feel free to use it in your open-source or commercial projects.

## Acknowledgements

We would like to thank all the contributors who have made Toolkit possible. Your valuable feedback, bug reports, and
code contributions have helped shape and improve the library.

## Contact

For any questions or inquiries, you can reach out to the maintainers of Toolkit via email or open an issue in the
GitHub repository.

---

Happy coding with Toolkit!

