/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.io.File
import java.net.JarURLConnection

import scala.jdk.CollectionConverters._
import scala.util.Using

import akka.annotation.InternalApi
import akka.runtime.sdk.spi.SpiPolicyDescriptor
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 *
 * Scans the classpath for OPA policy directories under `policies/` and creates [[SpiPolicyDescriptor]] instances for
 * each valid policy found.
 *
 * Expected directory layout on the classpath:
 * {{{
 * policies/
 * ├── my-policy/
 * │   ├── my-policy.conf    # HOCON manifest (name, version)
 * │   ├── data.json          # Policy data
 * │   └── policy.wasm        # Compiled policy
 * }}}
 */
@InternalApi
private[javasdk] object PolicyScanner {

  private val logger = LoggerFactory.getLogger(getClass)
  private val PoliciesPath = "policies"

  /**
   * Scans the classpath for policy directories and returns validated descriptors.
   *
   * @param classLoader
   *   the class loader to use for resource discovery
   * @return
   *   a sequence of validated policy descriptors, or `Seq.empty` if no policies directory exists
   */
  def scan(classLoader: ClassLoader): Seq[SpiPolicyDescriptor] = {
    val policyDirs = discoverPolicyDirectories(classLoader)
    if (policyDirs.isEmpty) {
      logger.debug("No policy directories found under [{}]", PoliciesPath)
      return Seq.empty
    }

    logger.info("Found [{}] policy director{}", policyDirs.size, if (policyDirs.size == 1) "y" else "ies")

    val descriptors = policyDirs.map { dirName =>
      val basePath = s"/$PoliciesPath/$dirName"
      val manifestPath = s"$PoliciesPath/$dirName/$dirName.conf"

      // Validate manifest exists
      val manifestStream = classLoader.getResourceAsStream(manifestPath)
      if (manifestStream eq null) {
        throw new IllegalArgumentException(
          s"Policy [$dirName]: manifest file not found at classpath resource [$manifestPath]. " +
          s"Expected a HOCON file named [$dirName.conf] in the policy directory.")
      }
      manifestStream.close()

      // Parse HOCON manifest
      val manifestUrl = classLoader.getResource(manifestPath)
      val config =
        try {
          ConfigFactory.parseURL(manifestUrl)
        } catch {
          case ex: Exception =>
            throw new IllegalArgumentException(
              s"Policy [$dirName]: failed to parse HOCON manifest at [$manifestPath]: ${ex.getMessage}",
              ex)
        }

      // Extract required fields
      val name =
        if (config.hasPath("name")) config.getString("name")
        else
          throw new IllegalArgumentException(
            s"Policy [$dirName]: manifest at [$manifestPath] is missing required field 'name'.")

      val version =
        if (config.hasPath("version")) config.getString("version")
        else
          throw new IllegalArgumentException(
            s"Policy [$dirName]: manifest at [$manifestPath] is missing required field 'version'.")

      // Validate name matches directory
      if (name != dirName) {
        throw new IllegalArgumentException(
          s"Policy [$dirName]: manifest 'name' field [$name] does not match directory name [$dirName]. " +
          "The policy name in the manifest must match the directory name.")
      }

      // Validate required files exist
      val dataPath = s"$PoliciesPath/$dirName/data.json"
      if (classLoader.getResourceAsStream(dataPath) eq null) {
        throw new IllegalArgumentException(
          s"Policy [$dirName]: required file [data.json] not found at classpath resource [$dataPath].")
      }

      val wasmPath = s"$PoliciesPath/$dirName/policy.wasm"
      if (classLoader.getResourceAsStream(wasmPath) eq null) {
        throw new IllegalArgumentException(
          s"Policy [$dirName]: required file [policy.wasm] not found at classpath resource [$wasmPath].")
      }

      logger.debug("Policy [{}] version [{}] at [{}]", name, version, basePath)
      PolicyDescriptorImpl(name = name, version = version, basePath = basePath)
    }

    // Check for duplicate names
    val seen = scala.collection.mutable.Map.empty[String, String]
    descriptors.foreach { d =>
      seen.get(d.name) match {
        case Some(existingPath) =>
          throw new IllegalArgumentException(
            s"Duplicate policy name [${d.name}] found at [${d.basePath}] and [$existingPath]. " +
            "Each policy must have a unique name.")
        case None =>
          seen(d.name) = d.basePath
      }
    }

    logger.info(
      "Discovered [{}] polic{}: [{}]",
      descriptors.size,
      if (descriptors.size == 1) "y" else "ies",
      descriptors.map(_.name).mkString(", "))
    descriptors
  }

  /**
   * Discovers policy subdirectory names under the `policies/` classpath resource. Supports both filesystem directories
   * and JAR entries.
   */
  private def discoverPolicyDirectories(classLoader: ClassLoader): Seq[String] = {
    val policyDirNames = scala.collection.mutable.LinkedHashSet.empty[String]

    val resources = classLoader.getResources(PoliciesPath).asScala.toSeq
    if (resources.isEmpty) {
      return Seq.empty
    }

    resources.foreach { policiesUrl =>
      logger.debug("Scanning policies at [{}]", policiesUrl)
      policiesUrl.getProtocol match {
        case "file" =>
          val policiesDir = new File(policiesUrl.toURI)
          if (policiesDir.isDirectory) {
            val subdirs = policiesDir.listFiles().filter(_.isDirectory).map(_.getName)
            subdirs.foreach { name =>
              logger.debug("Found policy directory: [{}]", name)
              policyDirNames += name
            }
          }

        case "jar" =>
          val jarConnection = policiesUrl.openConnection().asInstanceOf[JarURLConnection]
          Using(jarConnection.getJarFile) { jarFile =>
            val prefix = s"$PoliciesPath/"
            val entries = jarFile.entries().asScala
            entries
              .map(_.getName)
              .filter(name => name.startsWith(prefix) && name.length > prefix.length)
              .map(_.stripPrefix(prefix))
              .filter(_.contains("/"))
              .map(_.takeWhile(_ != '/'))
              .toSet
              .foreach { dirName: String =>
                logger.debug("Found policy directory in JAR: [{}]", dirName)
                policyDirNames += dirName
              }
          }.recover { case ex =>
            logger.warn("Failed to read JAR file for policy scanning: [{}]", ex.getMessage)
          }

        case protocol =>
          logger.debug("Skipping policies resource with unsupported protocol: [{}]", protocol)
      }
    }

    policyDirNames.toSeq
  }
}
