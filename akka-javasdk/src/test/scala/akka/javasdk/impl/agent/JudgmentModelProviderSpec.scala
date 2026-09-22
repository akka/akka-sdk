/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.time.Duration
import java.time.Instant
import java.util.{ List => JList }
import java.util.{ Map => JMap }

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.javadsl.model.headers.RawHeader
import akka.javasdk.JsonSupport
import akka.javasdk.agent.Agent
import akka.javasdk.agent.Judgment
import akka.javasdk.agent.JudgmentModelProvider
import akka.javasdk.agent.JudgmentRequest
import akka.javasdk.agent.Question
import akka.runtime.sdk.spi.SpiAgent
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

object JudgmentModelProviderSpec {
  private val config = ConfigFactory.load(ConfigFactory.parseString(s"""
    akka.javasdk.agent {
      judgment-model-provider = system-one

      system-one {
        api-key = "secret"
        model-name = "jev-preview"
      }

      fast-judge = $${akka.javasdk.agent.system-one}
      fast-judge {
        base-url = "http://localhost:8080/"
        max-retries = 0
      }

      custom-judge {
        provider = "akka.javasdk.impl.agent.MyJudgmentProvider"
        answer = "technical"
      }
    }

    other.judge = $${akka.javasdk.agent.system-one}
    other.judge {
      model-name = "other-model"
    }
    """))
}

/** Custom provider loaded by class name from configuration. */
class MyJudgmentProvider(config: Config) extends JudgmentModelProvider.Custom {
  override def judge(request: JudgmentRequest): Judgment =
    new Judgment(
      JMap.of("route", new Judgment.ChoiceAnswer(config.getString("answer"), JMap.of(), 1.0)),
      "my-model",
      new Agent.TokenUsage(1, 1))
}

class JudgmentModelProviderSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers {
  import JudgmentModelProviderSpec.config

  private val defaultConfig = ConfigFactory.load()

  private val route = Question.choice("Which team?").option("billing", "Payments").option("technical")
  private val severity = Question.score("How severe?", "Low", "High")
  private val urgent = Question.yesNo("Urgent?", "Time-sensitive", "Can wait")

  "The judgment model provider" should {

    "load defaults from config for system-one" in {
      val provider =
        JudgmentModelProvider.SystemOne.fromConfig(defaultConfig.getConfig("akka.javasdk.agent.system-one"))
      provider shouldBe JudgmentModelProvider.systemOne()
      provider.modelName shouldBe "jev-latest"
      provider.baseUrl shouldBe "https://api.typesafe.ai"
      provider.responseTimeout shouldBe Duration.ofSeconds(30)
      provider.maxRetries shouldBe 2
    }

    "load from judgment-model-provider in config" in {
      val provider = AgentImpl.judgmentModelProviderFromConfig(config, "", "myagent")
      provider shouldBe JudgmentModelProvider.systemOne().withApiKey("secret").withModelName("jev-preview")
    }

    "load from a bare name under akka.javasdk.agent" in {
      val provider = AgentImpl.judgmentModelProviderFromConfig(config, "fast-judge", "myagent")
      provider shouldBe JudgmentModelProvider
        .systemOne()
        .withApiKey("secret")
        .withModelName("jev-preview")
        .withBaseUrl("http://localhost:8080/")
        .withMaxRetries(0)
    }

    "load from a full config path" in {
      val provider = AgentImpl.judgmentModelProviderFromConfig(config, "other.judge", "myagent")
      provider shouldBe JudgmentModelProvider.systemOne().withApiKey("secret").withModelName("other-model")
    }

    "load a custom provider by class name" in {
      val provider = AgentImpl.judgmentModelProviderFromConfig(config, "custom-judge", "myagent")
      provider shouldBe a[MyJudgmentProvider]
    }

    "fail on an unknown provider or path" in {
      val unknown = ConfigFactory
        .parseString("""akka.javasdk.agent.bad { provider = "no-such-provider" }""")
        .withFallback(config)
      an[IllegalArgumentException] should be thrownBy
      AgentImpl.judgmentModelProviderFromConfig(unknown, "bad", "myagent")
      an[IllegalArgumentException] should be thrownBy
      AgentImpl.judgmentModelProviderFromConfig(config, "missing", "myagent")
      an[IllegalArgumentException] should be thrownBy
      AgentImpl.judgmentModelProviderFromConfig(defaultConfig, "", "myagent")
    }
  }

