/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.grpc

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

import scala.annotation.nowarn
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.util.control.NonFatal

import akka.Done
import akka.actor.ClassicActorSystemProvider
import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.grpc.GrpcClientSettings
import akka.grpc.javadsl.AkkaGrpcClient
import akka.javasdk.grpc.GrpcClientProvider
import akka.javasdk.impl.Settings
import akka.javasdk.impl.grpc.GrpcClientProviderImpl.AuthHeaders
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.{ Context => OtelContext }
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object GrpcClientProviderImpl {
  final case class AuthHeaders(headerName: String, headerValue: String)
  private final case class ClientKey(clientClass: Class[_], serviceName: String)

  private def isAkkaService(serviceName: String): Boolean = !(serviceName.contains('.') || serviceName.contains(':'))

  @nowarn("msg=deprecated")
  private def settingsWithCallCredentials(key: String, value: String)(
      settings: GrpcClientSettings): GrpcClientSettings = {

    import io.grpc.{ CallCredentials, Metadata }
    val headers = new Metadata()
    headers.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value)
    settings.withCallCredentials(new CallCredentials {
      override def applyRequestMetadata(
          requestInfo: CallCredentials.RequestInfo,
          appExecutor: Executor,
          applier: CallCredentials.MetadataApplier): Unit = {
        applier.apply(headers)
      }
      override def thisUsesUnstableApi(): Unit = ()
    })

  }

  /**
   * Picks up the service specific config from the client config block, sanitizes to allowed config and makes sure the
   * return will always be at least an empty block entry with the service name (needed for Akka gRPC).
   *
   * @param clientConfig
   *   the config under `akka.javasdk.grpc.client`
   */
  private[grpc] def serviceConfigFor(serviceName: String, clientConfig: Config): Config = {
    val quotedServiceName = s""""$serviceName""""
    // defaults but there must be an entry or akka grpc config parsing fails
    def emptyServiceConfig = ConfigFactory.parseString(s"""$quotedServiceName = {}""")

    // external service, details defined in user config,
    if (clientConfig.hasPath(quotedServiceName)) {
      // we do not allow any Akka gRPC setting, but a limited subset
      val sanitized = onlyAllowedAkkaGrpcSettings(clientConfig.getConfig(quotedServiceName))
      if (sanitized.isEmpty) emptyServiceConfig
      else sanitized.atPath(quotedServiceName)
    } else {
      emptyServiceConfig
    }
  }

  private val allowedAkkaGrpClientSettings = Set("host", "port", "use-tls")

  private def onlyAllowedAkkaGrpcSettings(config: Config): Config = {
    var safeConfig = ConfigFactory.empty()
    allowedAkkaGrpClientSettings.foreach(key =>
      if (config.hasPath(key))
        safeConfig = safeConfig.withValue(key, config.getValue(key)))
    safeConfig
  }

}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class GrpcClientProviderImpl(
    system: ActorSystem[_],
    settings: Settings,
    userServiceConfig: Config,
    remoteIdentificationHeader: Option[AuthHeaders])
    extends GrpcClientProvider {
  import GrpcClientProviderImpl._
  import system.executionContext

  private val log = LoggerFactory.getLogger(classOf[GrpcClientProvider])

  private val clients = new ConcurrentHashMap[ClientKey, AkkaGrpcClient]()

  private val clientConfig = userServiceConfig.getConfig("akka.javasdk.grpc.client")

  CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseServiceStop, "stop-grpc-clients")(() =>
    Future
      .traverse(clients.values().asScala)(_.close().asScala)
      .map(_ => Done))

  override def grpcClientFor[T <: AkkaGrpcClient](serviceClass: Class[T], serviceName: String): T = {
    val clientKey = ClientKey(serviceClass, serviceName)
    clients
      .computeIfAbsent(
        clientKey,
        { _ =>
          val client = createNewClientFor(serviceClass, serviceName)
          client.closed().asScala.foreach { _ =>
            // user should not close client, but just to be sure we don't keep it around if they do
            clients.remove(clientKey, client)
          }
          client
        })
      .asInstanceOf[T]
  }

  private[akka] def createNewClientFor[T <: AkkaGrpcClient](
      clientClass: Class[T],
      serviceName: String,
      addAuthHeaders: Boolean = true): T = {
    val clientSettings = {
      if (isAkkaService(serviceName)) {
        val akkaServiceClientSettings = if (settings.devModeSettings.isDefined) {
          // special cases in dev mode:
          // Allow config to override services to talk to services running wherever (auth headers won't work though)
          if (clientConfig.hasPath(serviceName)) {
            log.info("Using explicit dev mode config gRPC client override for service [{}]", serviceName)
            clientSettingsFromConfig(serviceName)
          } else {
            // Normally: local service discovery when running locally and trying to use gRPC
            localDevModeDiscovery(serviceName)
          }
        } else {
          // in production, we rely on DNS and service mesh transports, no overrides allowed
          if (clientConfig.hasPath(serviceName)) {
            log.warn(
              s"Configuration override for [${serviceName}] found in 'application.conf'. This is not supported and is ignored.")
          }

          log.debug("Creating gRPC client for Akka service [{}]", serviceName)
          GrpcClientSettings
            .connectToServiceAt(serviceName, 80)(system)
            // (TLS is handled for us by Kalix infra)
            .withTls(false)
        }

        // auth headers for Akka ACLs
        remoteIdentificationHeader match {
          case _ if !addAuthHeaders => akkaServiceClientSettings // used by testkit to specify its own calling principal
          case Some(auth) => settingsWithCallCredentials(auth.headerName, auth.headerValue)(akkaServiceClientSettings)
          case None       => akkaServiceClientSettings
        }
      } else {
        // external/public gRPC service
        log.debug("Creating gRPC client for external service [{}]", serviceName)
        if (clientConfig.hasPath(s""""${serviceName}"""")) {
          // user provided config for fqdn of service
          clientSettingsFromConfig(serviceName)
        } else {
          // or no config, we expect it is HTTPS on default port
          log.debug("Creating gRPC client for external service [{}] port [443]", serviceName)
          GrpcClientSettings.connectToServiceAt(serviceName, 443)(system)
        }
      }
    }

    // Java API - static create
    val create =
      clientClass.getMethod("create", classOf[GrpcClientSettings], classOf[ClassicActorSystemProvider])
    val client = create.invoke(null, clientSettings, system).asInstanceOf[AkkaGrpcClient]
    client.asInstanceOf[T]
  }

  private def clientSettingsFromConfig(serviceName: String): GrpcClientSettings = {
    val serviceConfig = serviceConfigFor(serviceName, clientConfig)
    GrpcClientSettings.fromConfig(serviceName, serviceConfig)(system)
  }

  private def localDevModeDiscovery(serviceName: String): GrpcClientSettings = {
    try {
      // The runtime has set up an Akka discovery mechanism that finds locally running
      // services. Since in dev mode only blocking is fine for now.
      log.debug("Creating dev mode gRPC client for Akka service [{}] using local discovery", serviceName)
      GrpcClientSettings
        .usingServiceDiscovery(serviceName)(system)
        // (No TLS locally)
        .withTls(false)

    } catch {
      case NonFatal(ex) =>
        throw new RuntimeException(
          s"Failed to look up service [${serviceName}] in dev-mode, make sure that it is also running " +
          "with a separate port and service name correctly defined in its application.conf under 'akka.javasdk.dev-mode.service-name' " +
          "if it differs from the maven project name",
          ex)
    }
  }

  // FIXME(tracing): have context propagators provided by the runtime
  def withTelemetryContext(telemetryContext: OtelContext): GrpcClientProvider = {
    val otelTraceHeaders: Vector[(String, String)] = {
      val builder = Vector.newBuilder[(String, String)]
      W3CTraceContextPropagator
        .getInstance()
        .inject(
          telemetryContext,
          null,
          // Note: side-effecting instead of mutable collection
          (_: scala.Any, key: String, value: String) => {
            builder += ((key, value))
          })
      builder.result()
    }
    if (otelTraceHeaders.isEmpty) this
    else
      new GrpcClientProvider {
        override def grpcClientFor[T <: AkkaGrpcClient](serviceClass: Class[T], serviceName: String): T = {
          otelTraceHeaders.foldLeft(GrpcClientProviderImpl.this.grpcClientFor(serviceClass, serviceName)) {
            case (client, (key, value)) =>
              client.addRequestHeader(key, value).asInstanceOf[T]
          }
        }
      }
  }
}
