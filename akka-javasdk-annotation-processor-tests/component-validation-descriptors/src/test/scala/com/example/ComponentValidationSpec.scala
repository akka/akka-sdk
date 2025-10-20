/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{ Files, Paths }
import javax.tools._
import scala.jdk.CollectionConverters._
import scala.util.Using

class ComponentValidationSpec extends AnyWordSpec with Matchers {

  "ComponentValidationProcessor" should {
    "accept valid public components" in {
      val result = compileTestSource("valid/ValidPublicComponent.java")
      result.success shouldBe true
      result.diagnostics.filter(_.getKind == Diagnostic.Kind.ERROR) shouldBe empty
    }

    "accept another valid public component" in {
      val result = compileTestSource("valid/AnotherValidComponent.java")
      result.success shouldBe true
      result.diagnostics.filter(_.getKind == Diagnostic.Kind.ERROR) shouldBe empty
    }

    "reject non-public component" in {
      val result = compileTestSource("invalid/NonPublicComponent.java")
      result.success shouldBe false

      val errors = result.diagnostics.filter(_.getKind == Diagnostic.Kind.ERROR)
      errors should not be empty

      val errorMessages = errors.map(_.getMessage(null)).mkString(" ")
      errorMessages should include("NonPublicComponent")
      errorMessages should include("not marked with `public` modifier")
      errorMessages should include("Components must be public")
    }

    "reject package-private component" in {
      val result = compileTestSource("invalid/PackagePrivateComponent.java")
      result.success shouldBe false

      val errors = result.diagnostics.filter(_.getKind == Diagnostic.Kind.ERROR)
      errors should not be empty

      val errorMessages = errors.map(_.getMessage(null)).mkString(" ")
      errorMessages should include("PackagePrivateComponent")
      errorMessages should include("not marked with `public` modifier")
    }
  }

  case class CompilationResult(success: Boolean, diagnostics: List[Diagnostic[_ <: JavaFileObject]])

  private def compileTestSource(relativePath: String): CompilationResult = {
    val compiler = ToolProvider.getSystemJavaCompiler
    require(compiler != null, "Java compiler not available. Make sure you're running on a JDK, not a JRE.")

    val diagnosticCollector = new DiagnosticCollector[JavaFileObject]()
    val fileManager = compiler.getStandardFileManager(diagnosticCollector, null, null)

    // Get the test source file
    val testSourcesDir = Paths.get("src/test/resources/test-sources")
    val sourceFile = testSourcesDir.resolve(relativePath).toFile
    require(sourceFile.exists(), s"Test source file not found: ${sourceFile.getAbsolutePath}")

    // Get compilation units
    val compilationUnits = fileManager.getJavaFileObjectsFromFiles(List(sourceFile).asJava)

    // Create output directory for compiled classes
    val outputDir = Files.createTempDirectory("component-validation-test")
    outputDir.toFile.deleteOnExit()

    // Set up compiler options
    val options = List(
      "-d",
      outputDir.toString,
      "-proc:only", // Only run annotation processing, don't generate class files
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
}
