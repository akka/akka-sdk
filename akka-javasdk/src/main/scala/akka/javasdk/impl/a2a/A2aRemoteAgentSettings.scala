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
import com.typesafe.config.ConfigValueFactory
import com.typesafe.config.ConfigValueType

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
      authMethod: TokenEndpointAuthMethod = ClientSecretBasic,
      tokenCaBundle: Option[String] = None,
      tokenClientCertificate: Option[String] = None,
      tokenClientKey: Option[String] = None)
      extends A2aAuth {
    override def toString: String =
      s"OAuth2ClientCredentials($tokenUrl,$clientId,***,$scope,$authMethod," +
      s"tokenCaBundle=${tokenCaBundle.isDefined},tokenClientCertificate=${tokenClientCertificate.isDefined}," +
      "tokenClientKey=***)"
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
    clientKey: Option[String],
    cardVerification: Option[A2aCardVerification] = None) {
  override def toString: String =
    s"A2aRemoteAgentSettings($name,$cardUrl,$timeout,$pollInterval,$cardRefresh,$auth," +
    s"caBundle=${caBundle.isDefined},clientCertificate=${clientCertificate.isDefined},clientKey=***," +
    s"cardVerification=$cardVerification)"
}

/**
 * INTERNAL API
 *
 * How the client checks the signature of a remote Agent Card, when the agent requires a signed card. At most one of the
 * two is set. When neither is set, no key is trusted and every card is refused.
 *
 * @param publicKey
 *   the PEM text of the public key that signs the card
 * @param jwksUrl
 *   the URL of a JSON Web Key Set that holds the key, fetched with the agent's TLS settings
 */
