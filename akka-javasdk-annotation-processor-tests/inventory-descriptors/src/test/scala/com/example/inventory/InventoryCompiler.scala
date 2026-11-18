/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

import scala.jdk.CollectionConverters._
import scala.util.Using

/** Compiles a folder of test sources in-process with the Akka annotation processors. */
object InventoryCompiler {

  /** Outcome of a compile, with the folder that holds the compiled classes and resources. */
  final case class InventoryCompileResult(
      success: Boolean,
      diagnostics: List[Diagnostic[_ <: JavaFileObject]],
      outputDir: Path) {

    /** Returns the bytes of the inventory for the given coordinates, if the compile wrote one. */
    def inventory(groupId: String, artifactId: String): Option[Array[Byte]] = {
      val file = outputDir.resolve(s"META-INF/akka-javasdk-inventory_${groupId}_$artifactId.json")
      if (Files.exists(file)) Some(Files.readAllBytes(file)) else None
    }
  }

  val AllProcessors: String =
    "akka.javasdk.tooling.processor.ComponentValidationProcessor," +
    "akka.javasdk.tooling.processor.ComponentAnnotationProcessor," +
    "akka.javasdk.tooling.processor.InventoryProcessor"

  val CoordinateOptions: Seq[String] = Seq("-Aakka.javasdk.groupId=com.example", "-Aakka.javasdk.artifactId=shop")

  val DefaultOptions: Seq[String] = CoordinateOptions :+ "-Aakka.javasdk.inventory=true"

  /**
   * Compiles every Java file under `src/test/resources/test-sources/<sourceDir>`. When `applicationConf` is set, copies
   * `src/test/resources/test-config/<applicationConf>` into the output folder as `application.conf` first.
   */
  def compile(
      sourceDir: String,
      options: Seq[String] = DefaultOptions,
      processors: String = AllProcessors,
      outputDir: Option[Path] = None,
      applicationConf: Option[String] = None): InventoryCompileResult = {
    val compiler = ToolProvider.getSystemJavaCompiler
    require(compiler != null, "Java compiler not available. Run the tests on a JDK.")

    val sourceRoot = Paths.get("src/test/resources/test-sources").resolve(sourceDir)
    require(Files.isDirectory(sourceRoot), s"Test source folder not found: ${sourceRoot.toAbsolutePath}")

    val sourceFiles = Using.resource(Files.walk(sourceRoot)) { paths =>
      paths.iterator().asScala.filter(_.toString.endsWith(".java")).map(_.toFile).toList.sortBy(_.getPath)
    }

    val output = outputDir.getOrElse(Files.createTempDirectory("inventory-test"))

    applicationConf.foreach { name =>
      val configFile = Paths.get("src/test/resources/test-config").resolve(name)
      require(Files.isRegularFile(configFile), s"Test config not found: ${configFile.toAbsolutePath}")
      Files.copy(configFile, output.resolve("application.conf"), StandardCopyOption.REPLACE_EXISTING)
    }

    val compilerOptions =
      Seq(
        "-encoding",
        "UTF-8",
        "-parameters",
        "-processor",
        processors,
        "-classpath",
        System.getProperty("java.class.path"),
        "-d",
        output.toString) ++ options

    val diagnosticCollector = new DiagnosticCollector[JavaFileObject]()
    Using.resource(compiler.getStandardFileManager(diagnosticCollector, null, null)) { fileManager =>
      val compilationUnits = fileManager.getJavaFileObjectsFromFiles(sourceFiles.asJava)
      val task =
        compiler.getTask(null, fileManager, diagnosticCollector, compilerOptions.asJava, null, compilationUnits)
      val success = task.call()
      InventoryCompileResult(success, diagnosticCollector.getDiagnostics.asScala.toList, output)
    }
  }
}
