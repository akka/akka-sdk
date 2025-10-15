/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.Done

import scala.annotation.nowarn
import akka.javasdk.annotations.Component
import akka.javasdk.annotations.ComponentId
import akka.javasdk.keyvalueentity.KeyValueEntity
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import akka.javasdk.impl.ComponentAnnValidationSpec.BlankComponentIdComponent
import akka.javasdk.impl.ComponentAnnValidationSpec.BlankDeprecatedComponentIdComponent
import akka.javasdk.impl.ComponentAnnValidationSpec.ConflictingComponent
import akka.javasdk.impl.ComponentAnnValidationSpec.EmptyComponentIdComponent
import akka.javasdk.impl.ComponentAnnValidationSpec.EmptyDeprecatedComponentIdComponent
import akka.javasdk.impl.ComponentAnnValidationSpec.InvalidPipeComponentIdComponent
import akka.javasdk.impl.ComponentAnnValidationSpec.InvalidPipeDeprecatedComponentIdComponent
import akka.javasdk.impl.ComponentAnnValidationSpec.NoComponentAnnotationComponent
import akka.javasdk.impl.ComponentAnnValidationSpec.ValidComponentIdComponent
import akka.javasdk.impl.ComponentAnnValidationSpec.ValidDeprecatedComponentIdComponent
import akka.javasdk.keyvalueentity.KeyValueEntity.Effect

object ComponentAnnValidationSpec {
  @nowarn("cat=deprecation")
  @Component(id = "new-id")
  @ComponentId("old-id")
  class ConflictingComponent extends KeyValueEntity[String] {
    def done(): Effect[Done] = ???
  }

  @Component(id = "")
  class EmptyComponentIdComponent extends KeyValueEntity[String] {
    def done(): Effect[Done] = ???
  }

  @Component(id = "   ")
  class BlankComponentIdComponent extends KeyValueEntity[String] {
    def done(): Effect[Done] = ???
  }

  @Component(id = "invalid|pipe")
  class InvalidPipeComponentIdComponent extends KeyValueEntity[String] {
    def done(): Effect[Done] = ???
  }

  @nowarn("cat=deprecation")
  @ComponentId("")
  class EmptyDeprecatedComponentIdComponent extends KeyValueEntity[String] {
    def done(): Effect[Done] = ???
  }

  @nowarn("cat=deprecation")
  @ComponentId("   ")
  class BlankDeprecatedComponentIdComponent extends KeyValueEntity[String] {
    def done(): Effect[Done] = ???
  }

  @nowarn("cat=deprecation")
  @ComponentId("invalid|pipe")
  class InvalidPipeDeprecatedComponentIdComponent extends KeyValueEntity[String] {
    def done(): Effect[Done] = ???
  }

  @Component(id = "valid-id")
  class ValidComponentIdComponent extends KeyValueEntity[String] {
    def done(): Effect[Done] = ???
  }

  @nowarn("cat=deprecation")
  @ComponentId("valid-id")
  class ValidDeprecatedComponentIdComponent extends KeyValueEntity[String] {
    def done(): Effect[Done] = ???
  }

  class NoComponentAnnotationComponent extends KeyValueEntity[String] {
    def done(): Effect[Done] = ???
  }
}

class ComponentAnnValidationSpec extends AnyWordSpec with Matchers with ValidationSupportSpec {

  "Validations.mustHaveValidComponentId" should {

    "return Invalid if both @Component and @ComponentId are defined" in {
      val result = Validations.validate(classOf[ConflictingComponent])
      result.expectInvalid("has both @Component and deprecated @ComponentId annotations")
      result.expectInvalid("remove @ComponentId and use only @Component")
    }

    "return Invalid if @Component id is empty" in {
      Validations
        .validate(classOf[EmptyComponentIdComponent])
        .expectInvalid("@Component id is empty, must be a non-empty string")
    }

    "return Invalid if @Component id is blank" in {
      Validations
        .validate(classOf[BlankComponentIdComponent])
        .expectInvalid("@Component id is empty, must be a non-empty string")
    }

    "return Invalid if @Component id contains pipe character" in {
      Validations
        .validate(classOf[InvalidPipeComponentIdComponent])
        .expectInvalid("@Component id must not contain the pipe character")
    }

    "return Invalid if @ComponentId is empty" in {
      Validations
        .validate(classOf[EmptyDeprecatedComponentIdComponent])
        .expectInvalid("@ComponentId name is empty, must be a non-empty string")
    }

    "return Invalid if @ComponentId is blank" in {
      Validations
        .validate(classOf[BlankDeprecatedComponentIdComponent])
        .expectInvalid("@ComponentId name is empty, must be a non-empty string")
    }

    "return Invalid if @ComponentId contains pipe character" in {
      Validations
        .validate(classOf[InvalidPipeDeprecatedComponentIdComponent])
        .expectInvalid("@ComponentId must not contain the pipe character")
    }

    "return Valid for valid @Component id" in {
      val result = Validations.validate(classOf[ValidComponentIdComponent])
      result.isValid shouldBe true
    }

    "return Valid for valid @ComponentId" in {
      val result = Validations.validate(classOf[ValidDeprecatedComponentIdComponent])
      result.isValid shouldBe true
    }

    "return Valid when no component annotation is present (disabled component)" in {
      val result = Validations.validate(classOf[NoComponentAnnotationComponent])
      result.isValid shouldBe true
    }
  }
}
