/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.time.Instant
import java.util
import java.util.Optional

import scala.jdk.CollectionConverters._

import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.javasdk.agent.AgentRegistry
import akka.javasdk.agent.MemoryFilter
import akka.javasdk.agent.SessionMemoryEntity
import akka.javasdk.agent.SessionMessage.AiMessage
import akka.javasdk.agent.SessionMessage.TokenUsage
import akka.javasdk.agent.SessionMessage.UserMessage
import akka.javasdk.impl.agent.SessionMemoryClient.MemorySettings
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.MemoryClient
import akka.runtime.sdk.spi.MemoryContextRequest
import akka.stream.SystemMaterializer
import akka.stream.scaladsl.Source
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

object SessionMemoryClientSpec {

  /** Captures the request the client passed to the runtime, and returns a canned event stream. */
  final class FakeMemoryClient(events: Vector[SessionMemoryEntity.Event], serializer: Serializer) extends MemoryClient {
    @volatile var lastRequest: Option[MemoryContextRequest] = None

    override def fetchStream(request: MemoryContextRequest): Source[BytesPayload, NotUsed] = {
      lastRequest = Some(request)
      Source(events.map(serializer.toBytesAsJson))
    }
  }

  private val ts: Instant = Instant.parse("2026-01-01T00:00:00Z")

  private def userEvent(componentId: String, text: String): SessionMemoryEntity.Event =
    new SessionMemoryEntity.Event.UserMessageAdded(ts, componentId, text, text.length)

  private def aiEvent(componentId: String, text: String): SessionMemoryEntity.Event =
    new SessionMemoryEntity.Event.AiMessageAdded(
      ts,
      componentId,
      text,
      text.length,
      0L,
      java.util.Collections.emptyList(),
      Optional.empty(),
      Optional.of(TokenUsage.EMPTY),
      java.util.Collections.emptyMap())
}

class SessionMemoryClientSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers {
  import SessionMemoryClientSpec._

  private val serializer = new Serializer
  private val emptyRegistry: AgentRegistry = AgentRegistryImpl.fromJavaSet(java.util.Set.of())

  private def newClient(
      fakeMemoryClient: FakeMemoryClient,
      settings: MemorySettings = new MemorySettings(true, true, Optional.empty(), util.List.of()),
      registry: AgentRegistry = emptyRegistry): SessionMemoryClient =
    new SessionMemoryClient(
      /* componentClient = */ null, // not exercised on the fallback path
      fakeMemoryClient,
      serializer,
      registry,
      SystemMaterializer(system).materializer,
      settings)

  "SessionMemoryClient.fetchHistoryFromJournal" should {

    "stream from MemoryClient and decode entity events into SessionMessages" in {
      val events = Vector(userEvent("agent-1", "Hello"), aiEvent("agent-1", "Hi there!"))
      val fake = new FakeMemoryClient(events, serializer)
      val client = newClient(fake)

      val result = client.fetchHistoryFromJournal("session-42", 0L)

      val msgs = result.messages().asScala.toVector
      msgs should have size 2
      msgs.head shouldBe a[UserMessage]
      msgs(1) shouldBe a[AiMessage]
      msgs.head.asInstanceOf[UserMessage].text() shouldBe "Hello"
      msgs(1).asInstanceOf[AiMessage].text() shouldBe "Hi there!"
    }

    "build the persistence id from SESSION_MEMORY_COMPONENT_ID and the session id, and pass fromSequenceNr through unchanged" in {
      val fake = new FakeMemoryClient(Vector.empty, serializer)
      val client = newClient(fake)

      client.fetchHistoryFromJournal("session-42", 11L)

      val req = fake.lastRequest.getOrElse(fail("MemoryClient.fetchStream was never called"))
      req.sessionId shouldBe s"${SessionMemoryEntity.SESSION_MEMORY_COMPONENT_ID}|session-42"
      req.fromSequenceNr shouldBe 11L
    }

    "apply the configured filters to the streamed messages" in {
      val events = Vector(
        userEvent("agent-1", "Hello"),
        aiEvent("agent-1", "Hi from 1"),
        userEvent("agent-2", "Hello again"),
        aiEvent("agent-2", "Hi from 2"))
      val fake = new FakeMemoryClient(events, serializer)
      val excludeAgent2: util.List[MemoryFilter] = MemoryFilter.excludeFromAgentId("agent-2").get()
      val settings = new MemorySettings(true, true, Optional.empty(), excludeAgent2)
      val client = newClient(fake, settings)

      val result = client.fetchHistoryFromJournal("s", 0L)

      val msgs = result.messages().asScala.toVector
      msgs should have size 2
      msgs.foreach(_.componentId() shouldBe "agent-1")
    }

    "trim to the configured lastN messages after filtering" in {
      val events = Vector(userEvent("a", "u1"), aiEvent("a", "a1"), userEvent("a", "u2"), aiEvent("a", "a2"))
      val fake = new FakeMemoryClient(events, serializer)
      val settings = new MemorySettings(true, true, Optional.of(2), util.List.of())
      val client = newClient(fake, settings)

      val result = client.fetchHistoryFromJournal("s", 0L)

      val msgs = result.messages().asScala.toVector
      msgs should have size 2
      msgs.head.asInstanceOf[UserMessage].text() shouldBe "u2"
      msgs(1).asInstanceOf[AiMessage].text() shouldBe "a2"
    }
  }
}
