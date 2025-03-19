import net.aichler.jupiter.sbt.Import.JupiterKeys
import sbt._
import sbt.Keys._

object Dependencies {
  object Kalix {
    val ProtocolVersionMajor = 1
    val ProtocolVersionMinor = 1
    val RuntimeImage = "gcr.io/kalix-public/kalix-runtime"
    // Remember to bump kalix-runtime.version in akka-javasdk-maven/akka-javasdk-parent if bumping this
    val RuntimeVersion = sys.props.getOrElse("kalix-runtime.version", "1.4.4")
  }
  // NOTE: embedded SDK should have the AkkaVersion aligned, when updating RuntimeVersion, make sure to check
  // if AkkaVersion and AkkaHttpVersion are aligned
  // for prod code, they are marked as Provided, but testkit still requires the alignment
  val AkkaVersion = "2.10.2"
  val AkkaHttpVersion = "10.7.0" // Note: should at least the Akka HTTP version required by Akka gRPC

  // Note: the Scala version must be aligned with the runtime
  val ScalaVersion = "2.13.16"
  val CrossScalaVersions = Seq(ScalaVersion)

  val ScalaTestVersion = "3.2.14"
  // https://github.com/akka/akka/blob/main/project/Dependencies.scala#L31
  val JacksonVersion = "2.17.2"
  val JacksonDatabindVersion = JacksonVersion
  val LogbackVersion = "1.5.12"
  val LogbackContribVersion = "0.1.5"
  val JUnitVersion = "4.13.2"
  val JUnitInterfaceVersion = "0.11"
  val JUnitJupiterVersion = "5.10.1"
  val OpenTelemetryVersion = "1.39.0"
  val OpenTelemetrySemConv = "1.25.0-alpha"

  val CommonsIoVersion = "2.11.0"
  val MunitVersion = "0.7.29"

  val kalixTestkitProtocol = "io.kalix" % "kalix-testkit-protocol" % Kalix.RuntimeVersion
  val kalixSdkSpi = "io.akka" %% "akka-sdk-spi" % Kalix.RuntimeVersion

  // Note: this should never be on the compile classpath, only test and or runtime
  val kalixDevRuntime = "io.kalix" %% "kalix-dev-runtime" % Kalix.RuntimeVersion

  val commonsIo = "commons-io" % "commons-io" % CommonsIoVersion
  val logback = "ch.qos.logback" % "logback-classic" % LogbackVersion
  val logbackJson = "ch.qos.logback.contrib" % "logback-json-classic" % LogbackContribVersion
  val logbackJackson = "ch.qos.logback.contrib" % "logback-jackson" % LogbackContribVersion

  val slf4jApi = "org.slf4j" % "slf4j-api" % "2.0.16"

  val jacksonCore = "com.fasterxml.jackson.core" % "jackson-core" % JacksonVersion
  val jacksonAnnotations = "com.fasterxml.jackson.core" % "jackson-annotations" % JacksonVersion
  val jacksonDatabind = "com.fasterxml.jackson.core" % "jackson-databind" % JacksonDatabindVersion
  val jacksonJdk8 = "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % JacksonVersion
  val jacksonJsr310 = "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % JacksonVersion
  val jacksonParameterNames = "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % JacksonVersion
  val jacksonScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % JacksonVersion

  val scalaTest = "org.scalatest" %% "scalatest" % ScalaTestVersion
  val munit = "org.scalameta" %% "munit" % MunitVersion
  val munitScalaCheck = "org.scalameta" %% "munit-scalacheck" % MunitVersion
  val junit4 = "junit" % "junit" % JUnitVersion
  val junit5 = "org.junit.jupiter" % "junit-jupiter" % JUnitJupiterVersion
  val junit5Vintage = "org.junit.vintage" % "junit-vintage-engine" % JUnitJupiterVersion

