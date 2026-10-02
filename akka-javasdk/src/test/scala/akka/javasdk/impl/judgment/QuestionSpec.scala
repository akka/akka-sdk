/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.judgment

import java.util.Optional

import scala.jdk.CollectionConverters._

import akka.javasdk.judgment.Answer
import akka.javasdk.judgment.ChoiceAnswer
import akka.javasdk.judgment.Judgment
import akka.javasdk.judgment.Question
import akka.javasdk.judgment.ScoreAnswer
import akka.javasdk.judgment.YesNoAnswer
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
      val q = Question.choice("Pick").option("a", "A").option("b", "B").option("c", "C").build()
      q.options.asScala.map(_.key) shouldBe Seq("a", "b", "c")
    }

    "generate a key when none is given" in {
      val q1 = Question.choice("Pick").option("a", "A").option("b", "B").build()
      val q2 = Question.choice("Pick").option("a", "A").option("b", "B").build()
      q1.key should not be empty
      q1.key should not be q2.key
    }

    "reject duplicate option keys" in {
      val e = intercept[IllegalArgumentException] {
        Question.choice("Pick").option("a", "A").option("a", "Another A").build()
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
  }

  "A score question" should {
    "keep the levels in order" in {
      severity.levels.asScala shouldBe Seq("Cosmetic", "Broken, with a workaround", "Blocking")
    }

    "accept two levels" in {
      Question.score("Rate").levels("low", "high").build().levels.size shouldBe 2
    }

    "reject blank levels" in {
      intercept[IllegalArgumentException](Question.score("Rate").levels("low", " ").build())
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
      val q = Question.yesNo("Is it spam?").build()
      q.whenYes shouldBe Optional.empty()
      q.whenNo shouldBe Optional.empty()
    }

    "start a new builder from an existing question" in {
      val copy = Question.yesNo(urgent).whenNo("No deadline is mentioned").build("urgent-2")
      copy.whenYes shouldBe urgent.whenYes
      copy.whenNo shouldBe Optional.of("No deadline is mentioned")
    }
  }

  "A judgment" should {
    val choice =
      new ChoiceAnswer("billing", 0.9, Map[String, java.lang.Double]("billing" -> 0.95, "technical" -> 0.05).asJava)
    val score = new ScoreAnswer(1.4, 0.7, List[java.lang.Double](0.1, 0.4, 0.5).asJava)
    val yesNo = new YesNoAnswer(0.8)
    val judgment =
      new Judgment("jev-1", Map[String, Answer]("route" -> choice, "severity" -> score, "urgent" -> yesNo).asJava)

    "return the typed answer for each question" in {
      val routeAnswer: ChoiceAnswer = judgment.answer(route)
      routeAnswer.selected shouldBe "billing"
      val severityAnswer: ScoreAnswer = judgment.answer(severity)
      severityAnswer.value shouldBe 1.4
      val urgentAnswer: YesNoAnswer = judgment.answer(urgent)
      urgentAnswer.probability shouldBe 0.8
    }

    "fail for a question it has no answer for" in {
      val other = Question.yesNo("Other?").build("other")
      val e = intercept[IllegalArgumentException](judgment.answer(other))
      e.getMessage should include("No answer for question [other]")
    }

    "fail when the answer has another type than the question" in {
      val sameKey = Question.yesNo("Route?").build("route")
      val e = intercept[IllegalArgumentException](judgment.answer(sameKey))
      e.getMessage should include("[ChoiceAnswer]")
    }
  }
}
