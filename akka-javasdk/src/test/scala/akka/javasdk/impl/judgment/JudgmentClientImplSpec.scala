/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.judgment

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

import akka.javasdk.agent.InternalServerException
import akka.javasdk.agent.ModelException
import akka.javasdk.agent.ModelTimeoutException
import akka.javasdk.agent.RateLimitException
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.judgment.Answer
import akka.javasdk.judgment.Judgment
import akka.javasdk.judgment.JudgmentModelProvider
import akka.javasdk.judgment.Question
import akka.javasdk.judgment.YesNoAnswer
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiJudgment
import akka.runtime.sdk.spi.SpiJudgmentClient
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.opentelemetry.context.Context
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

object JudgmentClientImplSpec {

  final class FakeSpiClient(respond: SpiJudgment.Request => Future[SpiJudgment.Response]) extends SpiJudgmentClient {
    @volatile var provider: SpiJudgment.ModelProvider = _
    @volatile var request: SpiJudgment.Request = _

    override def judge(
        provider: SpiJudgment.ModelProvider,
        request: SpiJudgment.Request,
        telemetryContext: Option[Context]): Future[SpiJudgment.Response] = {
      this.provider = provider
      this.request = request
      respond(request)
    }
  }

  val config: Config = ConfigFactory.load(ConfigFactory.parseString("""
    akka.javasdk.judgment {
      model-provider = system-one
      system-one {
        api-key = "test-key"
        model-name = "jev-1"
      }
      other = ${akka.javasdk.judgment.system-one}
      other.model-name = "jev-2"
    }
    """))
}

