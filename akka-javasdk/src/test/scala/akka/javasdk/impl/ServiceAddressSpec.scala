/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.runtime.sdk.spi.DeploymentInfo
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ServiceAddressSpec extends AnyWordSpec with Matchers {

  private val deployed = new DeploymentInfo("abc", "abc", None)
  private val local = new DeploymentInfo("", "", None)

  "ServiceAddress" should {

    "use the bare service name for a service in the project" in {
      ServiceAddress.hostFor("my-service", deployed, None) shouldBe "my-service"
      ServiceAddress.hostFor("my-service", local, None) shouldBe "my-service"
    }

    "use the platform service namespace for a service that belongs to a platform service" in {
      ServiceAddress.hostFor("platform-api", deployed, Some("my-platform-service")) shouldBe
      "platform-api.abc-my-platform-service.svc.cluster.local"
    }

    "fail for a service that belongs to a platform service when the project id is unknown" in {
      intercept[IllegalArgumentException] {
        ServiceAddress.hostFor("platform-api", local, Some("my-platform-service"))
      }.getMessage should include("no project id known")
    }

    "accept a valid platform service name" in {
      ServiceAddress.validPlatformService("my-platform-service") shouldBe "my-platform-service"
      ServiceAddress.validPlatformService("a" * 26) shouldBe "a" * 26
    }

    "reject an invalid platform service name" in {
      Seq(null, "", "My-Service", "my_service", "-my-service", "my-service-", "my.service", "a" * 27, "svc").foreach {
        name =>
          withClue(s"[$name]: ") {
            intercept[IllegalArgumentException](ServiceAddress.validPlatformService(name))
          }
      }
    }

    "scope a provider to a platform service from a user service" in {
      ServiceAddress.scopeFor("my-platform-service", deployed) shouldBe Some("my-platform-service")
    }

    "resolve the own platform service in the own namespace" in {
      val inPlatformService = new DeploymentInfo("abc", "abc-my-platform-service", Some("my-platform-service"))
      ServiceAddress.scopeFor("my-platform-service", inPlatformService) shouldBe None
    }

    "reject another platform service from a service of a platform service" in {
      val inPlatformService = new DeploymentInfo("abc", "abc-my-platform-service", Some("my-platform-service"))
      intercept[IllegalArgumentException] {
        ServiceAddress.scopeFor("other-platform-service", inPlatformService)
      }.getMessage should include("Cannot call services in platform service [other-platform-service]")
    }

    "reject a host name or a template together with a platform service in a stream source" in {
      ServiceAddress.streamPlatformService("platform-api", "my-platform-service", getClass) shouldBe Some(
        "my-platform-service")
      ServiceAddress.streamPlatformService("other.example.com", "", getClass) shouldBe None
      Seq("other.example.com", "other:9000", "${MY_SERVICE}").foreach { service =>
        withClue(s"[$service]: ") {
          intercept[IllegalArgumentException] {
            ServiceAddress.streamPlatformService(service, "my-platform-service", getClass)
          }.getMessage should include(getClass.getName)
        }
      }
    }

    "treat an empty annotation value as a service in this project" in {
      ServiceAddress.platformServiceFromAnnotation("") shouldBe None
      ServiceAddress.platformServiceFromAnnotation("my-platform-service") shouldBe Some("my-platform-service")
    }
  }
}
