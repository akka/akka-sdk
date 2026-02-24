/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import scala.jdk.CollectionConverters._
import scala.util.Using

import akka.javasdk.tooling.validation.Validation
import akka.javasdk.validation.ast.runtime.RuntimeTypeDef
import com.example.CompilationTestSupport.CompileTimeValidation
import com.example.CompilationTestSupport.RuntimeValidation
import com.example.CompilationTestSupport.ValidationMode
import javax.tools._
import org.scalatest.matchers.should.Matchers

object CompilationTestSupport {
  sealed trait ValidationMode

  object CompileTimeValidation extends ValidationMode {
    override def toString = "compile-time"
  }

  object RuntimeValidation extends ValidationMode {
    override def toString = "runtime"
  }
}

/**
 * Trait providing common compilation testing utilities for annotation processor validation tests.
 */
trait CompilationTestSupport extends Matchers {

  def validationMode: ValidationMode

  case class CompilationResult(success: Boolean, diagnostics: List[Diagnostic[_ <: JavaFileObject]])

  /**
   * Result from compiling test sources for runtime validation testing.
   *
   * @param outputDir
   *   the directory containing compiled .class files
   * @param classLoader
   *   URLClassLoader configured to load classes from outputDir
   * @param diagnostics
   *   any compiler diagnostics (warnings/errors)
   */
  case class RuntimeCompilationResult(
      outputDir: Path,
      classLoader: URLClassLoader,
      diagnostics: List[Diagnostic[_ <: JavaFileObject]])

  /**
   * Compiles a test source file using the ComponentValidationProcessor.
   *
   * @param relativePath
   *   path to the test source file relative to src/test/resources/test-sources
   * @return
   *   CompilationResult containing success status and diagnostics
   */
  protected def compileTestSource(relativePath: String): CompilationResult = {
    compileWithProcessor(relativePath, "akka.javasdk.tooling.processor.ComponentValidationProcessor")
  }

