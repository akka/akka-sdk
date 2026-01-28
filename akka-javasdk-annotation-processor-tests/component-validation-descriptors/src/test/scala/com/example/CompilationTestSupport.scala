/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import java.nio.file.Files
import java.nio.file.Paths
import javax.tools._

import scala.jdk.CollectionConverters._
import scala.util.Using

import org.scalatest.matchers.should.Matchers

/**
 * Trait providing common compilation testing utilities for annotation processor validation tests.
 */
trait CompilationTestSupport extends Matchers {

  case class CompilationResult(success: Boolean, diagnostics: List[Diagnostic[_ <: JavaFileObject]])

  /**
   * Compiles a test source file using the ComponentValidationProcessor.
   *
   * @param relativePath
   *   path to the test source file relative to src/test/resources/test-sources
   * @return
   *   CompilationResult containing success status and diagnostics
   */
  protected def compileTestSource(relativePath: String): CompilationResult = {
    val compiler = ToolProvider.getSystemJavaCompiler
    require(compiler != null, "Java compiler not available. Make sure you're running on a JDK, not a JRE.")

    val diagnosticCollector = new DiagnosticCollector[JavaFileObject]()
    val fileManager = compiler.getStandardFileManager(diagnosticCollector, null, null)

    // Get the test source file
    val testSourcesDir = Paths.get("src/test/resources/test-sources")
    val sourceFile = testSourcesDir.resolve(relativePath).toFile
    require(sourceFile.exists(), s"Test source file not found: ${sourceFile.getAbsolutePath}")

    // Collect all files to compile (include dependency entity files)
    val filesToCompile = collection.mutable.ListBuffer(sourceFile)

    // Add entity/workflow files that are commonly referenced
    val supportFiles =
      List("valid/SimpleKeyValueEntity.java", "valid/SimpleWorkflow.java", "valid/SimpleEventSourcedEntity.java")

    supportFiles.foreach { supportFile =>
      val file = testSourcesDir.resolve(supportFile).toFile
      if (file.exists()) {
        filesToCompile += file
      }
    }

    // Get compilation units
    val compilationUnits = fileManager.getJavaFileObjectsFromFiles(filesToCompile.asJava)

    // Create output directory for compiled classes
    val outputDir = Files.createTempDirectory("component-validation-test")
    outputDir.toFile.deleteOnExit()

    // Set up compiler options
    val options = List(
      "-d",
      outputDir.toString,
      "-Werror", // Treat warnings as errors to catch raw types
      "-Xlint:rawtypes", // Warn about raw type usage
      "-processor",
      "akka.javasdk.tooling.processor.ComponentValidationProcessor",
      "-classpath",
      System.getProperty("java.class.path")).asJava

    // Compile
    val task = compiler.getTask(
      null, // Use default writer for additional output
      fileManager,
      diagnosticCollector,
      options,
      null,
      compilationUnits)

    val success = task.call()

    Using.resource(fileManager) { _ =>
      CompilationResult(success, diagnosticCollector.getDiagnostics.asScala.toList)
    }
  }

  /**
   * Asserts that compilation succeeded with no errors.
   */
  protected def assertCompilationSuccess(result: CompilationResult): Unit = {
    withClue(result.diagnostics) {
      result.success shouldBe true
      result.diagnostics.filter(_.getKind == Diagnostic.Kind.ERROR) shouldBe empty
    }
  }

  /**
   * Asserts that compilation failed with expected error messages.
   *
   * @param result
   *   compilation result
   * @param expectedMessages
   *   expected substrings in error messages
   */
  protected def assertCompilationFailure(result: CompilationResult, expectedMessages: String*): Unit = {
    withClue(result.diagnostics) {
      result.success shouldBe false

      val errors = result.diagnostics.filter(_.getKind == Diagnostic.Kind.ERROR)
      errors should not be empty

      val errorMessages = errors.map(_.getMessage(null)).mkString(" ")
      expectedMessages.foreach { expected =>
        errorMessages should include(expected)
      }
    }
  }

  /**
   * Asserts that compilation failed and does not contain unexpected error messages.
   *
   * @param result
   *   compilation result
   * @param notExpectedMessages
   *   unexpected substrings in error messages
   */
  protected def assertCompilationFailureNotContain(result: CompilationResult, notExpectedMessages: String*): Unit = {
    withClue(result.diagnostics) {
      result.success shouldBe false

      val errors = result.diagnostics.filter(_.getKind == Diagnostic.Kind.ERROR)
      errors should not be empty

      val errorMessages = errors.map(_.getMessage(null)).mkString(" ")
      notExpectedMessages.foreach { expected =>
        errorMessages shouldNot include(expected)
      }
    }
  }
}
