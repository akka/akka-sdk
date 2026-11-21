/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import java.time.Duration

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.model.headers.RawHeader
import akka.javasdk.a2a._
import akka.javasdk.agent.RemoteA2aAgent
import akka.javasdk.agent.ToolCallGuardrail
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class A2aAgentToolsSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with LogCapturing {

  private val server = new FakeA2aServer
  private val binding = Await.result(server.start(), 10.seconds)

  override protected def afterAll(): Unit = {
    binding.unbind()
    super.afterAll()
  }

  private def skill(id: String, description: String) =
    new A2aAgentSkill(id, s"$id name", description, null, null, null, null, null)

  private def a2aConfig: Config =
    ConfigFactory
      .parseMap(Map("remote-agents.claims.card-url" -> server.cardUrl).asJava)
      .withFallback(system.settings.config.getConfig("akka.javasdk.a2a"))

  private def client = A2aClientImpl(new A2aRemoteAgents(system, a2aConfig), None, None)

  // A client with no configured agents, that reaches the service "claims-service" at the fake server
  private def unconfiguredClient =
    A2aClientImpl(
      new A2aRemoteAgents(
        system,
        system.settings.config.getConfig("akka.javasdk.a2a"),
        _ => A2aServiceRoute(server.baseUrl, None, Seq(RawHeader("X-Service-Identity", "caller-service")))),
      None,
      Some("caller-component"))

  private def reset(): Unit = {
    server.seen.clear()
    server.skills = Seq(skill("check-claim", "Checks a claim"), skill("pay", "Pays a claim"))
    server.sendReply = None
  }

  private def textMessage(texts: String*) =
    new A2aMessage("m", null, null, A2aRole.AGENT, texts.map(t => A2aPart.text(t): A2aPart).asJava, null, null, null)

  private def call(
      tool: A2aAgentTools.A2aTool,
      arguments: String,
      contextId: String = "session-1",
      client: A2aClientImpl = client): String =
    Await.result(
      A2aAgentTools.call(tool, arguments, contextId, "agent", writesSessionMemory = true, client)(
        system.executionContext,
        system.classicSystem.scheduler),
      10.seconds)

  "The tool name of a skill" should {

    "join the agent name and the skill id" in {
      A2aAgentTools.toolName("claims", "check-claim") shouldBe "claims_check-claim"
    }

    "replace characters the model providers refuse" in {
      A2aAgentTools.toolName("Claims Agent", "check.claim/v2") shouldBe "Claims_Agent_check_claim_v2"
    }

    "start with a letter or an underscore" in {
      A2aAgentTools.toolName("42agent", "skill") shouldBe "a2a_42agent_skill"
    }

    "stay within 64 characters and distinct when cut" in {
      val a = A2aAgentTools.toolName("agent", "s" * 80 + "a")
      val b = A2aAgentTools.toolName("agent", "s" * 80 + "b")
      a.length shouldBe 64
      b.length shouldBe 64
      a should not be b
      A2aAgentTools.toolName("agent", "s" * 80 + "a") shouldBe a
    }
  }

  "The tools of a remote agent" should {

    "be none, with a warning, for an agent whose Agent Card cannot be read" in {
      reset()
      val agents =
        Seq(RemoteA2aAgent.fromCardUrl(s"${server.baseUrl}/missing/card.json"), RemoteA2aAgent.named("claims"))
      val tools = akka.actor.testkit.typed.scaladsl.LoggingTestKit
        .warn("could not be read, so its skills are not offered as tools")
        .expect(A2aAgentTools.toolsFor(agents, client))
      tools.map(_.name) shouldBe Seq("claims_check-claim", "claims_pay")
    }

    "be one for each skill on the Agent Card" in {
      reset()
      val tools = A2aAgentTools.toolsFor(Seq(RemoteA2aAgent.named("claims")), client)
      tools.map(_.name) shouldBe Seq("claims_check-claim", "claims_pay")
      val descriptor = tools.head.descriptor
      descriptor.description should include("Checks a claim")
      descriptor.schema.required shouldBe Seq("message")
      descriptor.schema.properties.keySet shouldBe Set("message", "data")
    }

    "be only the allowed skills" in {
      reset()
      val tools =
        A2aAgentTools.toolsFor(Seq(RemoteA2aAgent.named("claims").withAllowedSkills(Set("pay").asJava)), client)
      tools.map(_.skillId) shouldBe Seq("pay")
    }

    "be named after the card for an agent given by card URL that is not configured" in {
      reset()
      val tools = A2aAgentTools.toolsFor(Seq(RemoteA2aAgent.fromCardUrl(server.cardUrl)), unconfiguredClient)
      tools.map(_.name) shouldBe Seq("fake_check-claim", "fake_pay")
    }

    "be named after the configuration with the same card URL for an agent given by card URL" in {
      reset()
      val byUrl = A2aAgentTools.toolsFor(Seq(RemoteA2aAgent.fromCardUrl(server.cardUrl)), client)
      val byName = A2aAgentTools.toolsFor(Seq(RemoteA2aAgent.named("claims")), client)
      byUrl.map(_.name) shouldBe byName.map(_.name)
      byUrl.map(_.origin) shouldBe byName.map(_.origin)
    }

    "carry the remote agent's name, its card URL and the skill as their origin" in {
      reset()
      val tool = A2aAgentTools.toolsFor(Seq(RemoteA2aAgent.named("claims")), client).head
      tool.origin shouldBe new ToolCallGuardrail.ToolOrigin.RemoteA2a("claims", server.cardUrl, "check-claim")
    }

    "reach another Akka service by its name, with the identity of the caller" in {
      reset()
      val tools = A2aAgentTools.toolsFor(Seq(RemoteA2aAgent.fromService("claims-service")), unconfiguredClient)
      tools.map(_.name) shouldBe Seq("fake_check-claim", "fake_pay")
      tools.head.origin shouldBe new ToolCallGuardrail.ToolOrigin.RemoteA2a("fake", server.cardUrl, "check-claim")
      call(tools.head, """{"message":"check"}""", client = unconfiguredClient)
      val sent = server.requests.filter(_.path == "/a2a").last
      sent.headers("x-service-identity") shouldBe "caller-service"
      sent.headers("x-akka-caller") shouldBe "caller-component"
    }

    "offer the skills named as varargs" in {
      reset()
      val tools =
        A2aAgentTools.toolsFor(Seq(RemoteA2aAgent.named("claims").withAllowedSkills("pay", "check-claim")), client)
      tools.map(_.skillId) shouldBe Seq("check-claim", "pay")
    }

    "be rejected when a name clashes with another tool" in {
      reset()
      val tools = A2aAgentTools.toolsFor(Seq(RemoteA2aAgent.named("claims")), client)
      val e = intercept[IllegalArgumentException](A2aAgentTools.validateNames(tools, Seq("claims_pay")))
      e.getMessage should include("claims_pay")
      A2aAgentTools.validateNames(tools, Seq("other"))
    }
  }

  "A tool call" should {

    "send the message and the data to the skill in the session's context and return the reply" in {
      reset()
      @volatile var params: com.fasterxml.jackson.databind.JsonNode = null
      server.sendReply = Some { p =>
        params = p
        val reply = new A2aMessage(
          "r",
          null,
          null,
          A2aRole.AGENT,
          java.util.List.of(A2aPart.text("claim 42 is valid"), A2aPart.data(java.util.Map.of("valid", true))),
          null,
          null,
          null)
        Right(A2aSendMessageResponse.of(reply))
      }
      val tool = A2aAgentTools.toolsFor(Seq(RemoteA2aAgent.named("claims")), client).head

      call(tool, """{"message":"check claim 42","data":{"claim":42}}""") shouldBe "claim 42 is valid\n{\"valid\":true}"

      params.path("metadata").path("skillId").asText() shouldBe "check-claim"
      params.path("message").path("contextId").asText() shouldBe "session-1"
      val parts = params.path("message").path("parts")
      parts.get(0).path("text").asText() shouldBe "check claim 42"
      parts.get(1).path("data").path("claim").asInt() shouldBe 42
    }

    "return the artifacts of a completed task" in {
      reset()
      server.sendReply = Some { _ =>
        val artifact = new A2aArtifact("a", null, null, java.util.List.of(A2aPart.text("paid")), null, null)
        Right(
          A2aSendMessageResponse.of(
            new A2aTask(
              "t-1",
              "session-1",
              new A2aTaskStatus(A2aTaskState.COMPLETED, null, null),
              java.util.List.of(artifact),
              null,
              null)))
      }
      val tool = A2aAgentTools.toolsFor(Seq(RemoteA2aAgent.named("claims")), client).last
      call(tool, """{"message":"pay claim 42"}""") shouldBe "paid"
    }

    "return the id and the state of a task that is still working" in {
      reset()
      server.sendReply = Some { _ =>
        Right(
          A2aSendMessageResponse.of(
            new A2aTask(
              "t-2",
              "session-1",
              new A2aTaskStatus(A2aTaskState.WORKING, textMessage("checking"), null),
              null,
              null,
              null)))
      }
      val tool = A2aAgentTools.toolsFor(Seq(RemoteA2aAgent.named("claims")), client).head
      call(tool, """{"message":"check"}""") shouldBe "The remote task [t-2] is in state TASK_STATE_WORKING: checking"
    }

    "return a failed task as an error" in {
      reset()
      server.sendReply = Some { _ =>
        Right(
          A2aSendMessageResponse.of(
            new A2aTask(
              "t-3",
              "session-1",
              new A2aTaskStatus(A2aTaskState.FAILED, textMessage("no such claim"), null),
              null,
              null,
              null)))
      }
      val tool = A2aAgentTools.toolsFor(Seq(RemoteA2aAgent.named("claims")), client).head
      call(tool, """{"message":"check"}""") shouldBe
      "Error: the remote task [t-3] ended in state TASK_STATE_FAILED: no such claim"
    }

    "return an error reply of the remote agent as text" in {
      reset()
      server.sendReply = Some(_ => Left(JsonRpcError.of(A2aErrorCode.INVALID_PARAMS, "unknown claim", null)))
      val tool = A2aAgentTools.toolsFor(Seq(RemoteA2aAgent.named("claims")), client).head
      val reply = call(tool, """{"message":"check"}""")
      reply should startWith("Error: ")
      reply should include("unknown claim")
    }

    "return a timeout as text" in {
      reset()
      server.sendReply = Some { _ =>
        Thread.sleep(1500)
        Right(A2aSendMessageResponse.of(textMessage("late")))
      }
      val agent = RemoteA2aAgent.named("claims").withTimeout(Duration.ofMillis(300))
      val tool = A2aAgentTools.toolsFor(Seq(agent), client).head
      call(tool, """{"message":"check"}""") should (startWith("Error: ") and include("did not answer"))
    }

    "return arguments without a message as an error" in {
      reset()
      val tool = A2aAgentTools.toolsFor(Seq(RemoteA2aAgent.named("claims")), client).head
      call(tool, """{"data":{}}""") should (startWith("Error: ") and include("message"))
      server.requests.filter(_.path == "/a2a") shouldBe empty
    }
  }

  "A remote agent" should {

    "refuse a service name that is not a bare name" in {
      intercept[IllegalArgumentException](RemoteA2aAgent.fromService("claims.example.com"))
    }

    "refuse a card URL that is not HTTP" in {
      intercept[IllegalArgumentException](RemoteA2aAgent.fromCardUrl("ftp://example.com/card.json"))
    }

    "refuse a timeout that is not positive" in {
      intercept[IllegalArgumentException](RemoteA2aAgent.named("claims").withTimeout(Duration.ZERO))
    }
  }
}