class JudgmentClientImplSpec extends AnyWordSpec with Matchers with ScalaFutures {
  import JudgmentClientImplSpec._

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = Span.convertDurationToSpan(3.seconds))
  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val route =
    Question.choice("Which team?").option("billing", "Payments").option("technical", "Bugs").build("route")
  private val severity = Question.score("How severe?").levels("low", "medium", "high").build("severity")
  private val urgent = Question.yesNo("Urgent?").whenYes("Deadline today").build("urgent")

  private val allAnswers =
    new SpiJudgment.Response(
      "jev-1-2026",
      Map(
        "route" -> new SpiJudgment.ChoiceAnswer("technical", Map("technical" -> 0.8, "billing" -> 0.2), 0.6),
        "severity" -> new SpiJudgment.ScoreAnswer(1.5, Seq(0.1, 0.3, 0.6), 0.4),
        "urgent" -> new SpiJudgment.YesNoAnswer(0.9)),
      inputTokenCount = 10,
      outputTokenCount = 3)

  private def client(
      spi: SpiJudgmentClient,
      config: Config = JudgmentClientImplSpec.config,
      overrideProvider: OverrideJudgmentModelProvider = new OverrideJudgmentModelProvider) =
    new JudgmentClientImpl(spi, new JsonSerializer, config, overrideProvider, None, None)

  "The judgment client" should {

    "send the state as JSON and the questions to the configured provider" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      client(spi).state(new Ticket("Refund", "Charged twice")).questions(route, severity, urgent).invoke()

      spi.request.stateJson shouldBe """{"subject":"Refund","body":"Charged twice"}"""
      spi.request.questions.map(_.key) shouldBe Seq("route", "severity", "urgent")

      val choice = spi.request.questions.head.asInstanceOf[SpiJudgment.ChoiceQuestion]
      choice.instructions shouldBe "Which team?"
      choice.options.map(o => o.key -> o.description) shouldBe Seq("billing" -> "Payments", "technical" -> "Bugs")
      spi.request.questions(1).asInstanceOf[SpiJudgment.ScoreQuestion].levels shouldBe Seq("low", "medium", "high")
      val yesNo = spi.request.questions(2).asInstanceOf[SpiJudgment.YesNoQuestion]
      yesNo.whenYes shouldBe Some("Deadline today")
      yesNo.whenNo shouldBe None

      val provider = spi.provider.asInstanceOf[SpiJudgment.SystemOne]
      provider.apiKey shouldBe "test-key"
      provider.modelName shouldBe "jev-1"
      provider.baseUrl shouldBe "https://api.typesafe.ai"
      provider.modelSettings.modelResponseTimeout shouldBe 30.seconds
    }

    "send a String state as a JSON string" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      client(spi).state("Charged \"twice\"").questions(urgent).invokeAsync().asScala.futureValue
      spi.request.stateJson shouldBe "\"Charged \\\"twice\\\"\""
    }

    "return the answers as typed answers" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      val judgment = client(spi).state("ticket").questions(route, severity, urgent).invoke()

      judgment.model shouldBe "jev-1-2026"
      val routeAnswer = judgment.answer(route)
      routeAnswer.selected shouldBe "technical"
      routeAnswer.confidence shouldBe 0.6
      routeAnswer.probabilities.asScala shouldBe Map("billing" -> 0.2, "technical" -> 0.8)
      judgment.answer(severity).value shouldBe 1.5
      judgment.answer(severity).probabilities.asScala.map(_.doubleValue) shouldBe Seq(0.1, 0.3, 0.6)
      judgment.answer(urgent).probability shouldBe 0.9
    }

    "use the provider given to the client" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      client(spi).model(JudgmentModelProvider.fromConfig("other")).state("t").questions(urgent).invoke()
      spi.provider.modelName shouldBe "jev-2"

      client(spi)
        .model(JudgmentModelProvider.systemOne().withModelName("jev-3").withBaseUrl("http://localhost:9999"))
        .state("t")
        .questions(urgent)
        .invoke()
      spi.provider.modelName shouldBe "jev-3"
      spi.provider.asInstanceOf[SpiJudgment.SystemOne].baseUrl shouldBe "http://localhost:9999"
    }

    "answer with a custom provider without calling the runtime" in {
      val spi = new FakeSpiClient(_ => Future.failed(new IllegalStateException("not expected")))
      val custom = new JudgmentModelProvider.Custom {
        override def judge(request: JudgmentModelProvider.Request): Judgment = {
          request.state shouldBe new Ticket("a", "b")
          new Judgment("custom", Map[String, Answer]("urgent" -> new YesNoAnswer(0.3)).asJava)
        }
      }
      val judgment = client(spi).model(custom).state(new Ticket("a", "b")).questions(urgent).invoke()
      judgment.answer(urgent).probability shouldBe 0.3
      spi.request shouldBe null
    }

    "use the test override before any other provider" in {
      val spi = new FakeSpiClient(_ => Future.failed(new IllegalStateException("not expected")))
      val overrideProvider = new OverrideJudgmentModelProvider
      overrideProvider.set(new JudgmentModelProvider.Custom {
        override def judge(request: JudgmentModelProvider.Request): Judgment =
          new Judgment("override", Map[String, Answer]("urgent" -> new YesNoAnswer(0.5)).asJava)
      })
      val judgment = client(spi, overrideProvider = overrideProvider)
        .model(JudgmentModelProvider.systemOne().withModelName("jev-3"))
        .state("t")
        .questions(urgent)
        .invoke()
      judgment.model shouldBe "override"
    }

    "fail when a custom provider does not answer every question" in {
      val custom = new JudgmentModelProvider.Custom {
        override def judge(request: JudgmentModelProvider.Request): Judgment =
          new Judgment("custom", Map[String, Answer]("urgent" -> new YesNoAnswer(0.3)).asJava)
      }
      val spi = new FakeSpiClient(_ => Future.failed(new IllegalStateException("not expected")))
      val e = intercept[ModelException](client(spi).model(custom).state("t").questions(urgent, route).invoke())
      e.getMessage should include("No answer for question [route]")
    }

    "map runtime failures to the agent exceptions" in {
      def failWith(reason: SpiAgent.FailureReason): Throwable = {
        val spi = new FakeSpiClient(_ => Future.failed(new SpiAgent.AgentException("boom", reason)))
        client(spi).state("t").questions(urgent).invokeAsync().asScala.failed.futureValue
      }
      failWith(SpiAgent.ModelFailure) shouldBe a[ModelException]
      failWith(SpiAgent.RateLimitFailure) shouldBe a[RateLimitException]
      failWith(SpiAgent.TimeoutFailure) shouldBe a[ModelTimeoutException]
      failWith(SpiAgent.InternalFailure) shouldBe a[InternalServerException]
    }

    "reject a request without questions or with duplicate keys" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      intercept[IllegalArgumentException](client(spi).state("t").questions())
      val sameKey = Question.yesNo("Other?").build("urgent")
      val e = intercept[IllegalArgumentException](client(spi).state("t").questions(urgent, sameKey))
      e.getMessage should include("Duplicate question keys [urgent]")
    }

    "fail when no provider is configured" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      val e = intercept[IllegalArgumentException](
        client(spi, config = ConfigFactory.load()).state("t").questions(urgent).invoke())
      e.getMessage should include("akka.javasdk.judgment.model-provider")
    }

    "fail when the provider has no model name" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      val e = intercept[IllegalArgumentException](
        client(spi).model(JudgmentModelProvider.systemOne()).state("t").questions(urgent).invoke())
      e.getMessage should include("model name")
    }

    "fail for an undefined provider configuration" in {
      val spi = new FakeSpiClient(_ => Future.successful(allAnswers))
      val e = intercept[IllegalArgumentException](
        client(spi).model(JudgmentModelProvider.fromConfig("missing")).state("t").questions(urgent).invoke())
      e.getMessage should include("Undefined judgment model provider configuration [missing]")
    }
  }

  "The SystemOne provider" should {
    "load defaults from config" in {
      val p =
        JudgmentModelProvider.SystemOne.fromConfig(ConfigFactory.load().getConfig("akka.javasdk.judgment.system-one"))
      p.modelName shouldBe ""
      p.baseUrl shouldBe "https://api.typesafe.ai"
      p.connectionTimeout shouldBe java.time.Duration.ofSeconds(15)
      p.responseTimeout shouldBe java.time.Duration.ofSeconds(30)
      p.maxRetries shouldBe 2
      p shouldBe JudgmentModelProvider.systemOne()
    }
  }
}
