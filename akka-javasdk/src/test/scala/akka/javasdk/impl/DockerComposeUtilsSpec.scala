/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.DockerComposeUtils
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class DockerComposeUtilsSpec extends AnyWordSpec with Matchers with OptionValues {

  private val defaultFile =
    """
      |version: "3"
      |services:
      |  kalix-runtime:
      |    image: gcr.io/kalix-public/kalix-runtime:1.1.31
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
      |      USER_SERVICE_HOST: ${USER_SERVICE_HOST:-host.docker.internal}
      |      USER_SERVICE_PORT: ${USER_SERVICE_PORT:-8081}
      |""".stripMargin

  "DockerComposeUtils" should {

    import DevModeSettingsSpec.createTmpFile

    "read user function port from docker-compose file" in {
      val dockerComposeFile = createTmpFile(defaultFile)
      val dockerComposeUtils = DockerComposeUtils(dockerComposeFile)
      dockerComposeUtils.userFunctionPort shouldBe 8081
    }

    "favor USER_SERVICE_PORT env var if set " in {
      val envVar = Map("USER_SERVICE_PORT" -> "8082")
      val dockerComposeFile = createTmpFile(defaultFile, envVar)
      val dockerComposeUtils = DockerComposeUtils(dockerComposeFile)
      dockerComposeUtils.userFunctionPort shouldBe 8082
    }

    "default to UF port 8080 when nothing is defined" in {
      val fileContent =
        """
          |version: "3"
          |services:
          |  kalix-runtime:
          |    image: gcr.io/kalix-public/kalix-runtime:1.1.31
          |    ports:
          |      - "9000:9000"
          |    extra_hosts:
          |      - "host.docker.internal:host-gateway"
          |""".stripMargin

      val dockerComposeFile = createTmpFile(fileContent)
      val dockerComposeUtils = DockerComposeUtils(dockerComposeFile)
      dockerComposeUtils.userFunctionPort shouldBe 8080
    }

    "be able to read user service port when env var is not used" in {
      val fileWithoutEnvVar =
        """
          |version: "3"
          |services:
          |  kalix-runtime:
          |    image: gcr.io/kalix-public/kalix-runtime:1.1.31
          |    ports:
          |      - "9000:9000"
          |    extra_hosts:
          |      - "host.docker.internal:host-gateway"
          |    environment:
          |      USER_SERVICE_PORT: 8081
          |""".stripMargin

      val dockerComposeFile = createTmpFile(fileWithoutEnvVar)
      val dockerComposeUtils = DockerComposeUtils(dockerComposeFile)
      dockerComposeUtils.userFunctionPort shouldBe 8081
    }

    "read services and ports from docker-compose file" in {
      val dockerComposeFile = createTmpFile(defaultFile)
      val dockerComposeUtils = DockerComposeUtils(dockerComposeFile)
      dockerComposeUtils.servicesHostAndPortMap shouldBe Map("foo" -> "9001", "bar" -> "9002")
    }

    "not fail if docker-compose file is absent" in {
      val dockerComposeUtils = DockerComposeUtils(UUID.randomUUID().toString)
      // in which case it should default to 8080
      dockerComposeUtils.userFunctionPort shouldBe 8080
      dockerComposeUtils.servicesHostAndPortMap shouldBe Map.empty
    }

    "select first UF port when more than one proxy is declared." in {

      val extraProxy =
        """
          |  kalix-runtime-2:
          |    image: gcr.io/kalix-public/kalix-runtime:1.1.31
          |    ports:
          |      - "9000:9000"
          |    extra_hosts:
          |      - "host.docker.internal:host-gateway"
          |    environment:
          |      JAVA_TOOL_OPTIONS: >
          |        -Dconfig.resource=dev-mode.conf
          |        -Dlogback.configurationFile=logback-dev-mode.xml
          |      USER_SERVICE_HOST: ${USER_SERVICE_HOST:-host.docker.internal}
          |      USER_SERVICE_PORT: ${USER_SERVICE_PORT:-8082}
          |""".stripMargin

      val dockerComposeFile = createTmpFile(defaultFile + extraProxy)
      val dockerComposeUtils = DockerComposeUtils(dockerComposeFile)
      dockerComposeUtils.userFunctionPort shouldBe 8081
    }

    "not load DockerComposeUtils if configured to 'none'" in {
      val dcu =
        DockerComposeUtils.fromConfig(ConfigFactory.parseString("akka.platform.dev-mode.docker-compose-file=none"))
      dcu shouldBe empty
    }

    "not load DockerComposeUtils if file doesn't exist" in {
      val dcu =
        DockerComposeUtils.fromConfig(
          ConfigFactory.parseString("akka.platform.dev-mode.docker-compose-file=non-existing-file.yml"))
      dcu shouldBe empty
    }

  }

}
