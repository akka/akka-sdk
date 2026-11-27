/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.net.URI
import java.util.Locale

import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._
import scala.util.control.NonFatal

import akka.annotation.InternalApi
import akka.runtime.sdk.spi.SpiA2aAuth
import akka.runtime.sdk.spi.SpiA2aClientSettings
import com.typesafe.config.Config
import com.typesafe.config.ConfigUtil
import com.typesafe.config.ConfigValueType

/**
 * INTERNAL API
 *
 * Reads the remote A2A agents from `akka.javasdk.agent.a2a-client.agents` and checks them, so that a mistake fails the
 * start of the service and names the key to change. An agent without `card-url` counts as not configured, as the value
 * can come from an optional environment variable.
 */
@InternalApi
private[impl] object RemoteA2aSettings {

  private val AgentsPath = "akka.javasdk.agent.a2a-client.agents"

  def fromConfig(applicationConf: Config): Map[String, SpiA2aClientSettings] =
    if (!applicationConf.hasPath(AgentsPath)) Map.empty
    else {
      val agents = applicationConf.getConfig(AgentsPath)
      agents
        .root()
        .keySet()
        .asScala
        .toSeq
        .flatMap { name =>
          if (agents.root().get(name).valueType() != ConfigValueType.OBJECT)
            throw invalid(name, s"[${keyPath(name, "")}] must be a configuration section")
          val c = agents.getConfig(ConfigUtil.quoteString(name))
          if (c.hasPath("card-url")) Some(name -> agentSettings(name, c)) else None
        }
        .toMap
    }

  private def keyPath(name: String, key: String): String =
    ConfigUtil.joinPath("akka", "javasdk", "agent", "a2a-client", "agents", name) + (if (key.isEmpty) "" else s".$key")

  private def invalid(name: String, problem: String): IllegalArgumentException =
    new IllegalArgumentException(s"The remote A2A agent [$name] is not valid: $problem")

  private def agentSettings(name: String, c: Config): SpiA2aClientSettings = {
    val cardUrl = c.getString("card-url")
    // The messages never hold the URL, which can hold a key in its query.
    val uri =
      try Some(new URI(cardUrl))
      catch { case NonFatal(_) => None }
    if (!uri.exists(u =>
        u.getHost != null && Option(u.getScheme).exists(s =>
          s.equalsIgnoreCase("http") || s.equalsIgnoreCase("https"))))
      throw invalid(name, s"[${keyPath(name, "card-url")}] must be an http or https URL")

    new SpiA2aClientSettings(cardUrl, auth(name, c), allowedHosts(name, c), requestTimeout(name, c))
  }

  private def auth(name: String, c: Config): SpiA2aAuth =
    if (!c.hasPath("auth")) SpiA2aAuth.NoAuth
    else {
      val a = c.getConfig("auth")
      def required(key: String): String = {
        val value = if (a.hasPath(key)) a.getString(key) else ""
        if (value.isEmpty)
          throw invalid(name, s"[${keyPath(name, s"auth.$key")}] is not set or is empty")
        value
      }
      required("type").toLowerCase(Locale.ROOT) match {
        case "none"   => SpiA2aAuth.NoAuth
        case "bearer" => new SpiA2aAuth.Bearer(required("token"))
        case "header" => new SpiA2aAuth.Header(required("header-name"), required("header-value"))
        case other =>
          throw invalid(name, s"[${keyPath(name, "auth.type")}] is [$other], expected none, bearer or header")
      }
    }

  private def allowedHosts(name: String, c: Config): Set[String] =
    if (!c.hasPath("allowed-hosts")) Set.empty
    else
      c.getStringList("allowed-hosts").asScala.toSeq.map(_.trim).toSet.map(checkedHost(name, _))

  private def checkedHost(name: String, entry: String): String = {
    if (entry.isEmpty || entry.exists(ch => ch == '/' || ch.isWhitespace))
      throw invalid(
        name,
        s"an entry of [${keyPath(name, "allowed-hosts")}] must be a host or host:port, without a scheme or a path")
    entry
  }

  private def requestTimeout(name: String, c: Config) =
    if (!c.hasPath("request-timeout")) None
    else {
      val timeout = c.getDuration("request-timeout")
      if (timeout.isZero || timeout.isNegative)
        throw invalid(name, s"[${keyPath(name, "request-timeout")}] must be greater than zero")
      Some(timeout.toScala)
    }
}
