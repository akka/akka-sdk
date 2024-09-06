import net.aichler.jupiter.sbt.Import.JupiterKeys
import sbt._
import sbt.Keys._

object Dependencies {
  object Kalix {
    val ProtocolVersionMajor = 1
    val ProtocolVersionMinor = 1
    val RuntimeImage = "gcr.io/kalix-public/kalix-runtime"
    // Remember to bump kalix-runtime.version in akka-javasdk-maven/akka-javasdk-parent if bumping this
    val RuntimeVersion = sys.props.getOrElse("kalix-runtime.version", "1.1.41-dcb5d51")
  }
  // NOTE: embedded SDK should have the AkkaVersion aligned, when updating RuntimeVersion, make sure to check
  // if AkkaVersion and AkkaHttpVersion are aligned
  // for prod code, they are marked as Provided, but testkit still requires the alignment
  val AkkaVersion = "2.9.5"
  val AkkaHttpVersion = "10.6.3" // Note: should at least the Akka HTTP version required by Akka gRPC

  // changing the Scala version of the Java SDK affects end users
  val ScalaVersion = "2.13.14"
  val ScalaVersionForTooling = "2.12.19"
  val Scala3Version = "3.3.3"
  val CrossScalaVersions = Seq(ScalaVersion, Scala3Version)

  val ProtobufVersion = // akka.grpc.gen.BuildInfo.googleProtobufVersion
    "3.21.12" // explicitly overriding the 3.21.1 version from Akka gRPC 2.1.6 (even though its build says 3.20.1)

  val ScalaTestVersion = "3.2.14"
  // https://github.com/akka/akka/blob/main/project/Dependencies.scala#L31
  val JacksonVersion = "2.15.4"
  val JacksonDatabindVersion = JacksonVersion
  val LogbackVersion = "1.4.14"
  val LogbackContribVersion = "0.1.5"
  val TestContainersVersion = "1.17.6"
  val JUnitVersion = "4.13.2"
  val JUnitInterfaceVersion = "0.11"
  val JUnitJupiterVersion = "5.10.1"
  val OpenTelemetryVersion = "1.39.0"
  val OpenTelemetrySemConv = "1.25.0-alpha"

  val CommonsIoVersion = "2.11.0"
  val MunitVersion = "0.7.29"

  val kalixProxyProtocol = "io.kalix" % "kalix-proxy-protocol" % Kalix.RuntimeVersion
  val kalixSdkProtocol = "io.kalix" % "kalix-sdk-protocol" % Kalix.RuntimeVersion
  val kalixTckProtocol = "io.kalix" % "kalix-tck-protocol" % Kalix.RuntimeVersion
  val kalixTestkitProtocol = "io.kalix" % "kalix-testkit-protocol" % Kalix.RuntimeVersion
  val kalixSdkSpi = "io.akka" %% "akka-sdk-spi" % Kalix.RuntimeVersion

  // Note: this should never be on the compile classpath, only test and or runtime
  val kalixDevRuntime = "io.kalix" %% "kalix-dev-runtime" % Kalix.RuntimeVersion

  val commonsIo = "commons-io" % "commons-io" % CommonsIoVersion
  val logback = "ch.qos.logback" % "logback-classic" % LogbackVersion
  val logbackJson = "ch.qos.logback.contrib" % "logback-json-classic" % LogbackContribVersion
  val logbackJackson = "ch.qos.logback.contrib" % "logback-jackson" % LogbackContribVersion

  // FIXME is this still correct with embedded SDK?
  // akka-slf4j pulls in slf4j-api v1.7.36 and but we want v2.0.9
  // because of Logback v1.4.5+ and because of Spring 3. Therefore we have to explicitly bump slf4j-api.
  // Version 2.0.9 is also problematic for Akka, but only when using the BehaviorTestKit which is not used in the SDK
  val slf4jApi = "org.slf4j" % "slf4j-api" % "2.0.9"

  val protobufJava = "com.google.protobuf" % "protobuf-java" % ProtobufVersion
  val protobufJavaUtil = "com.google.protobuf" % "protobuf-java-util" % ProtobufVersion

  val jacksonCore = "com.fasterxml.jackson.core" % "jackson-core" % JacksonVersion
  val jacksonAnnotations = "com.fasterxml.jackson.core" % "jackson-annotations" % JacksonVersion
  val jacksonDatabind = "com.fasterxml.jackson.core" % "jackson-databind" % JacksonDatabindVersion
  val jacksonJdk8 = "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % JacksonVersion
  val jacksonJsr310 = "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % JacksonVersion
  val jacksonParameterNames = "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % JacksonVersion
  val jacksonScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % JacksonVersion
  val jacksonDataFormatProto = "com.fasterxml.jackson.dataformat" % "jackson-dataformat-protobuf" % JacksonVersion