  /**
   * Compiles a test source file using the specified annotation processor.
   *
   * @param relativePath
   *   path to the test source file relative to src/test/resources/test-sources
   * @param processorClass
   *   fully qualified class name of the annotation processor to use
   * @return
   *   CompilationResult containing success status and diagnostics
   */
  private def compileWithProcessor(relativePath: String, processorClass: String): CompilationResult = {
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
      "-parameters", // Preserve parameter names (needed for HTTP endpoint validation)
      "-Werror", // Treat warnings as errors to catch raw types
      "-Xlint:rawtypes", // Warn about raw type usage
      "-processor",
      processorClass,
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

  // ==================== Runtime Validation Support ====================

  /**
   * Compiles a test source file for runtime validation testing. Unlike compileTestSource, this method:
   *   - Does NOT run annotation processors
   *   - Generates .class files to a temp directory
   *   - Creates a URLClassLoader to load the compiled classes
   *
   * @param relativePath
   *   path to the test source file relative to src/test/resources/test-sources
   * @return
   *   RuntimeCompilationResult with outputDir, classLoader, and diagnostics
   */
  protected def compileTestSourceForRuntime(relativePath: String): RuntimeCompilationResult = {
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
    val outputDir = Files.createTempDirectory("runtime-validation-test")
    outputDir.toFile.deleteOnExit()

    // Set up compiler options - NO annotation processor, just compile
    val options = List(
      "-d",
      outputDir.toString,
      "-parameters", // Preserve parameter names (needed for HTTP endpoint validation)
      "-proc:none", // Disable annotation processing
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
      require(success, s"Runtime compilation failed: ${diagnosticCollector.getDiagnostics.asScala.mkString(", ")}")

      // Create a URLClassLoader that can load classes from the output directory
      val classLoader = new URLClassLoader(Array(outputDir.toUri.toURL), Thread.currentThread().getContextClassLoader)

      RuntimeCompilationResult(outputDir, classLoader, diagnosticCollector.getDiagnostics.asScala.toList)
    }
  }

  /**
   * Loads a compiled class from a RuntimeCompilationResult.
   *
   * @param result
   *   the runtime compilation result
   * @param className
   *   fully qualified class name (e.g., "com.example.ValidEventSourcedEntity")
   * @return
   *   the loaded Class object
   */
  protected def loadCompiledClass(result: RuntimeCompilationResult, className: String): Class[_] = {
    result.classLoader.loadClass(className)
  }

  /**
   * Runs runtime validation on a class and asserts it succeeds.
   *
   * @param clazz
   *   the class to validate
   */
  protected def assertRuntimeValidationSuccess(clazz: Class[_]): Unit = {
    val typeDef = new RuntimeTypeDef(clazz)
    val validation = akka.javasdk.tooling.validation.Validations.validateComponent(typeDef)
    validation match {
      case _: Validation.Valid =>
        () // success
      case invalid: Validation.Invalid =>
        fail(s"Runtime validation failed: ${invalid.messages().asScala.mkString(", ")}")
    }
  }

  /**
   * Runs runtime validation on a class and asserts it fails with expected messages.
   *
   * @param clazz
   *   the class to validate
   * @param expectedMessages
   *   expected substrings in error messages
   */
  protected def assertRuntimeValidationFailure(clazz: Class[_], expectedMessages: String*): Unit = {
    val typeDef = new RuntimeTypeDef(clazz)
    val validation = akka.javasdk.tooling.validation.Validations.validateComponent(typeDef)
    validation match {
      case _: Validation.Valid =>
        fail("Expected runtime validation to fail but it succeeded")
      case invalid: Validation.Invalid =>
        val allMessages = invalid.messages().asScala.mkString(" ")
        expectedMessages.foreach { expected =>
          withClue(s"Expected message '$expected' in: $allMessages") {
            allMessages should include(expected)
          }
        }
    }
  }

  /**
   * Helper to derive the expected class name from a file path. Reads the actual package declaration from the source
   * file to determine the correct fully qualified class name.
   *
   * @param relativePath
   *   path like "valid/ValidEventSourcedEntity.java"
   * @return
   *   fully qualified class name like "com.example.ValidEventSourcedEntity"
   */
  protected def classNameFromPath(relativePath: String): String = {
    val testSourcesDir = Paths.get("src/test/resources/test-sources")
    val sourceFile = testSourcesDir.resolve(relativePath)
    val fileName = Paths.get(relativePath).getFileName.toString
    val className = fileName.stripSuffix(".java")

    // Read the package declaration from the source file
    val packageName = scala.io.Source
      .fromFile(sourceFile.toFile)
      .getLines()
      .find(_.trim.startsWith("package "))
      .map(_.trim.stripPrefix("package ").stripSuffix(";").trim)
      .getOrElse("com.example")

    s"$packageName.$className"
  }

  /**
   * Tests both compile-time and runtime validation for a valid component. Both validations should succeed.
   *
   * @param relativePath
   *   path to the test source file relative to src/test/resources/test-sources
   */
  protected def assertValid(relativePath: String): Unit = {
    val className = classNameFromPath(relativePath)

    validationMode match {
      case CompileTimeValidation =>
        val compileResult = compileTestSource(relativePath)
        assertCompilationSuccess(compileResult)
      case RuntimeValidation =>
        val runtimeResult = compileTestSourceForRuntime(relativePath)
        val clazz = loadCompiledClass(runtimeResult, className)
        assertRuntimeValidationSuccess(clazz)
    }
  }

  /**
   * Tests both compile-time and runtime validation for an invalid component.
   *
   * This method ensures compile-time validation fails with the expected messages. For runtime validation:
   *   - If the code doesn't compile without annotation processor, runtime validation is skipped (expected for some
   *     invalid test cases)
   *   - If the code compiles, runtime validation is attempted but failures are logged rather than causing test failures
   *     (since runtime validation may not catch all errors that compile-time validation catches)
   *
   * Use `assertInvalidInBothModesStrict` if you want to REQUIRE both modes to catch the same validation errors.
   *
   * @param relativePath
   *   path to the test source file relative to src/test/resources/test-sources
   * @param expectedMessages
   *   expected substrings in error messages
   */
  protected def assertInvalid(relativePath: String, expectedMessages: String*): Unit = {
    val className = classNameFromPath(relativePath)

    validationMode match {
      case CompileTimeValidation =>
        // Compile-time validation
        val compileResult = compileTestSource(relativePath)
        assertCompilationFailure(compileResult, expectedMessages: _*)
      case RuntimeValidation =>
        // Runtime validation
        val runtimeResult = compileTestSourceForRuntime(relativePath)
        val clazz = loadCompiledClass(runtimeResult, className)
        val typeDef = new RuntimeTypeDef(clazz)
        val validation = akka.javasdk.tooling.validation.Validations.validateComponent(typeDef)
        validation match {
          case _: Validation.Valid =>
            fail(s"Expected validation errors but got Valid. Expected: ${expectedMessages.mkString(", ")}")
          case invalid: Validation.Invalid =>
            val allMessages = invalid.messages().asScala.mkString(" ")
            val missingMessages = expectedMessages.filterNot(allMessages.contains)
            if (missingMessages.nonEmpty) {
              fail(s"Not all expected errors were found. Missing: ${missingMessages.mkString(", ")}. Got: $allMessages")
            }
        }
    }
  }

  /**
   * Strict version of assertInvalidInBothModes that requires the code to compile without annotation processor. Use this
   * for invalid test cases where the Java code is syntactically correct but fails validation.
   *
   * @param relativePath
   *   path to the test source file relative to src/test/resources/test-sources
   * @param expectedMessages
   *   expected substrings in error messages
   */
  protected def assertInvalidInBothModesStrict(relativePath: String, expectedMessages: String*): Unit = {
    val className = classNameFromPath(relativePath)

    // Compile-time validation
    val compileResult = compileTestSource(relativePath)
    assertCompilationFailure(compileResult, expectedMessages: _*)

    // Runtime validation (must succeed in compiling)
    val runtimeResult = compileTestSourceForRuntime(relativePath)
    val clazz = loadCompiledClass(runtimeResult, className)
    assertRuntimeValidationFailure(clazz, expectedMessages: _*)
  }

}
