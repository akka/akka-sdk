/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.time.Duration
import java.util.Optional

import akka.javasdk.agent.task.TaskAttachment
import akka.javasdk.agent.task.TaskEntity
import akka.javasdk.agent.task.TaskEvent
import akka.javasdk.agent.task.TaskState
import akka.javasdk.agent.task.TaskStatus
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi.BytesPayload
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TaskExpirySpec extends AnyWordSpec with Matchers {

  private def configWith(value: String) =
    ConfigFactory.parseString(s"akka.javasdk.agent.task.expire-after = $value")

  private val serializer: Serializer = {
    val s = new Serializer
    s.registerTypeHints(classOf[TaskEvent.TaskCreated])
    s
  }

  private def payload(contentType: String, json: String) =
    new BytesPayload(ByteString(json), contentType)

  "TaskExpiry.defaultExpireAfter" should {

    "be None for off in any case" in {
      TaskExpiry.defaultExpireAfter(configWith("off")) shouldBe None
      TaskExpiry.defaultExpireAfter(configWith("OFF")) shouldBe None
    }

    "be None for the default of reference.conf" in {
      TaskExpiry.defaultExpireAfter(ConfigFactory.load()) shouldBe None
    }

    "give a positive duration" in {
      TaskExpiry.defaultExpireAfter(configWith("30 days")) shouldBe Some(Duration.ofDays(30))
    }

    "throw for a duration that is not positive, or not a duration" in {
      Seq("0s", "-1s", "foo").foreach { value =>
        val ex = intercept[IllegalArgumentException](TaskExpiry.defaultExpireAfter(configWith(value)))
        ex.getMessage should include("akka.javasdk.agent.task.expire-after")
        ex.getMessage should include(s"[$value]")
      }
    }
  }

  "The stored task shapes" should {

    // The JSON that the release before expireAfter writes.

    "read a TaskCreated without expireAfter as null" in {
      val json =
        """{"taskId":"task-1","name":"research","description":"Research a topic","instructions":"Find sources","resultTypeName":"java.lang.String","dependencyTaskIds":[],"attachments":[],"ruleClassNames":[]}"""
      val event = serializer.fromBytes(payload("json.akka.io/akka-task-created", json))
      event shouldBe a[TaskEvent.TaskCreated]
      val created = event.asInstanceOf[TaskEvent.TaskCreated]
      created.taskId() shouldBe "task-1"
      created.name() shouldBe "research"
      created.expireAfter() shouldBe null
    }

    "read a CreateRequest without expireAfter as null" in {
      val json =
        """{"name":"research","description":"Research a topic","instructions":"Find sources","resultTypeName":"java.lang.String","dependencyTaskIds":[],"attachments":[],"ruleClassNames":[]}"""
      val request = serializer.fromBytes(
        classOf[TaskEntity.CreateRequest],
        payload("json.akka.io/akka.javasdk.agent.task.TaskEntity$CreateRequest", json))
      request.name() shouldBe "research"
      request.expireAfter() shouldBe null
    }

    "read a TaskState without expireAfter as empty" in {
      val json =
        """{"taskId":"task-1","name":"research","description":"Research a topic","instructions":"Find sources","status":"COMPLETED","resultTypeName":"java.lang.String","result":"done","failureReason":null,"dependencyTaskIds":[],"assignee":"agent-1","attachments":[],"reassignmentContext":[],"ruleClassNames":[]}"""
      val state =
        serializer.fromBytes(classOf[TaskState], payload("json.akka.io/akka.javasdk.agent.task.TaskState", json))
      state.status() shouldBe TaskStatus.COMPLETED
      state.result() shouldBe Optional.of("done")
      state.expireAfter() shouldBe Optional.empty()
    }

    "round trip with a duration" in {
      val oneHour = Duration.ofHours(1)
      val noItems = java.util.List.of[String]()
      val noAttachments = java.util.List.of[TaskAttachment]()

      val created = new TaskEvent.TaskCreated(
        "task-1",
        "research",
        "Research a topic",
        "Find sources",
        "java.lang.String",
        noItems,
        noAttachments,
        noItems,
        oneHour)
      val createdBytes = serializer.toBytes(created)
      createdBytes.contentType shouldBe "json.akka.io/akka-task-created"
      serializer.fromBytes(createdBytes) shouldBe created

      val request = new TaskEntity.CreateRequest(
        "research",
        "Research a topic",
        "Find sources",
        "java.lang.String",
        noItems,
        noAttachments,
        noItems,
        oneHour)
      serializer.fromBytes(classOf[TaskEntity.CreateRequest], serializer.toBytes(request)) shouldBe request

      val state = new TaskState(
        "task-1",
        "research",
        "Research a topic",
        "Find sources",
        TaskStatus.COMPLETED,
        "java.lang.String",
        Optional.of("done"),
        Optional.empty(),
        noItems,
        Optional.of("agent-1"),
        noAttachments,
        noItems,
        noItems,
        Optional.of(oneHour))
      serializer.fromBytes(classOf[TaskState], serializer.toBytes(state)) shouldBe state
    }
  }
}
