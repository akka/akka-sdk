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

    "use the feature namespace for a service in a system feature" in {
      ServiceAddress.hostFor("feature-service", deployed, Some("my-feature")) shouldBe
      "feature-service.abc-my-feature.svc.cluster.local"
    }

    "fail for a system feature service when the project id is unknown" in {
      intercept[IllegalArgumentException] {
        ServiceAddress.hostFor("feature-service", local, Some("my-feature"))
      }.getMessage should include("no project id known")
    }
  }
}
