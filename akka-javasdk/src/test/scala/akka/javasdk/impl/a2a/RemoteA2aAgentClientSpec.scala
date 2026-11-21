/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.model.headers.Authorization
import akka.javasdk.a2a._
import akka.stream.javadsl.Sink
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class RemoteA2aAgentClientSpec
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

  private def a2aConfig(settings: (String, String)*): Config =
    ConfigFactory
      .parseMap(settings.map { case (k, v) => s"remote-agents.agent.$k" -> v }.toMap.asJava)
      .withFallback(system.settings.config.getConfig("akka.javasdk.a2a"))

  private def client(settings: (String, String)*)(
      caller: Option[String] = None,
      cardUrl: String = server.cardUrl): RemoteA2aAgentClient =
    A2aClientImpl(new A2aRemoteAgents(system, a2aConfig(("card-url" -> cardUrl) +: settings: _*)), None, caller)
      .forAgent("agent")

  private def reset(): Unit = {
    server.seen.clear()
    server.cardRequests.set(0)
    server.tokenRequests.set(0)
    server.bindings = Seq("JSONRPC", "HTTP+JSON")
    server.authorized = _ => true
  }

  private def hello = new A2aMessage(null, null, null, null, java.util.List.of(A2aPart.text("hello")), null, null, null)

  private def taskOf(response: A2aSendMessageResponse): A2aTask =
    response.asInstanceOf[A2aSendMessageResponse.Task].task()

  private def collect(source: akka.stream.javadsl.Source[A2aStreamResponse, _]): Seq[A2aStreamResponse] =
    Await.result(source.runWith(Sink.seq[A2aStreamResponse], system).asScala, 10.seconds).asScala.toSeq

  private val bindings = Seq("JSONRPC" -> "JSON-RPC", "HTTP+JSON" -> "HTTP+JSON")

  "The remote agent client" should {

    "fetch the Agent Card and cache it until the refresh time" in {
      reset()
      val c = client("card-refresh" -> "500ms")()
      c.card().invoke().name() shouldBe "fake"
      c.card().invoke().name() shouldBe "fake"
      server.cardRequests.get() shouldBe 1
      Thread.sleep(700)
      c.card().invoke()
      server.cardRequests.get() shouldBe 2
    }

    "share the card between the clients of different components" in {
      reset()
      val agents = new A2aRemoteAgents(system, a2aConfig("card-url" -> server.cardUrl))
      A2aClientImpl(agents, None, Some("a")).forAgent("agent").card().invoke()
      A2aClientImpl(agents, None, Some("b")).forAgent("agent").card().invoke()
      server.cardRequests.get() shouldBe 1
    }

    "reach an agent given by card URL" in {
      reset()
      val agents = new A2aRemoteAgents(system, a2aConfig())
      A2aClientImpl(agents, None, None).forCardUrl(server.cardUrl).card().invoke().name() shouldBe "fake"
    }

    "refuse defaults that hold credentials" in {
      val config = ConfigFactory
        .parseString("""defaults.auth { type = header, header-name = X, header-value = v }""")
        .withFallback(a2aConfig())
      intercept[IllegalArgumentException](new A2aRemoteAgents(system, config)).getMessage should include(
        "defaults.auth.type must be none")
    }

    "keep the most recently used agents given by URL, up to a limit" in {
      val agents = new A2aRemoteAgents(system, a2aConfig())
      val first = agents.stateFor(A2aRemoteTarget.CardUrl("https://agent-0.example.com/card.json"))
      val evicted = agents.stateFor(A2aRemoteTarget.CardUrl("https://agent-1.example.com/card.json"))
      (2 to A2aRemoteAgents.MaxAgentsByUrl + 5).foreach { i =>
        agents.stateFor(A2aRemoteTarget.CardUrl(s"https://agent-$i.example.com/card.json"))
        agents.stateFor(A2aRemoteTarget.CardUrl("https://agent-0.example.com/card.json"))
      }
      agents.agentsByUrl shouldBe A2aRemoteAgents.MaxAgentsByUrl
      (agents.stateFor(A2aRemoteTarget.CardUrl("https://agent-0.example.com/card.json")) should be)
        .theSameInstanceAs(first)
      (agents.stateFor(A2aRemoteTarget.CardUrl("https://agent-1.example.com/card.json")) should not)
        .be(theSameInstanceAs(evicted))
    }

    "discard a response that arrives after the timeout" in {
      implicit val ec: scala.concurrent.ExecutionContext = system.executionContext
      implicit val materializer: akka.stream.Materializer = akka.stream.SystemMaterializer(system).materializer
      val drained = new java.util.concurrent.CountDownLatch(1)
      val late = scala.concurrent.Promise[akka.http.scaladsl.model.HttpResponse]()
      val result = RemoteA2aAgentClientImpl.within(
        late.future,
        100.millis,
        system.classicSystem.scheduler,
        new A2aClientException("timed out"))
      intercept[A2aClientException](Await.result(result, 5.seconds)).getMessage shouldBe "timed out"
      late.success(
        akka.http.scaladsl.model.HttpResponse(entity = akka.http.scaladsl.model.HttpEntity(
          akka.http.scaladsl.model.ContentTypes.`application/octet-stream`,
          akka.stream.scaladsl.Source.single(akka.util.ByteString("x")).map { b => drained.countDown(); b })))
      drained.await(5, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
    }

    "reject a name that is not configured" in {
      intercept[IllegalArgumentException](
        A2aClientImpl(new A2aRemoteAgents(system, a2aConfig()), None, None)
          .forAgent("nobody")).getMessage should include("[nobody]")
    }

    "fail with a client exception when the Agent Card answers with an error" in {
      reset()
      val e =
        intercept[A2aClientException](
          client()(cardUrl = server.cardUrl.replace("agent-card.json", "token")).card().invoke())
      e.getMessage should include("404")
    }

    "fail with a client exception when the agent is not reachable" in {
      val e = intercept[A2aClientException](client()(cardUrl = "http://127.0.0.1:1/card.json").card().invoke())
      e.getMessage should include("[agent]")
    }

  }

  bindings.foreach { case (wire, label) =>
    s"The remote agent client over the $label binding" should {
      def only = {
        reset()
        server.bindings = Seq(wire)
        client()()
      }

      "send a message in a context, with the skill" in {
        val c = only
        val reply = c.send("claims", hello).inContext("ctx-1").invoke()
        taskOf(reply).contextId() shouldBe "ctx-1"
        taskOf(reply).status().state() shouldBe A2aTaskState.COMPLETED
        taskOf(reply).metadata().get("skillId") shouldBe "claims"
      }

      "send a message asynchronously and read the context of the message" in {
        val c = only
        val message = A2aMessage.user("hello").withMessageId("m1").withContextId("ctx-msg")
        val reply = c.send(message).invokeAsync().toCompletableFuture.get()
        taskOf(reply).contextId() shouldBe "ctx-msg"
        taskOf(reply).metadata() shouldBe null
      }

      "stream a message" in {
        val c = only
        val events = collect(c.stream("claims", hello))
        events.map {
          case s: A2aStreamResponse.StatusUpdate => Some(s.statusUpdate().status().state())
          case t: A2aStreamResponse.Task         => Some(t.task().status().state())
          case _                                 => None
        } shouldBe Seq(Some(A2aTaskState.WORKING), Some(A2aTaskState.COMPLETED))
      }

      "get a task" in {
        val c = only
        val task = c.getTask("task-9").invoke()
        task.id() shouldBe "task-9"
        task.status().state() shouldBe A2aTaskState.WORKING
      }

      "cancel a task" in {
        val c = only
        c.cancel("task-9").invoke().status().state() shouldBe A2aTaskState.CANCELED
      }

      "map a task that does not exist to a remote error" in {
        val c = only
        val e = intercept[A2aRemoteErrorException](c.getTask("missing").invoke())
        e.code() shouldBe -32001
        e.reason() shouldBe java.util.Optional.of("TASK_NOT_FOUND")
      }
    }
  }

  "The remote agent client" should {

    "prefer JSON-RPC when the card lists both bindings" in {
      reset()
      client()().getTask("task-1").invoke()
      server.requests.map(_.path) should contain("/a2a")
      server.requests.map(_.path) should not contain "/rest/tasks/task-1"
    }

    "send the A2A version and the caller on every request" in {
      reset()
      client()(caller = Some("/akka/component/agent/MyAgent")).send("s", hello).invoke()
      server.requests.size shouldBe 2
      server.requests.foreach { seen =>
        seen.headers("a2a-version") shouldBe "1.0"
        seen.headers("x-akka-caller") shouldBe "/akka/component/agent/MyAgent"
      }
    }

    "leave the caller header out when the caller is not known" in {
      reset()
      client()().card().invoke()
      server.requests.head.headers.keySet should not contain "x-akka-caller"
    }

    "send a header credential on the card and on calls" in {
      reset()
      server.authorized = _.headers.exists(h => h.name == "X-Api-Key" && h.value == "abc")
      val c = client("auth.type" -> "header", "auth.header-name" -> "X-Api-Key", "auth.header-value" -> "abc")()
      taskOf(c.send("s", hello).invoke()) should not be null
      intercept[A2aClientException](client()().card().invoke()).getMessage should include("401")
    }

    "send an OAuth token and reuse it" in {
      reset()
      server.authorized = _.header[Authorization].exists(_.value == "Bearer tok-1")
      val c = client(oauth(): _*)()
      c.send("s", hello).invoke()
      c.getTask("t").invoke()
      server.tokenRequests.get() shouldBe 1
    }

    "request a new token once when the agent rejects the token" in {
      reset()
      server.authorized = _.header[Authorization].exists(_.value == "Bearer tok-2")
      val c = client(oauth(): _*)()
      taskOf(c.send("s", hello).invoke()) should not be null
      server.tokenRequests.get() shouldBe 2
    }

    "give up when the agent rejects the new token too" in {
      reset()
      server.authorized = _ => false
      val c = client(oauth(): _*)()
      intercept[A2aClientException](c.card().invoke()).getMessage should include("401")
      server.tokenRequests.get() shouldBe 2
    }

    "not send credentials to a plain http URL that the Agent Card names" in {
      reset()
      val agents = new A2aRemoteAgents(
        system,
        a2aConfig(
          "card-url" -> server.cardUrl,
          "auth.type" -> "header",
          "auth.header-name" -> "X-Api-Key",
          "auth.header-value" -> "abc"))
      val c = new RemoteA2aAgentClientImpl(
        agents.stateFor(A2aRemoteTarget.Named("agent")),
        Nil,
        _ => A2aBinding.JsonRpc("http://agent.example.com/a2a", None))
      val e = intercept[A2aClientException](c.send("s", hello).invoke())
      e.getMessage should include("http://agent.example.com/a2a")
      e.getMessage should include("only to https://")
      server.requests.map(_.path) shouldBe Seq("/.well-known/agent-card.json")
    }

    "fail with a client exception when the token endpoint refuses the client" in {
      reset()
      val c = client(oauth("auth.token-url" -> server.cardUrl): _*)()
      intercept[A2aClientException](c.card().invoke()).getMessage should include("405")
    }
  }

  "The remote agent client of an agent that requires a signed card" should {

    val keys = {
      val generator = java.security.KeyPairGenerator.getInstance("EC")
      generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"))
      generator.generateKeyPair()
    }
    val publicKeyPem =
      "-----BEGIN PUBLIC KEY-----\n" +
      java.util.Base64.getMimeEncoder().encodeToString(keys.getPublic.getEncoded) +
      "\n-----END PUBLIC KEY-----"
    val signer = new AgentCardSigning.Signer(keys.getPrivate, "k1", "ES256", null)
    def signed(card: A2aAgentCard): A2aAgentCard = AgentCardSigning.sign(card, signer)
    def altered(card: A2aAgentCard): A2aAgentCard = {
      val s = signed(card)
      new A2aAgentCard(
        s.name(),
        "Changed after signing",
        s.supportedInterfaces(),
        s.provider(),
        s.version(),
        s.documentationUrl(),
        s.capabilities(),
        s.securitySchemes(),
        s.securityRequirements(),
        s.defaultInputModes(),
        s.defaultOutputModes(),
        s.skills(),
        s.signatures(),
        s.iconUrl())
    }
    def withKey = client("require-signed-card" -> "true", "card-signing-key" -> publicKeyPem)()

    "accept a card signed by the configured key" in {
      reset()
      server.cardTransform = signed
      try withKey.card().invoke().name() shouldBe "fake"
      finally server.cardTransform = identity
    }

    "refuse a card that was changed after it was signed, and send nothing to the agent" in {
      reset()
      server.cardTransform = altered
      try {
        val e = intercept[A2aClientException](withKey.send("s", hello).invoke())
        e.getMessage should include("not signed by a trusted key")
        server.requests.map(_.path) shouldBe Seq("/.well-known/agent-card.json")
      } finally server.cardTransform = identity
    }

    "refuse a card that is not signed" in {
      reset()
      intercept[A2aClientException](withKey.card().invoke()).getMessage should include("no signatures")
    }

    "accept a card signed by a key in the configured key set" in {
      reset()
      val point = keys.getPublic.asInstanceOf[java.security.interfaces.ECPublicKey].getW
      def coordinate(value: java.math.BigInteger): String = {
        val bytes = value.toByteArray.dropWhile(_ == 0)
        java.util.Base64.getUrlEncoder.withoutPadding().encodeToString(Array.fill[Byte](32 - bytes.length)(0) ++ bytes)
      }
      server.jwks = s"""{"keys":[{"kty":"EC","crv":"P-256","kid":"k1","x":"${coordinate(
        point.getAffineX)}","y":"${coordinate(point.getAffineY)}"}]}"""
      server.cardTransform = signed
      try client("require-signed-card" -> "true", "card-jwks-url" -> s"${server.baseUrl}/jwks.json")()
        .card()
        .invoke()
        .name() shouldBe "fake"
      finally server.cardTransform = identity
    }
  }

  private def oauth(extra: (String, String)*): Seq[(String, String)] =
    Seq(
      "auth.type" -> "oauth2-client-credentials",
      "auth.token-url" -> server.tokenUrl,
      "auth.client-id" -> "id",
      "auth.client-secret" -> "secret") ++ extra
}
