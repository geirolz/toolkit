# app-toolkit
[![Build Status](https://github.com/geirolz/app-toolkit/actions/workflows/cicd.yml/badge.svg)](https://github.com/geirolz/app-toolkit/actions)
[![codecov](https://img.shields.io/codecov/c/github/geirolz/app-toolkit)](https://codecov.io/gh/geirolz/app-toolkit)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/db3274b55e0c4031803afb45f58d4413)](https://www.codacy.com/manual/david.geirola/app-toolkit?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=geirolz/app-toolkit&amp;utm_campaign=Badge_Grade)
[![Sonatype Nexus (Releases)](https://img.shields.io/nexus/r/com.github.geirolz/app-toolkit-core_2.13?server=https%3A%2F%2Foss.sonatype.org)](https://mvnrepository.com/artifact/com.github.geirolz/app-toolkit-core)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
[![Mergify Status](https://img.shields.io/endpoint.svg?url=https://api.mergify.com/v1/badges/geirolz/app-toolkit&style=flat)](https://mergify.io)
[![GitHub license](https://img.shields.io/github/license/geirolz/app-toolkit)](https://github.com/geirolz/app-toolkit/blob/main/LICENSE)

A small toolkit to build functional app with managed resources

```sbt
libraryDependencies += "com.github.geirolz" %% "app-toolkit-core" % "@VERSION@"
```

Check the full example [here](https://github.com/geirolz/app-toolkit/tree/main/example)

- `dependsOn` let you define the app dependencies expressed by a `Resource[F, DEPENDENCIES]`
- `provideOne` let you define the app logic expressed by an `F[?]`
- `provide` let you define the app provided services expressed by a `List[F[?]]` which will be run in parallel
- `provideF` let you define the app provided services expressed by a `F[List[F[?]]]` which will be run in parallel


Given
```scala mdoc:silent
import cats.Show
import cats.effect.{ExitCode, Resource, IO, IOApp}
import com.geirolz.app.toolkit.{ App, SimpleAppInfo }
import com.geirolz.app.toolkit.logger.ToolkitLogger
import com.geirolz.app.toolkit.novalues.NoResources
import org.typelevel.log4cats.slf4j.Slf4jLogger

// Define config
case class Config(host: String, port: Int)
object Config {
  implicit val show: Show[Config] = Show.fromToString
}

// Define service dependencies
case class AppDependencyServices(
 kafkaConsumer: KafkaConsumer[IO]
)
object AppDependencyServices {
  def resource(res: App.Resources[SimpleAppInfo[String], ToolkitLogger[IO], Config, NoResources]): Resource[IO, AppDependencyServices] =
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

```scala mdoc:silent
import cats.effect.{ExitCode, IO, IOApp}
import com.geirolz.app.toolkit.App
import com.geirolz.app.toolkit.logger.ToolkitLogger
import com.geirolz.app.toolkit.error.*

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    App[IO]
      .withInfo(
        SimpleAppInfo.string(
          name          = "app-toolkit",
          version       = "0.0.1",
          scalaVersion  = "2.13.10",
          sbtVersion    = "1.8.0"
        )
       )
      .withLogger(ToolkitLogger.console[IO](_))
      .withConfigLoader(_ => IO.pure(Config("localhost", 8080)))
      .dependsOn(AppDependencyServices.resource(_))
      .provideOne(deps =>
          // Kafka consumer
          deps.dependencies.kafkaConsumer
            .consumeFrom("test-topic")
            .evalTap(record => deps.logger.info(s"Received record $record"))
            .compile
            .drain
      )
      .beforeRun(_.logger.info("CUSTOM PRE-RUN"))
      .onFinalize(_.logger.info("CUSTOM END"))
      .run(ExitCode.Success)
}
```


### Integrations
#### pureconfig 
```sbt
libraryDependencies += "com.github.geirolz" %% "app-toolkit-config-pureconfig" % "@VERSION@"
```

#### log4cats
```sbt
libraryDependencies += "com.github.geirolz" %% "app-toolkit-log4cats" % "@VERSION@"
```

#### odin
```sbt
libraryDependencies += "com.github.geirolz" %% "app-toolkit-odin" % "@VERSION@"
```