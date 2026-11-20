/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._
import scala.util.control.NonFatal

import akka.annotation.InternalApi
import com.typesafe.config.Config

/**
 * INTERNAL API
 *
 * How a remote A2A agent is reached, read from `akka.javasdk.a2a.remote-agents.<name>`.
 */
@InternalApi
private[akka] sealed trait A2aAuth

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object A2aAuth {
  case object NoAuth extends A2aAuth

  // toString of the credential types leaves the secret out, so settings can be logged
  final case class Header(name: String, value: String) extends A2aAuth {
    override def toString: String = s"Header($name,***)"
  }

  final case class OAuth2ClientCredentials(
      tokenUrl: String,
      clientId: String,
      clientSecret: String,
      scope: Option[String],
      authMethod: TokenEndpointAuthMethod = ClientSecretBasic)
      extends A2aAuth {
    override def toString: String = s"OAuth2ClientCredentials($tokenUrl,$clientId,***,$scope,$authMethod)"
  }

  /**
   * How the client authenticates to the token endpoint, named as in RFC 7591.
   */
  sealed trait TokenEndpointAuthMethod

  /** The client id and secret in an HTTP Basic `Authorization` header, which RFC 6749 requires servers to support. */
  case object ClientSecretBasic extends TokenEndpointAuthMethod

  /** The client id and secret in the form body. */
  case object ClientSecretPost extends TokenEndpointAuthMethod
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final case class A2aRemoteAgentSettings(
    name: String,
    cardUrl: String,
    timeout: FiniteDuration,
    pollInterval: FiniteDuration,
    cardRefresh: FiniteDuration,
    auth: A2aAuth,
    caBundle: Option[String],
    clientCertificate: Option[String],
    clientKey: Option[String]) {
  override def toString: String =
    s"A2aRemoteAgentSettings($name,$cardUrl,$timeout,$pollInterval,$cardRefresh,$auth," +
    s"caBundle=${caBundle.isDefined},clientCertificate=${clientCertificate.isDefined},clientKey=***)"
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object A2aRemoteAgentSettings {

  /**
   * @param a2a
   *   the `akka.javasdk.a2a` config
   * @param nameOrCardUrl
   *   a name under `remote-agents`, or the URL of an Agent Card
   */
  def resolve(a2a: Config, nameOrCardUrl: String): A2aRemoteAgentSettings = {
    val defaults = a2a.getConfig("defaults")
    if (nameOrCardUrl.startsWith("http://") || nameOrCardUrl.startsWith("https://"))
      fromConfig(
        nameOrCardUrl,
        defaults.withValue("card-url", com.typesafe.config.ConfigValueFactory.fromAnyRef(nameOrCardUrl)))
    else {
      val remoteAgents = a2a.getConfig("remote-agents")
      if (!remoteAgents.hasPath(quoted(nameOrCardUrl)))
        throw new IllegalArgumentException(
          s"No remote A2A agent [$nameOrCardUrl] is configured under akka.javasdk.a2a.remote-agents " +
          s"(configured: ${remoteAgents.root().keySet().asScala.toSeq.sorted.mkString("[", ", ", "]")}). " +
          "Pass the URL of an Agent Card to call an agent that is not configured.")
      fromConfig(nameOrCardUrl, remoteAgents.getConfig(quoted(nameOrCardUrl)).withFallback(defaults))
    }
  }

  private def quoted(name: String): String = com.typesafe.config.ConfigUtil.quoteString(name)

  def fromConfig(name: String, config: Config): A2aRemoteAgentSettings = {
    def where(key: String) = s"akka.javasdk.a2a.remote-agents.$name.$key"
    def optional(key: String): Option[String] =
      if (config.hasPath(key)) Some(config.getString(key).trim).filter(_.nonEmpty) else None
    def required(key: String): String =
      optional(key).getOrElse(throw new IllegalArgumentException(s"${where(key)} must be set"))
    def httpUrl(key: String, value: String): String = {
      val ok =
        try {
          val uri = new URI(value)
          (uri.getScheme == "http" || uri.getScheme == "https") && uri.getHost != null
        } catch { case NonFatal(_) => false }
      if (!ok) throw new IllegalArgumentException(s"${where(key)} must be an http:// or https:// URL")
      value
    }
    def duration(key: String): FiniteDuration = config.getDuration(key).toScala
    def credentialUrl(key: String, value: String): String = {
      if (!safeForCredentials(value))
        throw new IllegalArgumentException(
          s"${where(key)} must be an https:// URL, because credentials are sent to it. " +
          "An http:// URL is accepted only for localhost.")
      value
    }

    val auth = config.getString("auth.type") match {
      case "none" => A2aAuth.NoAuth
      case "header" =>
        A2aAuth.Header(required("auth.header-name"), required("auth.header-value"))
      case "oauth2-client-credentials" =>
        A2aAuth.OAuth2ClientCredentials(
          credentialUrl("auth.token-url", httpUrl("auth.token-url", required("auth.token-url"))),
          required("auth.client-id"),
          required("auth.client-secret"),
          optional("auth.scope"),
          optional("auth.token-endpoint-auth-method") match {
            case None | Some("client_secret_basic") => A2aAuth.ClientSecretBasic
            case Some("client_secret_post")         => A2aAuth.ClientSecretPost
            case Some(other) =>
              throw new IllegalArgumentException(
                s"${where("auth.token-endpoint-auth-method")} must be client_secret_basic or client_secret_post, got [$other]")
          })
      case other =>
        throw new IllegalArgumentException(
          s"${where("auth.type")} must be one of none, header, oauth2-client-credentials, got [$other]")
    }

    val clientCertificate = optional("client-certificate")
    val clientKey = optional("client-key")
    if (clientCertificate.isDefined != clientKey.isDefined)
      throw new IllegalArgumentException(
        s"${where("client-certificate")} and ${where("client-key")} must be set together")

    val cardUrl = httpUrl("card-url", required("card-url"))
    if (auth != A2aAuth.NoAuth) credentialUrl("card-url", cardUrl)

    A2aRemoteAgentSettings(
      name,
      cardUrl,
      duration("timeout"),
      duration("poll-interval"),
      duration("card-refresh"),
      auth,
      optional("ca-bundle"),
      clientCertificate,
      clientKey)
  }

  private val LoopbackIpv4 = """127\.\d{1,3}\.\d{1,3}\.\d{1,3}""".r

  /**
   * Credentials travel over https only, or over http to the local host, where tests and local development run.
   */
  def safeForCredentials(url: String): Boolean =
    try {
      val uri = new URI(url)
      val host = Option(uri.getHost).getOrElse("")
      uri.getScheme == "https" ||
      (uri.getScheme == "http" &&
      (host.equalsIgnoreCase("localhost") || host == "[::1]" || LoopbackIpv4.matches(host)))
    } catch { case NonFatal(_) => false }

  /**
   * PEM text is used as it is. Anything else is the path of a file that holds the PEM text.
   *
   * @param setting
   *   the setting that holds the value, named in the error
   */
  def pemText(value: String, setting: String): String =
    if (value.contains("-----BEGIN")) value
    else
      try new String(Files.readAllBytes(Path.of(value)), StandardCharsets.UTF_8)
      catch {
        // Neither the value nor the cause is in the error: a key without its PEM header would be printed
        case NonFatal(_) =>
          throw new IllegalArgumentException(
            s"$setting must be PEM text or the path of a readable PEM file, and no file could be read at that path")
      }
}