@InternalApi
private[akka] final case class A2aCardVerification(publicKey: Option[String], jwksUrl: Option[String]) {
  override def toString: String = s"A2aCardVerification(publicKey=${publicKey.isDefined},$jwksUrl)"
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
  def resolve(a2a: Config, target: A2aRemoteTarget): A2aRemoteAgentSettings = target match {
    case A2aRemoteTarget.Named(name) =>
      val defaults = a2a.getConfig("defaults")
      val remoteAgents = a2a.getConfig("remote-agents")
      if (!remoteAgents.hasPath(quoted(name)))
        throw new IllegalArgumentException(
          s"No remote A2A agent [$name] is configured under akka.javasdk.a2a.remote-agents " +
          s"(configured: ${remoteAgents.root().keySet().asScala.toSeq.sorted.mkString("[", ", ", "]")}). " +
          "Use the URL of an Agent Card to call an agent that is not configured.")
      fromConfig(name, remoteAgents.getConfig(quoted(name)).withFallback(defaults))
    case A2aRemoteTarget.CardUrl(url) =>
      unconfigured(a2a, url, url)
    case A2aRemoteTarget.Service(serviceName) =>
      unconfigured(a2a, serviceName, s"http://$serviceName$CardPath")
  }

  /** The path of the Agent Card of an Akka service. */
  val CardPath = "/.well-known/agent-card.json"

  // An agent that is not configured gets no credentials and no custom TLS: its URL can come from anywhere, and the
  // defaults are shared by every such agent. It takes require-signed-card from the defaults, with the trusted key of
  // the defaults, and without one every card is refused.
  private def unconfigured(a2a: Config, name: String, cardUrl: String): A2aRemoteAgentSettings =
    fromConfig(
      name,
      Seq(
        "ca-bundle",
        "ca-bundle-file",
        "client-certificate",
        "client-certificate-file",
        "client-key",
        "client-key-file")
        .foldLeft(a2a.getConfig("defaults"))(_.withoutPath(_))
        .withValue("card-url", ConfigValueFactory.fromAnyRef(cardUrl))
        .withValue("auth.type", ConfigValueFactory.fromAnyRef("none")),
      refuseCardsWithoutKey = true)

  def isCardUrl(value: String): Boolean =
    value.startsWith("http://") || value.startsWith("https://")

  /**
   * The defaults apply to every agent, including one given by a URL, so they must not hold credentials.
   */
  def validateDefaults(a2a: Config): Unit = {
    val authType = a2a.getString("defaults.auth.type")
    if (authType != "none")
      throw new IllegalArgumentException(
        s"akka.javasdk.a2a.defaults.auth.type must be none, got [$authType]. Set the credentials of each agent " +
        "under akka.javasdk.a2a.remote-agents.<name>.auth")
  }

  /**
   * A warning for each configured agent whose TLS settings are not used, because its card URL is http.
   */
  def unusedTlsWarnings(a2a: Config): Seq[String] = {
    val defaults = a2a.getConfig("defaults")
    val remoteAgents = a2a.getConfig("remote-agents")
    remoteAgents.root().keySet().asScala.toSeq.sorted.flatMap { name =>
      val agent =
        if (remoteAgents.getValue(quoted(name)).valueType() != ConfigValueType.OBJECT) None
        else Some(remoteAgents.getConfig(quoted(name)).withFallback(defaults))
      def set(key: String) = agent.exists(c => c.hasPath(key) && c.getString(key).trim.nonEmpty)
      val plainHttp = agent.exists(c => c.hasPath("card-url") && c.getString("card-url").trim.startsWith("http://"))
      if (plainHttp && Seq("ca-bundle", "ca-bundle-file", "client-certificate", "client-certificate-file").exists(set))
        Some(
          s"Remote A2A agent [$name] has a ca-bundle or client-certificate, but its card-url is an http:// URL, " +
          "so the TLS settings are not used")
      else None
    }
  }

  private def quoted(name: String): String = com.typesafe.config.ConfigUtil.quoteString(name)

  /**
   * @param refuseCardsWithoutKey
   *   when a signed card is required and no key is set: true to refuse every card, false to fail as invalid settings
   */
  def fromConfig(name: String, config: Config, refuseCardsWithoutKey: Boolean = false): A2aRemoteAgentSettings = {
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
    // PEM text under the key, or the PEM text of the file named under the key with "-file" appended
    def pem(key: String): Option[String] =
      (optional(key), optional(s"$key-file")) match {
        case (Some(_), Some(_)) =>
          throw new IllegalArgumentException(s"Set only one of ${where(key)} and ${where(s"$key-file")}")
        case (Some(text), None) => Some(text)
        case (None, Some(path)) => Some(readPemFile(path, where(s"$key-file")))
        case (None, None)       => None
      }
    def certificateAndKey(certificateKey: String, keyKey: String): (Option[String], Option[String]) = {
      val certificate = pem(certificateKey)
      val key = pem(keyKey)
      if (certificate.isDefined != key.isDefined)
        throw new IllegalArgumentException(
          s"${where(certificateKey)} and ${where(keyKey)} (or their -file settings) must be set together")
      (certificate, key)
    }
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
        val (tokenClientCertificate, tokenClientKey) =
          certificateAndKey("auth.token-client-certificate", "auth.token-client-key")
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
          },
          pem("auth.token-ca-bundle"),
          tokenClientCertificate,
          tokenClientKey)
      case other =>
        throw new IllegalArgumentException(
          s"${where("auth.type")} must be one of none, header, oauth2-client-credentials, got [$other]")
    }

    val (clientCertificate, clientKey) = certificateAndKey("client-certificate", "client-key")

    val cardUrl = httpUrl("card-url", required("card-url"))
    if (auth != A2aAuth.NoAuth) credentialUrl("card-url", cardUrl)

    val cardVerification =
      if (config.hasPath("require-signed-card") && config.getBoolean("require-signed-card")) {
        val publicKey = pem("card-signing-key")
        val jwksUrl = optional("card-jwks-url").map(httpUrl("card-jwks-url", _))
        if (publicKey.isEmpty && jwksUrl.isEmpty && refuseCardsWithoutKey)
          Some(A2aCardVerification(None, None))
        else if (publicKey.isDefined == jwksUrl.isDefined)
          throw new IllegalArgumentException(
            s"${where("require-signed-card")} needs exactly one of ${where("card-signing-key")}, " +
            s"${where("card-signing-key-file")} and ${where("card-jwks-url")}")
        else Some(A2aCardVerification(publicKey, jwksUrl))
      } else None

    A2aRemoteAgentSettings(
      name,
      cardUrl,
      duration("timeout"),
      duration("poll-interval"),
      duration("card-refresh"),
      auth,
      pem("ca-bundle"),
      clientCertificate,
      clientKey,
      cardVerification)
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
   * The content of a PEM file.
   *
   * @param setting
   *   the setting that holds the path, named in the error
   */
  def readPemFile(path: String, setting: String): String =
    try new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8)
    catch {
      // The cause is not in the error, so that a key pasted into the setting by mistake is not printed
      case NonFatal(_) =>
        throw new IllegalArgumentException(s"$setting must be the path of a readable PEM file")
    }
}

/**
 * INTERNAL API
 *
 * A remote agent as the developer names it.
 */
@InternalApi
private[akka] sealed trait A2aRemoteTarget {

  /** The name of the agent in tool names, guardrail origins and messages. */
  def name: String
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object A2aRemoteTarget {

  /** An agent configured under `akka.javasdk.a2a.remote-agents.<name>`. */
  final case class Named(name: String) extends A2aRemoteTarget

  /** An agent given by the URL of its Agent Card. */
  final case class CardUrl(url: String) extends A2aRemoteTarget {
    def name: String = url
  }

  /** The A2A endpoint of another Akka service. */
  final case class Service(serviceName: String) extends A2aRemoteTarget {
    def name: String = serviceName
  }

  def named(name: String): Named = {
    if (name == null || name.isBlank) throw new IllegalArgumentException("A remote A2A agent name must not be blank")
    Named(name)
  }

  def cardUrl(url: String): CardUrl = {
    if (url == null || !A2aRemoteAgentSettings.isCardUrl(url))
      throw new IllegalArgumentException(
        s"The Agent Card URL of a remote A2A agent must start with http:// or https://, got [$url]")
    CardUrl(url)
  }

  def service(serviceName: String): Service = {
    if (serviceName == null || serviceName.isBlank || serviceName.exists(c => c == '.' || c == ':' || c == '/'))
      throw new IllegalArgumentException(
        s"The service of a remote A2A agent must be the bare name of an Akka service, got [$serviceName]")
    Service(serviceName)
  }
}
