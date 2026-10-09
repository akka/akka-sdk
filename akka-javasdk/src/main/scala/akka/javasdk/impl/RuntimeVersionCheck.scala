/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Duration
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

import scala.util.control.NonFatal

import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource

/**
 * INTERNAL API
 *
 * Checks, once per local dev-mode startup, whether the Runtime version packaged by this SDK release is older than the
 * latest Runtime published, and logs a warning if so. This only runs in local dev mode (never in a deployed service)
 * and never fails the run: any problem (offline, no local Maven config found, network error, timeout) is swallowed and
 * logged at debug.
 *
 * The Runtime is released independently of the SDK, so an SDK release can pin an older Runtime version than what's
 * actually available. There's no reliable way to know a customer's Maven setup in advance (direct repo.akka.io, or an
 * internal Artifactory/Nexus mirror), so this prefers checking against whichever repository Maven actually resolved the
 * pinned Runtime artifact from (respecting a mirror's own release cadence), and falls back to repo.akka.io directly if
 * that repository can't be reached or doesn't have metadata for the artifact.
 */
private[impl] object RuntimeVersionCheck {

  private val logger = LoggerFactory.getLogger("akka.javasdk.RuntimeVersionCheck")

  private val EnabledProperty = "akka.javasdk.dev-mode.runtime-version-check.enabled"
  private val TimeoutMillis = 2000
  private val GroupPath = "io/akka"
  private val ArtifactId = "akka-runtime-core_2.13"
  private val FallbackHost = "repo.akka.io"

  private[impl] final case class RepoLocation(baseUrl: String, username: Option[String], password: Option[String])

  /**
   * Fire-and-forget: spawns a daemon thread so it never delays local server startup. Safe to call unconditionally from
   * dev-mode startup; all the gating logic lives inside, and nothing this method does - or anything it spawns - is ever
   * allowed to propagate back to the caller. This is a nice-to-have safety check, never a hard requirement, so any
   * failure anywhere in it must be swallowed rather than risk breaking the actual dev-mode startup it's attached to.
   */
  def checkAsync(pinnedRuntimeVersion: String): Unit =
    try {
      if (!java.lang.Boolean.parseBoolean(System.getProperty(EnabledProperty, "true"))) {
        logger.debug("Runtime version check disabled via -D{}=false", EnabledProperty)
      } else {
        val thread = new Thread(() => runCheck(pinnedRuntimeVersion), "akka-runtime-version-check")
        thread.setDaemon(true)
        thread.start()
      }
    } catch {
      case NonFatal(e) =>
        logger.debug("Failed to start Runtime version check, ignoring", e)
    }

  private def runCheck(pinnedRuntimeVersion: String): Unit =
    try {
      val settingsFile = new File(System.getProperty("user.home"), ".m2/settings.xml")
      if (!settingsFile.exists()) {
        logger.debug("No {} found, skipping Runtime version check", settingsFile)
      } else {
        val settingsDoc = parseXml(Files.readAllBytes(settingsFile.toPath))
        val localRepo = resolveLocalRepository(settingsDoc)
        val repositoryIndex = buildRepositoryIndex(settingsDoc)
        val candidates = resolveCandidateLocations(repositoryIndex, localRepo, pinnedRuntimeVersion)

        firstSuccessfulLookup(candidates) match {
          case Some((source, latest)) =>
            if (isOlder(pinnedRuntimeVersion, latest))
              warnOutdated(pinnedRuntimeVersion, latest, source)
            else
              logger.debug(
                "Runtime version {} is up to date (checked against '{}', latest published: {})",
                pinnedRuntimeVersion,
                source,
                latest)
          case None =>
            logger.debug(
              "Could not determine the latest published Runtime version from any configured repository, " +
              "skipping check")
        }
      }
    } catch {
      case NonFatal(e) =>
        logger.debug("Runtime version check failed, ignoring", e)
    }

  private def warnOutdated(pinned: String, latest: String, source: String): Unit =
    logger.warn(
      "A newer Akka Runtime version is available: this project is running against Runtime {} (resolved via " +
      "'{}'), but Runtime {} has been published. This SDK release has not been updated to pull in the latest " +
      "Runtime yet. To use it locally now, override the Runtime version explicitly, e.g. " +
      "`mvn -Dakka-runtime.version={} compile exec:java`. (Disable this check with -D{}=false)",
      pinned,
      source,
      latest,
      latest,
      EnabledProperty)

  /**
   * Candidates in preference order: every repository the pinned Runtime artifact was actually resolved from locally (as
   * recorded by Maven's resolver in `_remote.repositories`, so this naturally reflects an internal Artifactory/Nexus
   * mirror when one is configured), followed by a direct repo.akka.io entry (if any is configured) as a fallback for
   * when the resolved repository doesn't have newer versions cached yet, or can't be reached for this check.
   */
  private[impl] def resolveCandidateLocations(
      repositoryIndex: Map[String, RepoLocation],
      localRepo: File,
      pinnedVersion: String): Seq[(String, RepoLocation)] = {
    val resolvedIds = readRemoteRepositoryIds(localRepo, pinnedVersion)
    val fromResolved = resolvedIds.flatMap(id => repositoryIndex.get(id).map(id -> _))
    val fallback = repositoryIndex.collectFirst { case (id, loc) if loc.baseUrl.contains(FallbackHost) => id -> loc }
    (fromResolved ++ fallback).distinct
  }

  private def firstSuccessfulLookup(candidates: Seq[(String, RepoLocation)]): Option[(String, String)] =
    candidates.iterator.flatMap { case (id, loc) => fetchLatestVersion(loc).map(id -> _) }.take(1).toSeq.headOption

  private def readRemoteRepositoryIds(localRepo: File, version: String): Seq[String] = {
    val file = new File(localRepo, s"$GroupPath/$ArtifactId/$version/_remote.repositories")
    if (!file.exists()) Seq.empty
    else parseRemoteRepositoryIds(new String(Files.readAllBytes(file.toPath), StandardCharsets.UTF_8))
  }

  // Maven Resolver's `_remote.repositories` marker file records, per downloaded file, which
  // configured repository id it came from, e.g.:
  //   akka-runtime-core_2.13-1.6.17.jar>akka-repository=
  // A file can have more than one recorded id (e.g. both a pluginRepository and a repository
  // entry pointing at the same artifact); all are tried, in file order, preferring the ids
  // recorded against the .jar over the .pom.
  private[impl] def parseRemoteRepositoryIds(content: String): Seq[String] = {
    val LineRegex = """^(\S+)>([^=]+)=?\s*$""".r
    val entries = content.linesIterator
      .filterNot(_.startsWith("#"))
      .collect { case LineRegex(file, id) => file -> id }
      .toSeq
    val jarIds = entries.collect { case (file, id) if file.endsWith(".jar") => id }
    (if (jarIds.nonEmpty) jarIds else entries.map(_._2)).distinct
  }

  private[impl] def resolveLocalRepository(settingsDoc: Document): File = {
    val userHome = System.getProperty("user.home")
    val default = new File(userHome, ".m2/repository")
    Option(settingsDoc.getElementsByTagName("localRepository").item(0))
      .map(_.getTextContent.trim)
      .filter(_.nonEmpty)
      .map(_.replace("${user.home}", userHome))
      .map(new File(_))
      .getOrElse(default)
  }

  // Builds an index of every repository/mirror/pluginRepository id -> location declared in
  // settings.xml (including ids nested in <profiles>, regardless of activation - a false match on
  // an inactive profile's id is harmless here, we just try it and move on). Credentials are only
  // picked up from plain-text <server> passwords; a Maven master-password-encrypted password
  // (starting with "{") is left out, so such a repository is simply tried unauthenticated and
  // will fail closed (no version found) rather than send a garbled credential.
  private[impl] def buildRepositoryIndex(settingsDoc: Document): Map[String, RepoLocation] = {
    def elements(tag: String): Seq[Element] = {
      val nodes = settingsDoc.getElementsByTagName(tag)
      (0 until nodes.getLength).flatMap(i => Option(nodes.item(i)).collect { case el: Element => el })
    }
    def childText(el: Element, tag: String): Option[String] =
      Option(el.getElementsByTagName(tag).item(0)).map(_.getTextContent.trim).filter(_.nonEmpty)

    val urlsById =
      (elements("mirror") ++ elements("repository") ++ elements("pluginRepository"))
        .flatMap(el => childText(el, "id").flatMap(id => childText(el, "url").map(id -> _)))

    val credentialsById =
      elements("server")
        .flatMap(el => childText(el, "id").map(id => id -> el))
        .map { case (id, el) =>
          id -> (childText(el, "username"), childText(el, "password").filterNot(_.startsWith("{")))
        }
        .toMap

    urlsById
      .groupBy(_._1)
      .view
      .mapValues(_.head._2)
      .map { case (id, url) =>
        val (user, pass) = credentialsById.getOrElse(id, (None, None))
        id -> RepoLocation(url.stripSuffix("/"), user, pass)
      }
      .toMap
  }

  private def fetchLatestVersion(location: RepoLocation): Option[String] =
    try {
      val metadataUrl = s"${location.baseUrl}/$GroupPath/$ArtifactId/maven-metadata.xml"
      val client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(TimeoutMillis)).build()
      val requestBuilder = HttpRequest
        .newBuilder(URI.create(metadataUrl))
        .timeout(Duration.ofMillis(TimeoutMillis))
        .GET()
      for {
        user <- location.username
        pass <- location.password
      } {
        val encoded = Base64.getEncoder.encodeToString(s"$user:$pass".getBytes(StandardCharsets.UTF_8))
        requestBuilder.header("Authorization", s"Basic $encoded")
      }
      val response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
      if (response.statusCode() != 200) None
      else {
        val doc = parseXml(response.body().getBytes(StandardCharsets.UTF_8))
        firstChildText(doc, "release").orElse(firstChildText(doc, "latest"))
      }
    } catch {
      case NonFatal(e) =>
        logger.debug("Failed to fetch Runtime metadata from {}, skipping", location.baseUrl, e)
        None
    }

  private def firstChildText(doc: Document, tag: String): Option[String] =
    Option(doc.getElementsByTagName(tag).item(0)).map(_.getTextContent.trim)

  private def parseXml(bytes: Array[Byte]): Document =
    DocumentBuilderFactory
      .newInstance()
      .newDocumentBuilder()
      .parse(new InputSource(new java.io.ByteArrayInputStream(bytes)))

  // Simple major.minor.patch comparison; good enough for the Runtime's "x.y.z" versioning scheme.
  // A real implementation should reuse the same comparator as
  // akka.javasdk.enforcer.VersionComparator instead of duplicating this.
  private[impl] def isOlder(current: String, candidate: String): Boolean = {
    def parts(v: String): Seq[Int] =
      v.split("\\.").toSeq.map(_.takeWhile(_.isDigit)).map(s => if (s.isEmpty) 0 else s.toInt)

    val c = parts(current)
    val n = parts(candidate)
    val length = math.max(c.length, n.length)
    val cPadded = c.padTo(length, 0)
    val nPadded = n.padTo(length, 0)
    cPadded.zip(nPadded).find { case (a, b) => a != b }.exists { case (a, b) => a < b }
  }
}
