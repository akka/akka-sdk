/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.runtime.sdk.spi.SpiDataSanitizer.SpiPredefinedSanitizer
import akka.runtime.sdk.spi.SpiDataSanitizer.SpiRegexSanitizer
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SanitizationSpec extends AnyWordSpec with Matchers {

  "Loading the Sanitization settings from config" should {

    "load predefined" in {
      val result = Sanitization.loadSettings(ConfigFactory.load(ConfigFactory.parseString("""
        akka.javasdk.sanitization {
          predefined-sanitizers = ["CREDIT_CARD", "IBAN"]
        }
        """)))

      result.sanitizers.map(entry => entry.getClass -> entry.name) shouldEqual Seq(
        classOf[SpiPredefinedSanitizer] -> "CREDIT_CARD",
        classOf[SpiPredefinedSanitizer] -> "IBAN")
    }

    "load user defined regexes" in {
      val result = Sanitization.loadSettings(ConfigFactory.load(ConfigFactory.parseString("""
        akka.javasdk.sanitization {
          regex-sanitizers {
            "warm-colors" = { pattern = "(?i)(red|orange|yellow)" }
          }
        }
        """)))

      result.sanitizers.map(entry => (entry.name, entry.asInstanceOf[SpiRegexSanitizer].pattern.regex)) shouldEqual Seq(
        "warm-colors" -> "(?i)(red|orange|yellow)")
    }

  }

}
