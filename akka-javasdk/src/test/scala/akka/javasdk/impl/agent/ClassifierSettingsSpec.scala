/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object ClassifierSettingsSpec {
  private val config = ConfigFactory.parseString(s"""
    akka.javasdk.agent.classifiers {
      "toxicity" {
        class = "test.ToxicityClassifier"
        threshold = 0.8
      }

      "bias" {
        class = "test.BiasClassifier"
      }
    }
    """)
}

class ClassifierSettingsSpec extends AnyWordSpec with Matchers {
  import ClassifierSettingsSpec._

  "The ClassifierSettings" should {
    "load from config" in {
      val settings = ClassifierSettings(config.getConfig("akka.javasdk.agent.classifiers"))
      settings.configuredClassifiers.size shouldBe 2

      val toxicity = settings.configuredClassifiers.find(_.name == "toxicity").get
      toxicity.implementationClass shouldBe "test.ToxicityClassifier"
      toxicity.config.getDouble("threshold") shouldBe 0.8

      val bias = settings.configuredClassifiers.find(_.name == "bias").get
      bias.implementationClass shouldBe "test.BiasClassifier"
    }

    "accept an empty classifiers section" in {
      val settings = ClassifierSettings(ConfigFactory.parseString("""{}"""))
      settings.configuredClassifiers shouldBe empty
    }

    "reject a configured classifier missing the class key" in {
      val faulty = ConfigFactory.parseString("""
        "no class" {
          threshold = 0.5
        }
        """)
      val ex = intercept[IllegalArgumentException] {
        ClassifierSettings(faulty)
      }
      ex.getMessage should include("no class")
      ex.getMessage should include("class")
    }

    "reject a classifier entry that is not a config object" in {
      val faulty = ConfigFactory.parseString("""
        "not an object" = 42
        """)
      val ex = intercept[IllegalArgumentException] {
        ClassifierSettings(faulty)
      }
      ex.getMessage should include("not an object")
      ex.getMessage should include("config object")
    }
  }
}
