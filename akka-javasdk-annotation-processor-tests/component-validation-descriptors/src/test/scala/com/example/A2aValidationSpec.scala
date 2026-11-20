/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.example.CompilationTestSupport.CompileTimeValidation
import com.example.CompilationTestSupport.RuntimeValidation
import com.example.CompilationTestSupport.ValidationMode
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CompileTimeA2aValidationSpec extends AbstractA2aValidationSpec(CompileTimeValidation) with Matchers {

  "ComponentValidationProcessor" should {

    "reject a second @A2aEndpoint in a service" in {
      val errors = compileTimeErrorMessages("invalid/TwoA2aEndpoints.java")
      errors.filter(_.contains("A service can have at most one @A2aEndpoint")) should have size 2
      errors.head should include("com.example.TwoA2aEndpoints.First, com.example.TwoA2aEndpoints.Second")
    }

    "reject a skill id used by two agents" in {
      val errors = compileTimeErrorMessages("invalid/DuplicateA2aSkillIds.java")
      errors should contain(
        "On 'com.example.DuplicateA2aSkillIds.First': @A2aSkill id [answer] is not unique. It is also used by [com.example.DuplicateA2aSkillIds.Second].")
      errors should contain(
        "On 'com.example.DuplicateA2aSkillIds.Second': @A2aSkill id [answer] is not unique. It is also used by [com.example.DuplicateA2aSkillIds.First].")
    }
  }
}

class RuntimeA2aValidationSpec extends AbstractA2aValidationSpec(RuntimeValidation)

abstract class AbstractA2aValidationSpec(val validationMode: ValidationMode)
    extends AnyWordSpec
    with CompilationTestSupport {

  s"A2A validation ($validationMode)" should {

    "accept an A2A endpoint with @Acl and @JWT, and an agent with several skills" in {
      assertValid("valid/ValidA2aEndpoint.java")
    }

    "reject an A2A endpoint that does not extend AbstractA2aEndpoint" in {
      assertInvalid(
        "invalid/A2aEndpointWithoutBaseClass.java",
        "A class annotated with @A2aEndpoint must extend AbstractA2aEndpoint.",
        "@A2aEndpoint name must not be empty.",
        "@A2aEndpoint path must start with '/'.")
    }

    "reject an invalid or repeated skill id" in {
      assertInvalid(
        "invalid/A2aSkillWithInvalidId.java",
        "@A2aSkill id [check claim!] is invalid",
        "@A2aSkill id [explain] is used more than once.")
    }

    "reject @A2aSkill on a class that is not an agent" in {
      assertInvalid("invalid/A2aSkillOnEntity.java", "@A2aSkill is only allowed on classes that extend Agent.")
    }
  }
}
