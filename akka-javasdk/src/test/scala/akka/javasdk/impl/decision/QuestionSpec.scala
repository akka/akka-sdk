/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.decision

import java.util.Optional

import scala.jdk.CollectionConverters._

import akka.javasdk.decision.Answer
import akka.javasdk.decision.Decision
import akka.javasdk.decision.Question
import akka.javasdk.impl.serialization.JsonSerializer
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class QuestionSpec extends AnyWordSpec with Matchers {

  private val route =
    Question
      .choice("Which team should handle this ticket?")
      .option("billing", "Payments, invoicing, refunds")
      .option("technical", "Bugs, outages, integrations")
      .build("route")

  private val severity =
    Question
      .score("How severe is the reported issue?")
      .levels("Cosmetic", "Broken, with a workaround", "Blocking")
      .build("severity")

  private val urgent =
    Question
      .yesNo("Does the customer need a reply today?")
      .whenYes("The customer states a deadline of today or earlier")
      .whenNo("There is no deadline")
      .build("urgent")

  "A choice question" should {
    "keep the options in order" in {
      route.key shouldBe "route"
      route.instructions shouldBe "Which team should handle this ticket?"
      route.options.asScala.map(_.key) shouldBe Seq("billing", "technical")
      route.options.get(0).description shouldBe "Payments, invoicing, refunds"
    }

    "accept more than two options" in {
      val q = Question.choice("Pick").option("a", "A").option("b", "B").option("c", "C").build("pick")
      q.options.asScala.map(_.key) shouldBe Seq("a", "b", "c")
    }

    "reject duplicate option keys" in {
      val e = intercept[IllegalArgumentException] {
        Question.choice("Pick").option("a", "A").option("a", "Another A").build("pick")
      }
      e.getMessage should include("Duplicate option key [a]")
    }

    "reject blank instructions, keys and descriptions" in {
      intercept[IllegalArgumentException](Question.choice(" "))
      intercept[IllegalArgumentException](Question.choice("Pick").option("", "A"))
      intercept[IllegalArgumentException](Question.choice("Pick").option("a", ""))
      intercept[IllegalArgumentException](Question.choice("Pick").option("a", "A").option("b", "B").build(" "))
    }

    "reject fewer than two options when created directly" in {
      intercept[IllegalArgumentException] {
        new Question.Choice("k", "Pick", List(new Question.Choice.Option("a", "A")).asJava)
      }
    }

    "start a new builder from an existing question" in {
      val extended = Question.choice(route).option("sales", "New business").build("route-with-sales")
      extended.key shouldBe "route-with-sales"
      extended.instructions shouldBe route.instructions
      extended.options.asScala.map(_.key) shouldBe Seq("billing", "technical", "sales")
      // the original is unchanged
      route.options.size shouldBe 2
    }

    "reject a template of another question type" in {
      val raw: Question[Answer.Choice] = severity.asInstanceOf[Question[Answer.Choice]]
      val e = intercept[IllegalArgumentException](Question.choice(raw))
      e.getMessage should include("not a choice question")
    }
  }

  "A score question" should {
    "keep the levels in order" in {
      severity.levels.asScala shouldBe Seq("Cosmetic", "Broken, with a workaround", "Blocking")
    }

    "accept two levels" in {
      Question.score("Rate").levels("low", "high").build("rate").levels.size shouldBe 2
    }

    "reject blank levels" in {
      intercept[IllegalArgumentException](Question.score("Rate").levels("low", " ").build("rate"))
    }

    "start a new builder from an existing question" in {
      val copy = Question.score(severity).build("severity-2")
      copy.key shouldBe "severity-2"
      copy.levels shouldBe severity.levels
    }
  }

  "A yes or no question" should {
    "keep the criteria" in {
      urgent.whenYes shouldBe Optional.of("The customer states a deadline of today or earlier")
      urgent.whenNo shouldBe Optional.of("There is no deadline")
    }

    "not need criteria" in {
      val q = Question.yesNo("Is it spam?").build("spam")
      q.whenYes shouldBe Optional.empty()
      q.whenNo shouldBe Optional.empty()
    }

    "start a new builder from an existing question" in {
      val copy = Question.yesNo(urgent).whenNo("No deadline is mentioned").build("urgent-2")
      copy.whenYes shouldBe urgent.whenYes
      copy.whenNo shouldBe Optional.of("No deadline is mentioned")
    }
  }

  "A decision" should {
    val choice =
      new Answer.Choice("billing", 0.9, Map[String, java.lang.Double]("billing" -> 0.95, "technical" -> 0.05).asJava)
    val score = new Answer.Score(1.4, 0.7, List[java.lang.Double](0.1, 0.4, 0.5).asJava)
    val yesNo = new Answer.YesNo(0.8)
    val decision =
      new Decision("jev-1", Map[String, Answer]("route" -> choice, "severity" -> score, "urgent" -> yesNo).asJava)

    "return the typed answer for each question" in {
      val routeAnswer: Answer.Choice = decision.answer(route)
      routeAnswer.selected shouldBe "billing"
      val severityAnswer: Answer.Score = decision.answer(severity)
      severityAnswer.value shouldBe 1.4
      val urgentAnswer: Answer.YesNo = decision.answer(urgent)
      urgentAnswer.probability shouldBe 0.8
    }

    "fail for a question it has no answer for" in {
      val other = Question.yesNo("Other?").build("other")
      val e = intercept[IllegalArgumentException](decision.answer(other))
      e.getMessage should include("No answer for question [other]")
      e.getMessage should include("route")
    }

    "fail when the answer has another type than the question" in {
      val sameKey = Question.yesNo("Route?").build("route")
      val e = intercept[IllegalArgumentException](decision.answer(sameKey))
      e.getMessage should include("[Choice]")
    }

    "fail when the answer belongs to a question with other options" in {
      val otherOptions =
        Question.choice("Which region?").option("east", "East").option("west", "West").build("route")
      val e = intercept[IllegalArgumentException](decision.answer(otherOptions))
      e.getMessage should include("not an option of the question")

      val oneOtherOption =
        Question.choice("Which team?").option("billing", "Payments").option("sales", "New business").build("route")
      val e2 = intercept[IllegalArgumentException](decision.answer(oneOtherOption))
      e2.getMessage should include("not all options of the question")
    }

    "fail when the answer belongs to a question with other levels" in {
      val otherLevels = Question.score("Rate").levels("low", "high").build("severity")
      val e = intercept[IllegalArgumentException](decision.answer(otherLevels))
      e.getMessage should include("levels")
    }

    "serialize and deserialize as JSON" in {
      val serializer = new JsonSerializer
      val json = serializer.toJsonString(decision)
      json should include(""""type":"choice"""")
      json should include(""""type":"score"""")
      json should include(""""type":"yes-no"""")

      val restored = serializer.fromJsonString(json, classOf[Decision])
      restored shouldBe decision
      restored.answer(route).selected shouldBe "billing"
      restored.answer(severity).value shouldBe 1.4
      restored.answer(urgent).probability shouldBe 0.8
    }
  }
}
