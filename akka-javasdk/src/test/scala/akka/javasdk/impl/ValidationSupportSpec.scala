/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.Validations.Invalid
import akka.javasdk.impl.Validations.Valid
import akka.javasdk.impl.Validations.Validation
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers

trait ValidationSupportSpec extends Matchers {

  implicit class ValidationOps(val validation: Validation) {
    def expectInvalid(message: String): Assertion =
      validation match {
        case Invalid(messages) if messages.exists(_.contains(message)) => true shouldBe true
        case Invalid(messages) =>
          messages shouldBe include(message)
        case Valid => fail("Expected Invalid result")
      }
  }
}
