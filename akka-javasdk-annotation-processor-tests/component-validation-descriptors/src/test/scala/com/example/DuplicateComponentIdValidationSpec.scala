/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import scala.jdk.CollectionConverters._

import akka.javasdk.tooling.validation.Validation
import akka.javasdk.tooling.validation.Validations
import akka.javasdk.validation.ast.runtime.RuntimeTypeDef
import com.example.CompilationTestSupport.CompileTimeValidation
import com.example.CompilationTestSupport.RuntimeValidation
import com.example.CompilationTestSupport.ValidationMode
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CompileTimeDuplicateComponentIdValidationSpec
    extends AbstractDuplicateComponentIdValidationSpec(CompileTimeValidation) {

  "ComponentValidationProcessor" should {

    "accept duplicate ids when application.conf allows them" in {
      assertCompilesWithApplicationConf(
        "invalid/DuplicateComponentIds.java",
        "akka.javasdk.allow-duplicate-component-ids = true")
    }

    "resolve an include in application.conf next to it in the class output" in {
      assertCompilesWithClassOutputFiles(
        "invalid/DuplicateComponentIds.java",
        Map(
          "application.conf" -> """include required("common.conf")""",
          "common.conf" -> "akka.javasdk.allow-duplicate-component-ids = true"))
    }

    "let a system property override application.conf" in {
      System.setProperty("akka.javasdk.allow-duplicate-component-ids", "false")
      ConfigFactory.invalidateCaches()

      try {
        assertCompilationFailsWithApplicationConf(
          "invalid/DuplicateComponentIds.java",
          "akka.javasdk.allow-duplicate-component-ids = true",
          "On 'com.example.DuplicateComponentIds.First': @Component id [duplicated-id] is not unique. It is also used by [com.example.DuplicateComponentIds.Second].",
          "On 'com.example.DuplicateComponentIds.Second': @Component id [duplicated-id] is not unique. It is also used by [com.example.DuplicateComponentIds.First].")
      } finally {
        System.clearProperty("akka.javasdk.allow-duplicate-component-ids")
        ConfigFactory.invalidateCaches()
      }
    }
  }
}

class RuntimeDuplicateComponentIdValidationSpec extends AbstractDuplicateComponentIdValidationSpec(RuntimeValidation) {

  "Validations.duplicateComponentIdErrors" should {

    "return one error per class, ordered by id and class name" in {
      val typeDefs = loadRuntimeClasses("invalid/TripleDuplicateComponentIds.java").reverse.map(new RuntimeTypeDef(_))

      Validations.duplicateComponentIdErrors(typeDefs.asJava, false).asScala.toList shouldBe List(
        "com.example.TripleDuplicateComponentIds.A" ->
        "On 'com.example.TripleDuplicateComponentIds.A': @Component id [triple-id] is not unique. It is also used by [com.example.TripleDuplicateComponentIds.B, com.example.TripleDuplicateComponentIds.C].",
        "com.example.TripleDuplicateComponentIds.B" ->
        "On 'com.example.TripleDuplicateComponentIds.B': @Component id [triple-id] is not unique. It is also used by [com.example.TripleDuplicateComponentIds.A, com.example.TripleDuplicateComponentIds.C].",
        "com.example.TripleDuplicateComponentIds.C" ->
        "On 'com.example.TripleDuplicateComponentIds.C': @Component id [triple-id] is not unique. It is also used by [com.example.TripleDuplicateComponentIds.A, com.example.TripleDuplicateComponentIds.B].")
    }
  }

  "Validations.uniqueComponentIds" should {

    "accept duplicate ids when allowDuplicates is true" in {
      val typeDefs = loadRuntimeClasses("invalid/DuplicateComponentIds.java").map(new RuntimeTypeDef(_))

      Validations.uniqueComponentIds(typeDefs.asJava, true) shouldBe Validation.Valid.instance()
    }
  }
}

abstract class AbstractDuplicateComponentIdValidationSpec(val validationMode: ValidationMode)
    extends AnyWordSpec
    with Matchers
    with CompilationTestSupport {

  s"Duplicate component id validation ($validationMode)" should {

    "reject two components with the same id" in {
      assertUniqueIdsInvalid(
        "invalid/DuplicateComponentIds.java",
        "On 'com.example.DuplicateComponentIds.First': @Component id [duplicated-id] is not unique. It is also used by [com.example.DuplicateComponentIds.Second].",
        "On 'com.example.DuplicateComponentIds.Second': @Component id [duplicated-id] is not unique. It is also used by [com.example.DuplicateComponentIds.First].")

      if (validationMode == CompileTimeValidation) {
        val errors = compileTimeErrorMessages("invalid/DuplicateComponentIds.java")
        withClue(errors) {
          errors.count(_.contains("is not unique")) shouldBe 2
        }
      }
    }

    "reject components of different types with the same id" in {
      assertUniqueIdsInvalid(
        "invalid/DuplicateComponentIdsAcrossTypes.java",
        "@Component id [shared-id] is not unique",
        "com.example.DuplicateComponentIdsAcrossTypes.Entity",
        "com.example.DuplicateComponentIdsAcrossTypes.Action")
    }

    "accept components with distinct ids" in {
      assertUniqueIdsValid("valid/DistinctComponentIds.java")
    }
  }
}
