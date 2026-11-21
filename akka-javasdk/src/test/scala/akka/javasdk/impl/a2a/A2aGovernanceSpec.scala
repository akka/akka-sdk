/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import akka.javasdk.JsonSupport
import akka.javasdk.SanitizerClient
import akka.javasdk.a2a.A2aArtifact
import akka.javasdk.a2a.A2aMessage
import akka.javasdk.a2a.A2aPart
import akka.javasdk.a2a.A2aRole
import akka.javasdk.a2a.A2aTask
import akka.javasdk.a2a.A2aTaskState
import akka.javasdk.a2a.A2aTaskStatus
import akka.javasdk.a2a.A2aTaskStatusUpdate
import akka.javasdk.a2a.A2aTaskUpdate
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class A2aGovernanceSpec extends AnyWordSpec with Matchers {
  implicit val ec: ExecutionContext = ExecutionContext.global

  // Masks digits, and records the sanitizer names in the order they ran
  private val client = new SanitizerClient {
    override def sanitize(name: String, text: String): String = text.replaceAll("[0-9]", "*")
    override def sanitizeAsync(name: String, text: String): CompletionStage[String] =
      CompletableFuture.completedFuture(sanitize(name, text))
  }

  "The reply sanitizer" should {

    "mask text parts and the strings of data parts in a task" in {
      val sanitizer = new A2aReplySanitizer(Seq("digits"), client)
      val task = new A2aTask(
        "t1",
        "c1",
        new A2aTaskStatus(A2aTaskState.COMPLETED, A2aMessage.user("status 12").withRole(A2aRole.AGENT), null),
        java.util.List.of(
          new A2aArtifact(
            "a1",
            null,
            null,
            java.util.List.of(
              A2aPart.text("claim 34"),
              A2aPart.data(java.util.Map.of("ref", "56", "count", Integer.valueOf(7))),
              A2aPart.url("https://x/1", null, null)),
            null,
            null)),
        java.util.List.of(A2aMessage.user("history 89")),
        null)

      val masked = Await.result(sanitizer.task(task), 3.seconds)

      masked.status().message().parts().get(0).asInstanceOf[A2aPart.Text].text() shouldBe "status **"
      val parts = masked.artifacts().get(0).parts().asScala
      parts(0).asInstanceOf[A2aPart.Text].text() shouldBe "claim **"
      parts(1).asInstanceOf[A2aPart.Data].data() shouldBe java.util.Map.of("ref", "**", "count", Integer.valueOf(7))
      parts(2).asInstanceOf[A2aPart.Url].url() shouldBe "https://x/1"
      masked.history().get(0).parts().get(0).asInstanceOf[A2aPart.Text].text() shouldBe "history **"
    }

    "leave a reply as it is without sanitizers" in {
      val task = new A2aTask("t1", "c1", null, null, null, null)
      (Await.result(new A2aReplySanitizer(Nil, client).task(task), 3.seconds) should be).theSameInstanceAs(task)
    }
  }

  "The follow timeout update" should {

    "say that the following timed out, also after serialization" in {
      val update = A2aRemoteTasks.followTimedOutUpdate("t1", "c1", java.time.Duration.ofHours(24))
      update.followTimedOut() shouldBe true
      update.status().state() shouldBe A2aTaskState.UNSPECIFIED
      update.status().message().parts().get(0).asInstanceOf[A2aPart.Text].text() should include("1 day")

      val mapper = JsonSupport.getObjectMapper
      val read = mapper.readValue(mapper.writeValueAsBytes(update), classOf[A2aTaskUpdate])
      read.asInstanceOf[A2aTaskStatusUpdate].followTimedOut() shouldBe true
      new A2aTaskStatusUpdate("t1", "c1", update.status(), null).followTimedOut() shouldBe false
    }
  }
}