  "The SPI conversion" should {

    "convert a SystemOne provider with its settings" in {
      val provider = JudgmentModelProvider
        .systemOne()
        .withApiKey("secret")
        .withConnectionTimeout(Duration.ofSeconds(3))
        .withAdditionalModelRequestHeaders(JList.of(RawHeader.create("x-extra", "yes")))
      val spi = AgentImpl
        .toSpiJudgmentModelProvider(provider, config, "myagent", system.executionContext)
        .asInstanceOf[SpiAgent.JudgmentModelProvider.SystemOne]
      spi.apiKey shouldBe "secret"
      spi.modelName shouldBe "jev-latest"
      spi.baseUrl shouldBe "https://api.typesafe.ai"
      spi.providerName shouldBe "system-one"
      spi.modelSettings.modelConnectionTimeout shouldBe 3.seconds
      spi.modelSettings.modelResponseTimeout shouldBe 30.seconds
      spi.modelSettings.maxModelRetries shouldBe 2
      spi.modelSettings.additionalModelRequestHeaders.map(_.name) shouldBe Seq("x-extra")
    }

    "resolve a FromConfig provider" in {
      val spi = AgentImpl
        .toSpiJudgmentModelProvider(
          JudgmentModelProvider.fromConfig("fast-judge"),
          config,
          "myagent",
          system.executionContext)
        .asInstanceOf[SpiAgent.JudgmentModelProvider.SystemOne]
      spi.baseUrl shouldBe "http://localhost:8080/"
    }

    "convert the questions to SPI questions" in {
      val spiRequest =
        AgentImpl.toSpiJudgmentRequest(
          "\"ticket\"",
          Vector("route" -> route, "severity" -> severity, "urgent" -> urgent))
      spiRequest.stateJson shouldBe "\"ticket\""
      spiRequest.questions.map(_.key) shouldBe Seq("route", "severity", "urgent")
      val choice = spiRequest.questions.head.asInstanceOf[SpiAgent.ChoiceQuestion]
      choice.instructions shouldBe "Which team?"
      choice.options shouldBe Seq("billing" -> Some("Payments"), "technical" -> None)
      spiRequest.questions(1).asInstanceOf[SpiAgent.ScoreQuestion].levels shouldBe Seq("Low", "High")
      val noul = spiRequest.questions(2).asInstanceOf[SpiAgent.NoulQuestion]
      noul.whenTrue shouldBe Some("Time-sensitive")
      noul.whenFalse shouldBe Some("Can wait")
    }

    "convert an SPI request back for a custom provider" in {
      val spiRequest =
        AgentImpl.toSpiJudgmentRequest("{\"subject\":\"x\"}", Vector("route" -> route, "urgent" -> urgent))
      val request = AgentImpl.toJudgmentRequest(spiRequest)
      request.stateJson shouldBe "{\"subject\":\"x\"}"
      request.stateAsText shouldBe "{\"subject\":\"x\"}"
      request.questions.keySet.asScala.toSeq shouldBe Seq("route", "urgent")
      request.questions.get("route") shouldBe route
      request.questions.get("urgent") shouldBe urgent
      AgentImpl
        .toJudgmentRequest(AgentImpl.toSpiJudgmentRequest("\"plain\"", Vector.empty))
        .stateAsText shouldBe "plain"
    }

    "convert an SPI response to a Judgment in request order" in {
      val response = new SpiAgent.JudgmentResponse(
        "jev-1.13.0",
        Map(
          "urgent" -> new SpiAgent.NoulAnswer(0.87),
          "route" -> new SpiAgent.ChoiceAnswer("billing", Map("billing" -> 0.9, "technical" -> 0.1), 0.8),
          "severity" -> new SpiAgent.ScoreAnswer(1.3, Seq("Low", "High"), Seq(0.4, 0.6), 0.2)),
        120,
        9,
        Instant.now())
      val judgment = AgentImpl.toJudgment(response, Seq("route", "severity", "urgent"))
      judgment.answers.keySet.asScala.toSeq shouldBe Seq("route", "severity", "urgent")
      judgment.model shouldBe "jev-1.13.0"
      judgment.tokenUsage shouldBe new Agent.TokenUsage(120, 9)
      judgment.choice("route").choice shouldBe "billing"
      judgment.choice("route").probabilities.get("billing") shouldBe 0.9
      judgment.choice("route").confidence shouldBe 0.8
      judgment.score("severity").score shouldBe 1.3
      judgment.score("severity").legend.asScala shouldBe Seq("Low", "High")
      judgment.yesNo("urgent").probability shouldBe 0.87
      judgment.yesNo("urgent").isYes shouldBe true
      an[IllegalArgumentException] should be thrownBy judgment.choice("severity")
      an[IllegalArgumentException] should be thrownBy judgment.yesNo("missing")
    }

    "convert a Judgment from a custom provider to an SPI response" in {
      val judgment = new Judgment(
        JMap.of(
          "route",
          new Judgment.ChoiceAnswer("technical", JMap.of("technical", 1.0), 1.0),
          "severity",
          new Judgment.ScoreAnswer(2.0, JList.of("Low", "High"), JList.of(0.0, 1.0), 1.0),
          "urgent",
          new Judgment.YesNoAnswer(0.1)),
        "",
        null)
      val spi = AgentImpl.toSpiJudgmentResponse(judgment, "fallback-model")
      spi.model shouldBe "fallback-model"
      spi.inputTokenCount shouldBe 0
      spi.answers("route").asInstanceOf[SpiAgent.ChoiceAnswer].choice shouldBe "technical"
      spi.answers("severity").asInstanceOf[SpiAgent.ScoreAnswer].probabilities shouldBe Seq(0.0, 1.0)
      spi.answers("urgent").asInstanceOf[SpiAgent.NoulAnswer].probability shouldBe 0.1
    }

    "call a custom provider in process" in {
      val provider = new MyJudgmentProvider(ConfigFactory.parseString("answer = billing"))
      val spi = AgentImpl
        .toSpiJudgmentModelProvider(provider, config, "myagent", system.executionContext)
        .asInstanceOf[SpiAgent.JudgmentModelProvider.Custom]
      spi.modelName shouldBe "custom"
      val response =
        Await.result(spi.judge(AgentImpl.toSpiJudgmentRequest("\"ticket\"", Vector("route" -> route))), 3.seconds)
      response.model shouldBe "my-model"
      response.answers("route").asInstanceOf[SpiAgent.ChoiceAnswer].choice shouldBe "billing"
    }

    "serialize a String state as a JSON string and an object as JSON" in {
      AgentImpl.judgmentStateJson(JsonSupport.getObjectMapper, "Help!") shouldBe "\"Help!\""
      AgentImpl.judgmentStateJson(JsonSupport.getObjectMapper, JMap.of("subject", "x")) shouldBe "{\"subject\":\"x\"}"
      AgentImpl.judgmentStateJson(JsonSupport.getObjectMapper, JList.of("a", "b")) shouldBe "[\"a\",\"b\"]"
    }
  }
}
