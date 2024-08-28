/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import scala.collection.mutable
import scala.sys.process._
import scala.util.matching.Regex

/**
 * INTERNAL API
 */
@InternalApi
object DockerComposeUtils {

  def fromConfig(config: Config): Option[DockerComposeUtils] =
    Option(config.getString("akka.platform.dev-mode.docker-compose-file"))
      .filter(_.trim.toLowerCase != "none")
      .filter { file => new File(sys.props("user.dir"), file).exists() }
      .map { file => DockerComposeUtils(file) }
}

/**
 * INTERNAL API
 */
@InternalApi
case class DockerComposeUtils(file: String) {

  @volatile private var started = false

  private def execIfFileExists[T](block: => T): T =
    if (Files.exists(Paths.get(file)))
      block
    else {
      val extraMsg =
        if (file == "docker-compose.yml")
          "This file is included in the project by default. Check if it was not deleted by mistake."
        else
          "Check if your build is configured correctly and the file name was not mistyped."

      throw new IllegalArgumentException(s"File '$file' does not exist. $extraMsg")
    }

  // read the file once and cache the lines
  // we will need to iterate over it more than once
  private lazy val lines: Seq[String] =
    if (Files.exists(Paths.get(file))) {
      val collectedLines = mutable.Buffer.empty[String]
      val processLogger = ProcessLogger(out => collectedLines.append(out))
      Process(s"docker compose -f $file config", None).!(processLogger)
      collectedLines.toSeq // to immutable Seq
    } else {
      Seq.empty
    }

  // docker-compose sends some of its output to stderr even when it's not an error
  // to avoid sending the wrong message to users, we redirect to stdout
  // unfortunately, real errors won't be logged as errors anymore
  // this is an issue with some versions of docker-compose, latest version seems to have it fixed
  // (see https://github.com/docker/compose/issues/7346)
  private val processLogger = ProcessLogger(out => println(out))

  // FIXME: process output is being printed in sbt console as error
  // note to self: this is seems to be similar to sbt native packager printing errors when build docker images
  def start(): Unit =
    execIfFileExists {
      val proc = Process(s"docker compose -f $file up", None).run(processLogger)
      started = proc.isAlive()
      // shutdown hook to down containers when jvm exits
      sys.addShutdownHook {
        execIfFileExists {
          stop()
        }
      }
    }

  def stop(): Unit =
    if (started)
      execIfFileExists {
        Process(s"docker compose -f $file stop", None).run(processLogger)
      }

  def stopAndWait(): Int =
    if (started)
      execIfFileExists {
        Process(s"docker compose -f $file stop", None).!(processLogger)
      }
    else 0

  def userFunctionPort: Int =
    userFunctionPortFromFile.getOrElse(8080)

  private def userFunctionPortFromFile: Option[Int] =
    lines.collectFirst { case UserServicePortExtractor(port) => port }

  /**
   * Extract all lines starting with [[DevModeSettings.portMappingsKeyPrefix]] The returned Seq only contains the
   * service name and the mapped host and port, eg: some-service=somehost:9001
   */
  private def servicePortMappings: Seq[String] =
    lines.flatten {
      case ServicePortMappingsExtractor(mappings) => mappings
      case _                                      => Seq.empty
    }

  def tracingConfig: Option[Int] =
    TracingPortExtractor.unapply(lines)

  /**
   * Returns a Map from service name to host:port.
   */
  def servicesHostAndPortMap: Map[String, String] =
    servicePortMappings.map { mapping =>
      mapping.split("=") match {
        case Array(serviceName, hostAndPort) => serviceName -> hostAndPort
        case _                               => throw new IllegalArgumentException(s"Invalid port mapping: $mapping")
      }
    }.toMap

  private object ServicePortMappingsExtractor {

    /**
     * Extracts all occurrences of [[DevModeSettings.portMappingsKeyPrefix]] from a line and returns them as a Seq
     * without the settings prefix.
     */
    def unapply(line: String): Option[Seq[String]] = {
      val portMappings =
        line
          .split("-D")
          .collect {
            case s if s.startsWith(DevModeSettings.portMappingsKeyPrefix) =>
              s.trim.replace(DevModeSettings.portMappingsKeyPrefix + ".", "")
          }

      if (portMappings.nonEmpty) Some(portMappings.toIndexedSeq) else None
    }

  }

  private object TracingPortExtractor {

    val PortPattern: Regex = """:(\d{4,5})""".r

    /**
     * Extracts the port of the `collector-endpoint` from the docker file (after substituting any variable) if tracing
     * is enabled.
     * @param dockerComposeUtilsLines
     * @return
     */
    def unapply(dockerComposeUtilsLines: Seq[String]): Option[Int] = {

      def isValidPort(port: String): Boolean = port.toInt >= 1023 && port.toInt <= 65535
      def removeColon(port: String): String = port.replace(":", "")

      val lines =
        dockerComposeUtilsLines
          .filter(_.contains("-D"))
          .flatMap(_.split("-D"))
          .filter(_.trim.startsWith("kalix.proxy.telemetry.tracing"))

      lines
        .foldLeft(Option(-1)) {
          case (None, _) => None // once it becomes None, it stays None
          case (port, line) if line.startsWith(DevModeSettings.tracingConfigEnabled) =>
            val enabled = ConfigFactory.parseString(line).getBoolean(DevModeSettings.tracingConfigEnabled)
            port.filter(_ => enabled)
          case (_, line) if line.startsWith(DevModeSettings.tracingConfigEndpoint) =>
            PortPattern
              .findFirstIn(line)
              .map(removeColon)
              .filter(isValidPort)
              .map(_.toInt)
          case _ => None
        }
        .filter(_ > 0)
    }
  }

  object UserServicePortExtractor {

    private val ExtractPort = """USER_SERVICE_PORT:.*?(\d+).?""".r
    private val ExtractLegacyPort = """USER_FUNCTION_PORT:.*?(\d+).?""".r

    def unapply(line: String): Option[Int] =
      line.trim match {
        case ExtractPort(port)       => Some(port.toInt)
        case ExtractLegacyPort(port) => Some(port.toInt)
        case _                       => None
      }
  }

}
