/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import scala.annotation.nowarn

import akka.javasdk.annotations.Component
import akka.javasdk.agent.Agent
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import akka.javasdk.impl.AgentValidationSpec.AgentWithStreamEffect
import akka.javasdk.impl.AgentValidationSpec.AgentWithTooManyArgsCommandHandler
import akka.javasdk.impl.AgentValidationSpec.AgentWithValidNoArgCommandHandler
import akka.javasdk.impl.AgentValidationSpec.AgentWithValidSingleArgCommandHandler
import akka.javasdk.impl.AgentValidationSpec.BlankDescriptionAgent
import akka.javasdk.impl.AgentValidationSpec.BlankNameAgent
import akka.javasdk.impl.AgentValidationSpec.ConflictingDescriptionAgent
import akka.javasdk.impl.AgentValidationSpec.ConflictingNameAgent
import akka.javasdk.impl.AgentValidationSpec.ConflictingRolesAgent
import akka.javasdk.impl.AgentValidationSpec.EmptyDescriptionAgent
import akka.javasdk.impl.AgentValidationSpec.EmptyNameAgent
import akka.javasdk.impl.AgentValidationSpec.MultipleCommandHandlerAgent
import akka.javasdk.impl.AgentValidationSpec.NoCommandHandlerAgent
import akka.javasdk.impl.AgentValidationSpec.ValidAgentDescriptionAgent
import akka.javasdk.impl.AgentValidationSpec.ValidComponentAgent

object AgentValidationSpec {
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
    def handleStream(): Agent.StreamEffect = streamEffects().reply("one")
  }

  class AgentWithTooManyArgsCommandHandler extends Agent {
    def handle(arg1: String, arg2: String): Agent.Effect[String] = effects.reply("ok")
  }

  class AgentWithValidSingleArgCommandHandler extends Agent {
    def handle(request: String): Agent.Effect[String] = effects.reply(request)
  }

  class AgentWithValidNoArgCommandHandler extends Agent {
    def handle(): Agent.Effect[String] = effects.reply("ok")
  }
}

class AgentValidationSpec extends AnyWordSpec with Matchers with ValidationSupportSpec {

  "Agent validation" should {

    "return Invalid if both @AgentDescription.role and @AgentRole are defined" in {
      val result = Validations.validate(classOf[ConflictingRolesAgent])
      result.expectInvalid("Both @AgentDescription.role and @AgentRole are defined.")
      result.expectInvalid("Remove @AgentDescription.role and use only @AgentRole.")
    }

    "return Invalid if both @AgentDescription.name and @Component.name are defined" in {
      val result = Validations.validate(classOf[ConflictingNameAgent])
      result.expectInvalid("Both @AgentDescription.name and @Component.name are defined.")
      result.expectInvalid("Remove @AgentDescription.name and use only @Component.name.")
    }

    "return Invalid if both @AgentDescription.description and @Component.description are defined" in {
      val result = Validations.validate(classOf[ConflictingDescriptionAgent])
      result.expectInvalid("Both @AgentDescription.description and @Component.description are defined.")
      result.expectInvalid("Remove @AgentDescription.description and use only @Component.description")
    }

    "return Invalid if @AgentDescription.name is empty and @Component is not used" in {
      Validations
        .validate(classOf[EmptyNameAgent])
        .expectInvalid("@AgentDescription.name is empty. Remove @AgentDescription annotation and use only @Component.")
    }

    "return Invalid if @AgentDescription.description is empty and @Component is not used" in {
      Validations
        .validate(classOf[EmptyDescriptionAgent])
        .expectInvalid(
          "@AgentDescription.description is empty.Remove @AgentDescription annotation and use only @Component.")
    }

    "return Invalid if @AgentDescription.name is blank and @Component is not used" in {
      Validations
        .validate(classOf[BlankNameAgent])
        .expectInvalid("@AgentDescription.name is empty. Remove @AgentDescription annotation and use only @Component.")
    }

    "return Invalid if @AgentDescription.description is blank and @Component is not used" in {
      Validations
        .validate(classOf[BlankDescriptionAgent])
        .expectInvalid(
          "@AgentDescription.description is empty.Remove @AgentDescription annotation and use only @Component.")
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
      Validations
        .validate(classOf[NoCommandHandlerAgent])
        .expectInvalid("has 0 command handlers. There must be one public method returning Agent.Effect")
    }

    "return Invalid if Agent has multiple command handlers" in {
      Validations
        .validate(classOf[MultipleCommandHandlerAgent])
        .expectInvalid("has 2 command handlers. There must be one public method returning Agent.Effect")
    }

    "return Valid for Agent with StreamEffect" in {
      val result = Validations.validate(classOf[AgentWithStreamEffect])
      result.isValid shouldBe true
    }

    "return Invalid if Agent command handler has more than 1 argument" in {
      val result = Validations.validate(classOf[AgentWithTooManyArgsCommandHandler])
      result.expectInvalid("Method [handle] must have zero or one argument")
      result.expectInvalid("If you need to pass more arguments, wrap them in a class")
    }

    "return Valid for Agent command handler with single argument" in {
      val result = Validations.validate(classOf[AgentWithValidSingleArgCommandHandler])
      result.isValid shouldBe true
    }

    "return Valid for Agent command handler with no arguments" in {
      val result = Validations.validate(classOf[AgentWithValidNoArgCommandHandler])
      result.isValid shouldBe true
    }
  }
}
