/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GovernanceDefaultsSpec extends AnyWordSpec with Matchers {

  "the SDK reference config" should {

    "hold the default guardrails from governance.conf" in {
      val ref = ConfigFactory.defaultReference()

      ref.getString(
        "akka.javasdk.agent.guardrails.\"default model-call jailbreak\".class") shouldBe "akka.javasdk.agent.ModelCallSimilarityGuard"
      ref.getString(
        "akka.javasdk.agent.guardrails.\"default jailbreak\".class") shouldBe "akka.javasdk.agent.SimilarityGuard"
      ref.getStringList("akka.javasdk.agent.guardrails.\"default model-call jailbreak\".agents") shouldBe empty
    }

    "ship governance.conf as its own resource" in {
      val defaults = ConfigFactory.parseResources("akka/javasdk/governance.conf")

      defaults.hasPath("akka.javasdk.agent.guardrails.\"default model-call jailbreak\".category") shouldBe true
    }
  }
}
