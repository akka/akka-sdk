/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.LoggingTestKit
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

class ModelAttributesSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {

  "ModelAttributes.logDroppedAttributes" should {

    "log the agent, the session and the dropped keys" in {
      val attributes =
        java.util.Map.of[String, AnyRef]("signature", "abc", "count", Integer.valueOf(42), "image", new Object)
      val kept = ModelAttributes.keepStringValues(attributes)

      LoggingTestKit
        .warn("dropped model attributes")
        .withCustom { event =>
          event.message.contains("[my-agent]") &&
          event.message.contains("[session-1]") &&
          event.message.contains("count") &&
          event.message.contains("image") &&
          !event.message.contains("signature")
        }
        .expect(ModelAttributes.logDroppedAttributes("my-agent", "session-1", attributes, kept))
    }

    "not log when nothing is dropped" in {
      val attributes = java.util.Map.of[String, AnyRef]("signature", "abc")
      val kept = ModelAttributes.keepStringValues(attributes)

      LoggingTestKit
        .warn("dropped model attributes")
        .withOccurrences(0)
        .expect(ModelAttributes.logDroppedAttributes("my-agent", "session-1", attributes, kept))
    }
  }
}
