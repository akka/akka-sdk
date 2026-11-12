/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.decision

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

import akka.javasdk.agent.InternalServerException
import akka.javasdk.agent.ModelException
import akka.javasdk.agent.ModelTimeoutException
import akka.javasdk.agent.RateLimitException
import akka.javasdk.decision.Answer
import akka.javasdk.decision.Decision
import akka.javasdk.decision.DecisionModelProvider
import akka.javasdk.decision.Question
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiDecision
import akka.runtime.sdk.spi.SpiDecisionModelClient
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.opentelemetry.context.Context
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

object DecisionModelClientImplSpec {

  final class FakeSpiClient(respond: SpiDecision.Request => Future[SpiDecision.Response])
      extends SpiDecisionModelClient {
    @volatile var provider: SpiDecision.ModelProvider = _
    @volatile var request: SpiDecision.Request = _

    override def decide(
        provider: SpiDecision.ModelProvider,
        request: SpiDecision.Request,
        telemetryContext: Option[Context]): Future[SpiDecision.Response] = {
      this.provider = provider
      this.request = request
      respond(request)
    }
  }

  val config: Config = ConfigFactory.load(ConfigFactory.parseString("""
    akka.javasdk.decision {
      model-provider = system-one
      system-one {
        api-key = "test-key"
        model-name = "jev-1"
      }
      other = ${akka.javasdk.decision.system-one}
      other.model-name = "jev-2"
    }
    """))
}

