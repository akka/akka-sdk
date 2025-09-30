/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import scala.annotation.nowarn

import akka.javasdk.annotations.Component
import akka.javasdk.annotations.ComponentId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ValidationsSpec extends AnyWordSpec with Matchers {

  @nowarn("cat=deprecation")
  @Component(id = "new-id")
  @ComponentId("old-id")
  class ConflictingComponent

  "Validations.mustHaveValidComponentId" should {
    "return Invalid if both @Component and @ComponentId are present" in {
      val result = Validations.validate(classOf[ConflictingComponent])
      result.isValid shouldBe false
      result match {
        case Validations.Invalid(messages) =>
          messages.exists(_.contains("has both @Component and deprecated @ComponentId annotations")) shouldBe true
          messages.exists(_.contains("remove @ComponentId and use only @Component")) shouldBe true
        case _ => fail("Expected Invalid result")
      }
    }
  }
}
