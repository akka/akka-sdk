/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util.{ List => JList }
import java.util.function.{ Function => JFunction }

import akka.javasdk.Metadata
import akka.javasdk.agent.Judgment
import akka.javasdk.agent.JudgmentModelProvider
import akka.javasdk.agent.Question
import akka.javasdk.impl.agent.BaseAgentEffectBuilder.RequestJudgment
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JudgmentEffectSpec extends AnyWordSpec with Matchers {

  private val route =
    Question.choice("Which team should handle this?").option("billing", "Payments").option("technical")

  private def requestOf(effect: AnyRef): RequestJudgment =
    effect.asInstanceOf[AgentEffectImpl].primaryEffect.asInstanceOf[RequestJudgment]

  "The judgment effect builder" should {

    "collect the state and the questions in order" in {
      val effect = new BaseAgentEffectBuilder[AnyRef]()
        .judgment()
        .state("Help! My payouts have been failing for 3 days.")
        .question("route", route)
        .question("urgent", Question.yesNo("Does this need a reply today?"))
        .thenReply()

      val request = requestOf(effect)
      request.state shouldBe Some("Help! My payouts have been failing for 3 days.")
      request.questions.map(_._1) shouldBe Vector("route", "urgent")
      request.questions.head._2 shouldBe route
      request.provider shouldBe JudgmentModelProvider.fromConfig()
      request.responseMapping shouldBe None
      request.failureMapping shouldBe None
      request.replyMetadata shouldBe Metadata.EMPTY
    }

    "keep the provider, the mapping, the failure mapping and the reply metadata" in {
      val provider = JudgmentModelProvider.systemOne().withApiKey("key")
      val metadata = Metadata.EMPTY.add("k", "v")
      val mapper: JFunction[Judgment, String] = judgment => judgment.choice("route").choice()
      val recover: JFunction[Throwable, String] = _ => "unknown"

      val effect = new BaseAgentEffectBuilder[AnyRef]()
        .judgment()
        .model(provider)
        .state("ticket")
        .question("route", route)
        .map(mapper)
        .onFailure(recover)
        .thenReply(metadata)

      val request = requestOf(effect)
      request.provider shouldBe provider
      request.responseMapping.isDefined shouldBe true
      request.failureMapping.isDefined shouldBe true
      request.replyMetadata shouldBe metadata
    }

    "accept an object state" in {
      val state = JList.of("first message", "second message")
      val request =
        requestOf(new BaseAgentEffectBuilder[AnyRef]().judgment().state(state).question("route", route).thenReply())
      request.state shouldBe Some(state)
    }

    "reject a null, blank, optional, number or boolean state" in {
      val builder = new BaseAgentEffectBuilder[AnyRef]().judgment()
      an[IllegalArgumentException] should be thrownBy builder.state(null)
      an[IllegalArgumentException] should be thrownBy builder.state("  ")
      an[IllegalArgumentException] should be thrownBy builder.state(java.util.Optional.of("ticket"))
      an[IllegalArgumentException] should be thrownBy builder.state(Integer.valueOf(42))
      an[IllegalArgumentException] should be thrownBy builder.state(java.lang.Boolean.TRUE)
    }

    "reject a blank or duplicate question key" in {
      val builder = new BaseAgentEffectBuilder[AnyRef]().judgment().state("ticket").question("route", route)
      an[IllegalArgumentException] should be thrownBy builder.question(" ", route)
      an[IllegalArgumentException] should be thrownBy builder.question("route", route)
      an[IllegalArgumentException] should be thrownBy builder.question("other", null)
    }

    "reject a choice question without options" in {
      val builder = new BaseAgentEffectBuilder[AnyRef]().judgment().state("ticket")
      an[IllegalArgumentException] should be thrownBy builder.question("route", Question.choice("Which team?"))
    }

    "require a state and a question before the reply" in {
      an[IllegalStateException] should be thrownBy
      new BaseAgentEffectBuilder[AnyRef]().judgment().question("route", route).thenReply()
      an[IllegalStateException] should be thrownBy
      new BaseAgentEffectBuilder[AnyRef]().judgment().state("ticket").thenReply()
      an[IllegalStateException] should be thrownBy
      new BaseAgentEffectBuilder[AnyRef]()
        .judgment()
        .state("ticket")
        .map[String](_.model())
        .thenReply()
    }

    "only start after no other call on the builder" in {
      val afterSystemMessage = new BaseAgentEffectBuilder[AnyRef]()
      afterSystemMessage.systemMessage("You are helpful")
      an[IllegalStateException] should be thrownBy afterSystemMessage.judgment()

      val afterReply = new BaseAgentEffectBuilder[AnyRef]()
      afterReply.reply("done")
      an[IllegalStateException] should be thrownBy afterReply.judgment()
    }
  }

  "The questions" should {

    "validate a choice" in {
      an[IllegalArgumentException] should be thrownBy Question.choice(" ")
      an[IllegalArgumentException] should be thrownBy route.option("billing")
      an[IllegalArgumentException] should be thrownBy route.option(" ", "blank key")
      an[IllegalArgumentException] should be thrownBy route.option("other", " ")
      val tooMany = (1 to Question.Choice.MAX_OPTIONS).foldLeft(Question.choice("Pick one")) { (q, i) =>
        q.option(s"option-$i")
      }
      an[IllegalArgumentException] should be thrownBy tooMany.option("one-more")
      route.options.size shouldBe 2
      route.options.get(0).description.get shouldBe "Payments"
      route.options.get(1).description.isPresent shouldBe false
    }

    "validate a score" in {
      an[IllegalArgumentException] should be thrownBy Question.score("How severe?", "Only one")
      an[IllegalArgumentException] should be thrownBy Question.score("How severe?", "Low", " ")
      an[IllegalArgumentException] should be thrownBy
      Question.score("How severe?", (1 to 11).map(i => s"level $i"): _*)
      Question.score("How severe?", "Low", "High").levels.size shouldBe 2
    }

    "validate a yes or no question" in {
      an[IllegalArgumentException] should be thrownBy Question.yesNo("")
      a[NullPointerException] should be thrownBy Question.yesNo("Urgent?", null, "Can wait")
      an[IllegalArgumentException] should be thrownBy Question.yesNo("Urgent?", " ", "Can wait")
      val yesNo = Question.yesNo("Urgent?", "Time-sensitive", "Can wait")
      yesNo.whenYes.get shouldBe "Time-sensitive"
      yesNo.whenNo.get shouldBe "Can wait"
    }
  }
}