class DecisionModelClientImplSpec extends AnyWordSpec with Matchers with ScalaFutures {
  import DecisionModelClientImplSpec._

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = Span.convertDurationToSpan(3.seconds))
  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val route =
    Question.choice("Which team?").option("billing", "Payments").option("technical", "Bugs").build("route")
  private val severity = Question.score("How severe?").levels("low", "medium", "high").build("severity")
  private val urgent = Question.yesNo("Urgent?").whenYes("Deadline today").build("urgent")

  private val allAnswers =
    new SpiDecision.Response(
      "jev-1-2026",
      Map(
        "route" -> new SpiDecision.ChoiceAnswer("technical", Map("technical" -> 0.8, "billing" -> 0.2), 0.6),
        "severity" -> new SpiDecision.ScoreAnswer(1.5, Seq(0.1, 0.3, 0.6), 0.4),
        "urgent" -> new SpiDecision.PredicateAnswer(0.9)),
      inputTokenCount = 10,
      outputTokenCount = 3)

  private def client(
      spi: SpiDecisionModelClient,
      config: Config = DecisionModelClientImplSpec.config,
      overrideProvider: OverrideDecisionModelProvider = new OverrideDecisionModelProvider) =
    new DecisionModelClientImpl(spi, new JsonSerializer, config, overrideProvider, None, None)

  "The decision model client" should {

    "send the input as JSON and the questions to the configured provider" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      client(spi).input(new Ticket("Refund", "Charged twice")).questions(route, severity, urgent).invoke()

      spi.request.stateJson shouldBe """{"subject":"Refund","body":"Charged twice"}"""
      spi.request.questions.map(_.key) shouldBe Seq("route", "severity", "urgent")

      val choice = spi.request.questions.head.asInstanceOf[SpiDecision.ChoiceQuestion]
      choice.instructions shouldBe "Which team?"
      choice.options.map(o => o.key -> o.description) shouldBe Seq("billing" -> "Payments", "technical" -> "Bugs")
      spi.request.questions(1).asInstanceOf[SpiDecision.ScoreQuestion].levels shouldBe Seq("low", "medium", "high")
      val yesNo = spi.request.questions(2).asInstanceOf[SpiDecision.PredicateQuestion]
      yesNo.whenTrue shouldBe Some("Deadline today")
      yesNo.whenFalse shouldBe None

      val provider = spi.provider.asInstanceOf[SpiDecision.SystemOne]
      provider.apiKey shouldBe "test-key"
      provider.modelName shouldBe "jev-1"
      provider.baseUrl shouldBe "https://api.typesafe.ai"
      provider.modelSettings.modelResponseTimeout shouldBe 30.seconds
    }

    "send a String input as a JSON string" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      client(spi).input("Charged \"twice\"").questions(urgent).invokeAsync().asScala.futureValue
      spi.request.stateJson shouldBe "\"Charged \\\"twice\\\"\""
    }

    "accept questions as a collection" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      val questions: java.util.Collection[Question[_]] = java.util.List.of(route, severity, urgent)
      client(spi).input("ticket").questions(questions).invoke()
      spi.request.questions.map(_.key) shouldBe Seq("route", "severity", "urgent")
    }

    "return the answers as typed answers" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      val decision = client(spi).input("ticket").questions(route, severity, urgent).invoke()

      decision.model shouldBe "jev-1-2026"
      val routeAnswer = decision.answer(route)
      routeAnswer.selected shouldBe "technical"
      routeAnswer.confidence shouldBe 0.6
      routeAnswer.probabilities.asScala shouldBe Map("billing" -> 0.2, "technical" -> 0.8)
      decision.answer(severity).value shouldBe 1.5
      decision.answer(severity).probabilities.asScala.map(_.doubleValue) shouldBe Seq(0.1, 0.3, 0.6)
      decision.answer(urgent).probability shouldBe 0.9
    }

    "use the provider given to the client" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      client(spi).model(DecisionModelProvider.fromConfig("other")).input("t").questions(urgent).invoke()
      spi.provider.modelName shouldBe "jev-2"

      client(spi)
        .model(
          DecisionModelProvider
            .systemOne()
            .withApiKey("key-3")
            .withModelName("jev-3")
            .withBaseUrl("http://localhost:9999"))
        .input("t")
        .questions(urgent)
        .invoke()
      spi.provider.modelName shouldBe "jev-3"
      spi.provider.asInstanceOf[SpiDecision.SystemOne].baseUrl shouldBe "http://localhost:9999"
    }

    "add the extra headers of a config provider to the resolved provider" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      val extraHeader: akka.http.javadsl.model.HttpHeader =
        akka.http.javadsl.model.headers.RawHeader.create("x-extra", "yes")
      client(spi)
        .model(DecisionModelProvider.fromConfig("other").withAdditionalModelRequestHeaders(List(extraHeader).asJava))
        .input("t")
        .questions(urgent)
        .invoke()
      val settings = spi.provider.asInstanceOf[SpiDecision.SystemOne].modelSettings
      settings.additionalModelRequestHeaders.map(_.name) should contain("x-extra")
    }

    "answer with a custom provider without calling the runtime" in {
      val spi = new FakeSpiClient(_ => Future.failed(new IllegalStateException("not expected")))
      val custom = new DecisionModelProvider.Custom {
        override def decide(request: DecisionModelProvider.DecisionRequest): Decision = {
          request.input shouldBe new Ticket("a", "b")
          new Decision("custom", Map[String, Answer]("urgent" -> new Answer.YesNo(0.3)).asJava)
        }
      }
      val decision = client(spi).model(custom).input(new Ticket("a", "b")).questions(urgent).invoke()
      decision.answer(urgent).probability shouldBe 0.3
      spi.request shouldBe null
    }

    "drop answers of a custom provider for questions that were not asked" in {
      val spi = new FakeSpiClient(_ => Future.failed(new IllegalStateException("not expected")))
      val custom = new DecisionModelProvider.Custom {
        override def decide(request: DecisionModelProvider.DecisionRequest): Decision =
          new Decision(
            "custom",
            Map[String, Answer]("urgent" -> new Answer.YesNo(0.3), "extra" -> new Answer.YesNo(0.7)).asJava)
      }
      val decision = client(spi).model(custom).input("t").questions(urgent).invoke()
      decision.answers.asScala.keySet shouldBe Set("urgent")
    }

    "fail when a custom provider answers outside the question" in {
      val spi = new FakeSpiClient(_ => Future.failed(new IllegalStateException("not expected")))
      def customWith(answer: Answer, question: Question[_]) = {
        val custom = new DecisionModelProvider.Custom {
          override def decide(request: DecisionModelProvider.DecisionRequest): Decision =
            new Decision("custom", Map[String, Answer](question.key -> answer).asJava)
        }
        intercept[ModelException](client(spi).model(custom).input("t").questions(question).invoke())
      }
      customWith(new Answer.Choice("marketing", 1.0, Map.empty[String, java.lang.Double].asJava), route).getMessage
        .should(include("selects [marketing]"))
      customWith(new Answer.Score(3.5, 1.0, java.util.List.of[java.lang.Double]()), severity).getMessage
        .should(include("outside the levels"))
      customWith(new Answer.YesNo(1.5), urgent).getMessage should include("outside 0 to 1")
    }

    "use the test override before any other provider" in {
      val spi = new FakeSpiClient(_ => Future.failed(new IllegalStateException("not expected")))
      val overrideProvider = new OverrideDecisionModelProvider
      overrideProvider.set(new DecisionModelProvider.Custom {
        override def decide(request: DecisionModelProvider.DecisionRequest): Decision =
          new Decision("override", Map[String, Answer]("urgent" -> new Answer.YesNo(0.5)).asJava)
      })
      val decision = client(spi, overrideProvider = overrideProvider)
        .model(DecisionModelProvider.systemOne().withModelName("jev-3"))
        .input("t")
        .questions(urgent)
        .invoke()
      decision.model shouldBe "override"
    }

    "fail when a custom provider does not answer every question" in {
      val custom = new DecisionModelProvider.Custom {
        override def decide(request: DecisionModelProvider.DecisionRequest): Decision =
          new Decision("custom", Map[String, Answer]("urgent" -> new Answer.YesNo(0.3)).asJava)
      }
      val spi = new FakeSpiClient(_ => Future.failed(new IllegalStateException("not expected")))
      val e = intercept[ModelException](client(spi).model(custom).input("t").questions(urgent, route).invoke())
      e.getMessage should include("No answer for question [route]")
    }

    "map runtime failures to the agent exceptions" in {
      def failWith(reason: SpiAgent.FailureReason): Throwable = {
        val spi = new FakeSpiClient(_ => Future.failed(new SpiAgent.AgentException("boom", reason)))
        client(spi).input("t").questions(urgent).invokeAsync().asScala.failed.futureValue
      }
      failWith(SpiAgent.ModelFailure) shouldBe a[ModelException]
      failWith(SpiAgent.RateLimitFailure) shouldBe a[RateLimitException]
      failWith(SpiAgent.TimeoutFailure) shouldBe a[ModelTimeoutException]
      failWith(SpiAgent.InternalFailure) shouldBe a[InternalServerException]
    }

    "reject a request without questions or with duplicate keys" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      intercept[IllegalArgumentException](client(spi).input("t").questions())
      val sameKey = Question.yesNo("Other?").build("urgent")
      val e = intercept[IllegalArgumentException](client(spi).input("t").questions(urgent, sameKey))
      e.getMessage should include("Duplicate question keys [urgent]")
    }

    "reject an input that is not JSON text, an object or an array" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      val number = intercept[IllegalArgumentException](client(spi).input(42))
      number.getMessage should include("java.lang.Integer")
      intercept[IllegalArgumentException](client(spi).input(true))
      intercept[IllegalArgumentException](client(spi).input(1.5))
      intercept[IllegalArgumentException](client(spi).input(Array[Byte](1, 2)))
      intercept[IllegalArgumentException](client(spi).input(" "))
      intercept[NullPointerException](client(spi).input(null))
    }

    "fail with an IllegalArgumentException for an input that cannot be serialized" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      val e = intercept[IllegalArgumentException](client(spi).input(new Object {}).questions(urgent).invoke())
      e.getMessage should include("cannot be serialized to JSON")
    }

    "reject more options or levels than the provider supports" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      var choice = Question.choice("Pick").option("o0", "D0").option("o1", "D1")
      (2 to 255).foreach(i => choice = choice.option(s"o$i", s"D$i"))
      val tooManyOptions = choice.build("big-choice")
      val e1 = intercept[IllegalArgumentException](client(spi).input("t").questions(tooManyOptions).invoke())
      e1.getMessage should include("at most [255] options")

      val tooManyLevels =
        Question.score("Rate").levels("l0", "l1", (2 to 10).map(i => s"l$i"): _*).build("big-score")
      val e2 = intercept[IllegalArgumentException](client(spi).input("t").questions(tooManyLevels).invoke())
      e2.getMessage should include("at most [10] levels")
    }

    "fail when no provider is configured" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      val e = intercept[IllegalArgumentException](
        client(spi, config = ConfigFactory.load()).input("t").questions(urgent).invoke())
      e.getMessage should include("akka.javasdk.decision.model-provider")
    }

    "fail when the provider has no model name" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      val e = intercept[IllegalArgumentException](
        client(spi).model(DecisionModelProvider.systemOne()).input("t").questions(urgent).invoke())
      e.getMessage should include("model name")
    }

    "fail when the provider has no API key" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      val e = intercept[IllegalArgumentException](
        client(spi)
          .model(DecisionModelProvider.systemOne().withModelName("jev-1"))
          .input("t")
          .questions(urgent)
          .invoke())
      e.getMessage should include("API key")
    }

    "fail for an undefined provider configuration" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      val e = intercept[IllegalArgumentException](
        client(spi).model(DecisionModelProvider.fromConfig("missing")).input("t").questions(urgent).invoke())
      e.getMessage should include("Undefined decision model provider configuration [missing]")
    }

    "fail with an IllegalArgumentException for a provider configuration without a required key" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      val incomplete = ConfigFactory
        .parseString("""
          akka.javasdk.decision.escalation-model {
            provider = "system-one"
            model-name = "jev-1"
          }
          """)
        .withFallback(DecisionModelClientImplSpec.config)
      val e = intercept[IllegalArgumentException](
        client(spi, config = incomplete)
          .model(DecisionModelProvider.fromConfig("escalation-model"))
          .input("t")
          .questions(urgent)
          .invoke())
      e.getMessage should include("[akka.javasdk.decision.escalation-model]")
      e.getMessage should include("api-key")
    }
  }

  "The SystemOne provider" should {
    "load defaults from config" in {
      val p =
        DecisionModelProvider.SystemOne.fromConfig(ConfigFactory.load().getConfig("akka.javasdk.decision.system-one"))
      p.modelName shouldBe ""
      p.baseUrl shouldBe "https://api.typesafe.ai"
      p.connectionTimeout shouldBe java.time.Duration.ofSeconds(15)
      p.responseTimeout shouldBe java.time.Duration.ofSeconds(30)
      p.maxRetries shouldBe 2
      p shouldBe DecisionModelProvider.systemOne()
    }
  }
}
