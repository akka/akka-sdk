/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.DevModeSettings
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

object DevModeSettingsSpec {
  private val defaultFile =
    """
      |version: "3"
      |services:
      |  kalix-runtime:
      |    image: gcr.io/kalix-public/kalix-runtime:1.1.27
      |    ports:
      |      - "9000:9000"
      |    extra_hosts:
      |      - "host.docker.internal:host-gateway"
      |    environment:
      |      JAVA_TOOL_OPTIONS: >
      |        -Dconfig.resource=dev-mode.conf
      |        -Dlogback.configurationFile=logback-dev-mode.xml
      |        -Dakka.platform.dev-mode.service-port-mappings.foo=9001
      |        -Dakka.platform.dev-mode.service-port-mappings.bar=9002
      |        -Dakka.platform.dev-mode.service-port-mappings.baz=host.docker.internal:9003
      |      USER_SERVICE_HOST: ${USER_SERVICE_HOST:-host.docker.internal}
      |      USER_SERVICE_PORT: ${USER_SERVICE_PORT:-8081}
      |""".stripMargin

  def createTmpFile(fileContent: String, env: Map[String, String] = Map.empty): String = {
    // write docker-compose.yml to a temporary file
    val userDir = sys.props("user.dir")

    val envFile = new File(new File(userDir, "target"), ".env")
    // if previous exist, we should delete it
    if (envFile.exists()) envFile.delete()

    // create .env file if needed
    if (env.nonEmpty) {
      envFile.deleteOnExit()
      val envBuff = new BufferedWriter(new FileWriter(envFile))
      env.foreach { case (key, value) =>
        envBuff.write(s"$key=$value")
      }
      envBuff.close()
    }

    val dockerComposeFile = File.createTempFile("docker-compose-", ".yml", new File(userDir, "target"))
    dockerComposeFile.deleteOnExit()
    val bw = new BufferedWriter(new FileWriter(dockerComposeFile))
    bw.write(fileContent)
    bw.close()
    dockerComposeFile.getAbsolutePath.replace(userDir + "/", "")
  }
}

class DevModeSettingsSpec extends AnyWordSpec with Matchers with OptionValues {
  import DevModeSettingsSpec.createTmpFile

  "DevModeSettings" should {

    "override user function port using docker-compose file" in {
      val dockerComposeFile = createTmpFile(DevModeSettingsSpec.defaultFile)
      val config = ConfigFactory.parseString(s"""
          |kalix.user-function-port = "8080"
          |akka.platform.dev-mode.docker-compose-file = $dockerComposeFile
          |""".stripMargin)

      val enrichedConfig = DevModeSettings.addDevModeConfig(config)

      enrichedConfig.getString("kalix.user-function-port") shouldBe "8081"
    }

    "add port mappings for WebClient from docker-compose file" in {
      val dockerComposeFile = createTmpFile(DevModeSettingsSpec.defaultFile)
      val config = ConfigFactory.parseString(s"""
           |akka.platform.dev-mode.docker-compose-file = $dockerComposeFile
           |""".stripMargin)

      val enrichedConfig = DevModeSettings.addDevModeConfig(config)

      // when relying on docker-compose file, hosts are replaced by 0.0.0.0
      enrichedConfig.getString("akka.platform.dev-mode.service-port-mappings.foo") shouldBe "0.0.0.0:9001"
      enrichedConfig.getString("akka.platform.dev-mode.service-port-mappings.bar") shouldBe "0.0.0.0:9002"
      enrichedConfig.getString("akka.platform.dev-mode.service-port-mappings.baz") shouldBe "0.0.0.0:9003"
    }

    "add port gRPC client configs from docker-compose file" in {
      val dockerComposeFile = createTmpFile(DevModeSettingsSpec.defaultFile)
      val config = ConfigFactory.parseString(s"""
           |akka.platform.dev-mode.docker-compose-file = $dockerComposeFile
           |""".stripMargin)

      val enrichedConfig = DevModeSettings.addDevModeConfig(config)

      // when relying on docker-compose file, hosts are replaced by 0.0.0.0
      enrichedConfig.getString("akka.grpc.client.foo.host") shouldBe "0.0.0.0"
      enrichedConfig.getString("akka.grpc.client.foo.port") shouldBe "9001"
      enrichedConfig.getString("akka.grpc.client.bar.host") shouldBe "0.0.0.0"
      enrichedConfig.getString("akka.grpc.client.bar.port") shouldBe "9002"
      enrichedConfig.getString("akka.grpc.client.baz.host") shouldBe "0.0.0.0"
      enrichedConfig.getString("akka.grpc.client.baz.port") shouldBe "9003"
    }

    "add port gRPC client configs from existing dev-mode settings" in {
      val config = ConfigFactory.parseString(s"""
           |akka.platform.dev-mode.docker-compose-file = none
           |akka.platform.dev-mode.service-port-mappings.foo = "foo.docker.internal:9001"
           |akka.platform.dev-mode.service-port-mappings.bar = "barhost:9002"
           |akka.platform.dev-mode.service-port-mappings.baz = "baz-host:9003"
           |""".stripMargin)

      val enrichedConfig = DevModeSettings.addDevModeConfig(config)

      // when not relying on docker-compose file, hosts are kept as as originally
      enrichedConfig.getString("akka.grpc.client.foo.host") shouldBe "foo.docker.internal"
      enrichedConfig.getString("akka.grpc.client.foo.port") shouldBe "9001"
      enrichedConfig.getString("akka.grpc.client.bar.host") shouldBe "barhost"
      enrichedConfig.getString("akka.grpc.client.bar.port") shouldBe "9002"
      enrichedConfig.getString("akka.grpc.client.baz.host") shouldBe "baz-host"
      enrichedConfig.getString("akka.grpc.client.baz.port") shouldBe "9003"
    }
  }

}