  val opentelemetryApi = "io.opentelemetry" % "opentelemetry-api" % OpenTelemetryVersion
  val opentelemetrySdk = "io.opentelemetry" % "opentelemetry-sdk" % OpenTelemetryVersion
  val opentelemetryExporterOtlp = "io.opentelemetry" % "opentelemetry-exporter-otlp" % OpenTelemetryVersion
  val opentelemetryContext = "io.opentelemetry" % "opentelemetry-context" % OpenTelemetryVersion
  val opentelemetrySemConv = "io.opentelemetry.semconv" % "opentelemetry-semconv" % OpenTelemetrySemConv

  val typesafeConfig = "com.typesafe" % "config" % "1.4.2"

  private val deps = libraryDependencies

  private val sdkDeps = Seq(
    opentelemetryApi,
    opentelemetrySdk,
    opentelemetryExporterOtlp,
    opentelemetryContext,
    opentelemetrySemConv,
    // akka-http is pulling akka-pki and akka-discovery, we need to force it to be same version
    akkaDependency("akka-pki"),
    akkaDependency("akka-discovery"),
    akkaDependency("akka-testkit") % Test,
    akkaDependency("akka-actor-testkit-typed") % Test,
    akkaDependency("akka-stream-testkit") % Test,
    akkaHttpDependency("akka-http-testkit") % Test,
    scalaTest % Test,
    slf4jApi,
    logback,
    logbackJson,
    logbackJackson,
    jacksonCore,
    jacksonAnnotations,
    jacksonDatabind,
    jacksonJdk8,
    jacksonJsr310,
    jacksonParameterNames)

  // Important: be careful when adding dependencies here, unless provided, runtime or test they will also be packaged in the user project
  //            binaries/artifacts unless explicitly excluded in the akka-javasdk-parent assembly descriptor
  val javaSdk = deps ++= sdkDeps ++ Seq(
    kalixSdkSpi,
    // make sure these two are on the classpath for users to consume http request/response APIs and streams
    "com.typesafe.akka" %% "akka-http-core" % AkkaHttpVersion,
    akkaDependency("akka-stream"),
    akkaDependency("akka-actor-typed") % Provided,
    "net.aichler" % "jupiter-interface" % JupiterKeys.jupiterVersion.value % Test,
    junit5 % Test,
    "org.assertj" % "assertj-core" % "3.24.2" % Test)

  val javaSdkTestKit =
    deps ++=
      Seq(
        // These two are for the eventing testkit
        akkaDependency("akka-actor-testkit-typed"),
        akkaDependency("akka-stream-testkit"),
        akkaDependency("akka-testkit"),
        kalixTestkitProtocol % "protobuf-src",
        // FIXME use by the testkit itself but should not be on user test classpath
        //      should possibly be provided or runtime here and added for the test runner in project parent pom
        kalixDevRuntime,
        // user will interface with these
        junit5,
        // convenience-transitive dependencies for user assertions and async interactions
        "org.awaitility" % "awaitility" % "4.2.1",
        "org.assertj" % "assertj-core" % "3.24.2",
        // for the tests of the testkit itself
        "net.aichler" % "jupiter-interface" % JupiterKeys.jupiterVersion.value % Test,
        scalaTest % Test)

  val tests =
    deps ++= Seq(
      // FIXME why doesn't these two come along transitively from the testkit?
      "org.assertj" % "assertj-core" % "3.24.2" % Test,
      "org.awaitility" % "awaitility" % "4.2.1" % Test,
      kalixDevRuntime % Test,
      akkaDependency("akka-testkit"),
      // These are for the test of the testkit
      "net.aichler" % "jupiter-interface" % JupiterKeys.jupiterVersion.value % Test,
      scalaTest % Test,
      akkaDependency("akka-actor-testkit-typed") % Test)

  lazy val excludeTheseDependencies: Seq[ExclusionRule] = Seq(
    // exclusion rules can be added here
  )

  def akkaDependency(name: String, excludeThese: ExclusionRule*): ModuleID =
    ("com.typesafe.akka" %% name % AkkaVersion).excludeAll((excludeTheseDependencies ++ excludeThese): _*)

  def akkaHttpDependency(name: String, excludeThese: ExclusionRule*): ModuleID =
    ("com.typesafe.akka" %% name % AkkaHttpVersion).excludeAll((excludeTheseDependencies ++ excludeThese): _*)

}
