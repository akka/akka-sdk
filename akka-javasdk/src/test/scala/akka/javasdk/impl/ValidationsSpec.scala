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

  @nowarn("cat=deprecation")
  @akka.javasdk.annotations.AgentDescription(name = "Agent", description = "desc", role = "role1")
  @akka.javasdk.annotations.AgentRole("role2")
  class ConflictingRolesAgent extends akka.javasdk.agent.Agent

  @nowarn("cat=deprecation")
  @akka.javasdk.annotations.AgentDescription(name = "AgentName", description = "desc")
  @akka.javasdk.annotations.Component(id = "id", name = "OtherName")
  class ConflictingNameAgent extends akka.javasdk.agent.Agent

  @nowarn("cat=deprecation")
  @akka.javasdk.annotations.AgentDescription(name = "Agent", description = "desc1")
  @akka.javasdk.annotations.Component(id = "id", description = "desc2")
  class ConflictingDescriptionAgent extends akka.javasdk.agent.Agent

  @nowarn("cat=deprecation")
  @akka.javasdk.annotations.AgentDescription(name = "", description = "desc")
  class EmptyNameAgent extends akka.javasdk.agent.Agent

  @nowarn("cat=deprecation")
  @akka.javasdk.annotations.AgentDescription(name = "Agent", description = "")
  class EmptyDescriptionAgent extends akka.javasdk.agent.Agent

  "Validations.mustHaveValidComponentId" should {
    "return Invalid if both @Component and @ComponentId are define" in {
      val result = Validations.validate(classOf[ConflictingComponent])
      result.isValid shouldBe false
      result match {
        case Validations.Invalid(messages) =>
          messages.exists(_.contains("has both @Component and deprecated @ComponentId annotations")) shouldBe true
          messages.exists(_.contains("remove @ComponentId and use only @Component")) shouldBe true
        case _ => fail("Expected Invalid result")
      }
    }

    "return Invalid if both @AgentDescription.role and @AgentRole are defined" in {
      val result = Validations.validate(classOf[ConflictingRolesAgent])
      result.isValid shouldBe false
      result match {
        case Validations.Invalid(messages) =>
          messages.exists(_.contains("Both @AgentDescription.role and @AgentRole are defined. ")) shouldBe true
          messages.exists(_.contains("Remove @AgentDescription.role and use only @AgentRole.")) shouldBe true
        case _ => fail("Expected Invalid result")
      }
    }

    "return Invalid if both @AgentDescription.name and @Component.name are defined" in {
      val result = Validations.validate(classOf[ConflictingNameAgent])
      result.isValid shouldBe false
      result match {
        case Validations.Invalid(messages) =>
          messages.exists(_.contains("Both @AgentDescription.name and @Component.name are defined.")) shouldBe true
          messages.exists(_.contains("Remove @AgentDescription.name and use only @Component.name.")) shouldBe true
        case _ => fail("Expected Invalid result")
      }
    }

    "return Invalid if both @AgentDescription.description and @Component.description are defined" in {
      val result = Validations.validate(classOf[ConflictingDescriptionAgent])
      result.isValid shouldBe false
      result match {
        case Validations.Invalid(messages) =>
          messages.exists(
            _.contains("Both @AgentDescription.description and @Component.description are defined.")) shouldBe true
          messages.exists(
            _.contains("Remove @AgentDescription.description and use only @Component.description")) shouldBe true
        case _ => fail("Expected Invalid result")
      }
    }

    "return Invalid if @AgentDescription.name is empty and @Component is not used" in {
      val result = Validations.validate(classOf[EmptyNameAgent])
      result.isValid shouldBe false
      result match {
        case Validations.Invalid(messages) =>
          messages.exists(_.contains(
            "@AgentDescription.name is empty. Remove @AgentDescription annotation and use only @Component.")) shouldBe true
        case _ => fail("Expected Invalid result")
      }
    }

    "return Invalid if @AgentDescription.description is empty and @Component is not used" in {
      val result = Validations.validate(classOf[EmptyDescriptionAgent])
      result.isValid shouldBe false
      result match {
        case Validations.Invalid(messages) =>
          messages.exists(_.contains(
            "@AgentDescription.description is empty.Remove @AgentDescription annotation and use only @Component.")) shouldBe true
        case _ => fail("Expected Invalid result")
      }
    }
  }
}
