/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import scala.annotation.nowarn

import akka.javasdk.annotations.Component
import akka.javasdk.agent.Agent
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AgentValidationSpec extends AnyWordSpec with Matchers {

  @nowarn("cat=deprecation")
  @akka.javasdk.annotations.AgentDescription(name = "Agent", description = "desc", role = "role1")
  @akka.javasdk.annotations.AgentRole("role2")
  class ConflictingRolesAgent extends Agent

  @nowarn("cat=deprecation")
  @akka.javasdk.annotations.AgentDescription(name = "AgentName", description = "desc")
  @Component(id = "id", name = "OtherName")
  class ConflictingNameAgent extends Agent

  @nowarn("cat=deprecation")
  @akka.javasdk.annotations.AgentDescription(name = "Agent", description = "desc1")
  @Component(id = "id", description = "desc2")
  class ConflictingDescriptionAgent extends Agent

  @nowarn("cat=deprecation")
  @akka.javasdk.annotations.AgentDescription(name = "", description = "desc")
  class EmptyNameAgent extends Agent

  @nowarn("cat=deprecation")
  @akka.javasdk.annotations.AgentDescription(name = "Agent", description = "")
  class EmptyDescriptionAgent extends Agent

  @nowarn("cat=deprecation")
  @akka.javasdk.annotations.AgentDescription(name = "   ", description = "desc")
  class BlankNameAgent extends Agent

  @nowarn("cat=deprecation")
  @akka.javasdk.annotations.AgentDescription(name = "Agent", description = "   ")
  class BlankDescriptionAgent extends Agent

  @nowarn("cat=deprecation")
  @akka.javasdk.annotations.AgentDescription(name = "Agent", description = "desc")
  class ValidAgentDescriptionAgent extends Agent {
    def handle(): Agent.Effect[String] = effects.reply("ok")
  }

  @Component(id = "agent-id", name = "AgentName", description = "Agent description")
  class ValidComponentAgent extends Agent {
    def handle(): Agent.Effect[String] = effects.reply("ok")
  }

  class NoCommandHandlerAgent extends Agent

  class MultipleCommandHandlerAgent extends Agent {
    def handleFirst(): Agent.Effect[String] = effects.reply("first")
    def handleSecond(): Agent.Effect[String] = effects.reply("second")
  }

  class AgentWithStreamEffect extends Agent {
    def handleStream(): Agent.StreamEffect = effects.ignore()
  }

  "Agent validation" should {

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

    "return Invalid if @AgentDescription.name is blank and @Component is not used" in {
      val result = Validations.validate(classOf[BlankNameAgent])
      result.isValid shouldBe false
      result match {
        case Validations.Invalid(messages) =>
          messages.exists(_.contains(
            "@AgentDescription.name is empty. Remove @AgentDescription annotation and use only @Component.")) shouldBe true
        case _ => fail("Expected Invalid result")
      }
    }

    "return Invalid if @AgentDescription.description is blank and @Component is not used" in {
      val result = Validations.validate(classOf[BlankDescriptionAgent])
      result.isValid shouldBe false
      result match {
        case Validations.Invalid(messages) =>
          messages.exists(_.contains(
            "@AgentDescription.description is empty.Remove @AgentDescription annotation and use only @Component.")) shouldBe true
        case _ => fail("Expected Invalid result")
      }
    }

    "return Valid for properly configured @AgentDescription" in {
      val result = Validations.validate(classOf[ValidAgentDescriptionAgent])
      result.isValid shouldBe true
    }

    "return Valid for properly configured @Component on Agent" in {
      val result = Validations.validate(classOf[ValidComponentAgent])
      result.isValid shouldBe true
    }

    "return Invalid if Agent has no command handler" in {
      val result = Validations.validate(classOf[NoCommandHandlerAgent])
      result.isValid shouldBe false
      result match {
        case Validations.Invalid(messages) =>
          messages.exists(
            _.contains("has 0 command handlers. There must be one public method returning Agent.Effect")) shouldBe true
        case _ => fail("Expected Invalid result")
      }
    }

    "return Invalid if Agent has multiple command handlers" in {
      val result = Validations.validate(classOf[MultipleCommandHandlerAgent])
      result.isValid shouldBe false
      result match {
        case Validations.Invalid(messages) =>
          messages.exists(
            _.contains("has 2 command handlers. There must be one public method returning Agent.Effect")) shouldBe true
        case _ => fail("Expected Invalid result")
      }
    }

    "return Valid for Agent with StreamEffect" in {
      val result = Validations.validate(classOf[AgentWithStreamEffect])
      result.isValid shouldBe true
    }
  }
}
