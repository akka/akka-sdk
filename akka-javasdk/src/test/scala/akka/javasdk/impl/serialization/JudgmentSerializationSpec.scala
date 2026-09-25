/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.serialization

import java.util.{ List => JList }
import java.util.{ Map => JMap }

import akka.javasdk.JsonSupport
import akka.javasdk.agent.Agent
import akka.javasdk.agent.Judgment
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JudgmentSerializationSpec extends AnyWordSpec with Matchers {

  private val serializer = new Serializer(JsonSupport.getObjectMapper)

  "A Judgment" should {

    "round trip through JSON with all answer types" in {
      val judgment = new Judgment(
        JMap.of(
          "route",
          new Judgment.ChoiceAnswer("billing", JMap.of("billing", 0.9, "technical", 0.1), 0.8),
          "severity",
          new Judgment.ScoreAnswer(1.3, JList.of("Low", "Medium", "High"), JList.of(0.15, 0.65, 0.2), 0.58),
          "urgent",
          new Judgment.YesNoAnswer(0.87)),
        "jev-1.13.0",
        new Agent.TokenUsage(120, 9))

      val bytes = serializer.toBytesAsJson(judgment)
      val json = bytes.bytes.utf8String
      json should include("\"type\":\"choice\"")
      json should include("\"type\":\"score\"")
      json should include("\"type\":\"noul\"")
      (json should not).include("\"yes\"")

      val restored = serializer.fromBytes(classOf[Judgment], bytes)
      restored shouldBe judgment
      restored.choice("route").selected shouldBe "billing"
      restored.score("severity").legend.get(1) shouldBe "Medium"
      restored.yesNo("urgent").isYes shouldBe true
    }
  }
}