  val scalaTest = "org.scalatest" %% "scalatest" % ScalaTestVersion
  val munit = "org.scalameta" %% "munit" % MunitVersion
  val munitScalaCheck = "org.scalameta" %% "munit-scalacheck" % MunitVersion
  val testContainers = "org.testcontainers" % "testcontainers" % TestContainersVersion
  val junit4 = "junit" % "junit" % JUnitVersion
  val junit5 = "org.junit.jupiter" % "junit-jupiter" % JUnitJupiterVersion
  val junit5Vintage = "org.junit.vintage" % "junit-vintage-engine" % JUnitJupiterVersion

  val opentelemetryApi = "io.opentelemetry" % "opentelemetry-api" % OpenTelemetryVersion
  val opentelemetrySdk = "io.opentelemetry" % "opentelemetry-sdk" % OpenTelemetryVersion
  val opentelemetryExporterOtlp = "io.opentelemetry" % "opentelemetry-exporter-otlp" % OpenTelemetryVersion
  val opentelemetryContext = "io.opentelemetry" % "opentelemetry-context" % OpenTelemetryVersion
  val opentelemetrySemConv = "io.opentelemetry.semconv" % "opentelemetry-semconv" % OpenTelemetrySemConv

  val scalapbCompilerPlugin = "com.thesamet.scalapb" %% "compilerplugin" % scalapb.compiler.Version.scalapbVersion
  val scalaPbValidateCore = "com.thesamet.scalapb" %% "scalapb-validate-core" % "0.3.4"
  val sbtProtoc = "com.thesamet" % "sbt-protoc" % "1.0.0"

  val scalaCollectionCompat = "org.scala-lang.modules" %% "scala-collection-compat" % "2.10.0"
  val typesafeConfig = "com.typesafe" % "config" % "1.4.2"

  private val deps = libraryDependencies

  val devTools = deps ++= Seq(scalaCollectionCompat, typesafeConfig, scalaTest % Test)

  private val sdkDeps = Seq(
    protobufJavaUtil,
    kalixProxyProtocol % "protobuf-src",
    kalixSdkProtocol % "compile;protobuf-src",
    scalaPbValidateCore,
    opentelemetryApi,
    opentelemetrySdk,
    opentelemetryExporterOtlp,
    opentelemetryContext,
    opentelemetrySemConv,
    // FIXME: akka-http is pulling akka-pki and akka-discovery v2.9.3, we need to force it to be 2.9.4
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
    jacksonDataFormatProto,
    "com.typesafe.akka" %% "akka-http-core" % AkkaHttpVersion,
    akkaDependency("akka-actor-typed") % Provided,
    "org.scala-lang.modules" %% "scala-parser-combinators" % "2.3.0",
    "net.aichler" % "jupiter-interface" % JupiterKeys.jupiterVersion.value % Test,
    junit5 % Test,
    "org.assertj" % "assertj-core" % "3.24.2" % Test)

  val javaSdkTestKit =
    deps ++=
      Seq(
        jacksonDataFormatProto,
        // These two are for the eventing testkit
        akkaDependency("akka-actor-testkit-typed"),
        kalixTestkitProtocol % "protobuf-src",
        // user will interface with these
        junit5,
        // convenience-transitive dependencies for user assertions and async interactions
        "org.awaitility" % "awaitility" % "4.2.1",
        "org.assertj" % "assertj-core" % "3.24.2",
        // FIXME used in the tests of the testkit itself but should not be on user test classpath
        //       should possibly be provided or runtime here and added for the test runner in project parent pom
        kalixDevRuntime,
        // These are for the test of the testkit
        // FIXME move integration tests into test config
        "net.aichler" % "jupiter-interface" % JupiterKeys.jupiterVersion.value % s"$Test, $IntegrationTest",
        scalaTest % Test)

  val tck = deps ++= Seq(
    // FIXME: For now TCK protos have been copied and adapted into this project.
    //        Running the TCK is still meaningful as it runs the TCK check against the defined framework version.
    //        Eventually, the final form of protos from should be backported to the framework.
    //        See https://github.com/lightbend/kalix-jvm-sdk/issues/605
    //  kalixTckProtocol % "protobuf-src",
    //  "io.kalix" % "kalix-tck-protocol" % Kalix.RuntimeVersion % "protobuf-src",
    logback)

  lazy val excludeTheseDependencies: Seq[ExclusionRule] = Seq(
    // exclusion rules can be added here
  )

  def akkaDependency(name: String, excludeThese: ExclusionRule*): ModuleID =
    ("com.typesafe.akka" %% name % AkkaVersion).excludeAll((excludeTheseDependencies ++ excludeThese): _*)

  def akkaHttpDependency(name: String, excludeThese: ExclusionRule*): ModuleID =
    ("com.typesafe.akka" %% name % AkkaHttpVersion).excludeAll((excludeTheseDependencies ++ excludeThese): _*)

}
