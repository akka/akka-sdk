/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.grpc

import akka.Done
import akka.actor.ClassicActorSystemProvider
import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.discovery.Discovery
import akka.grpc.GrpcClientSettings
import akka.grpc.javadsl.AkkaGrpcClient
import akka.javasdk.grpc.GrpcClientProvider
import akka.javasdk.impl.Settings
import akka.javasdk.impl.grpc.GrpcClientProviderImpl.AuthHeaders
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import scala.annotation.nowarn
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.util.control.NonFatal

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

}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class GrpcClientProviderImpl(
    system: ActorSystem[_],
    settings: Settings,
    applicationConfig: Config,
    remoteIdentificationHeader: Option[AuthHeaders])
    extends GrpcClientProvider {
  import GrpcClientProviderImpl._
  import system.executionContext

  private val log = LoggerFactory.getLogger(classOf[GrpcClientProvider])

  private val clients = new ConcurrentHashMap[ClientKey, AkkaGrpcClient]()

  CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseServiceStop, "stop-grpc-clients")(() =>
    Future
      .traverse(clients.values().asScala)(_.close().asScala)
      .map(_ => Done))

  override def grpcClientFor[T <: AkkaGrpcClient](serviceClass: Class[T], serviceName: String): T = {
    val clientKey = ClientKey(serviceClass, serviceName)
    clients.computeIfAbsent(clientKey, createNewClientFor _).asInstanceOf[T]
  }

  // FIXME for testkit
  // /** This gets called by the testkit, and should impersonate the given principal. */
  // def impersonatingGrpcClient[T  <: AkkaGrpcClient](serviceClass: Class[T], service: String, port: Int, impersonate: String): T =
  // getGrpcClient(serviceClass, service, port, Some("impersonate-kalix-service" -> impersonate))

  private def createNewClientFor(clientKey: ClientKey): AkkaGrpcClient = {
    val clientSettings =
      if (isAkkaService(clientKey.serviceName)) {
        val akkaServiceClientSettings = if (settings.devModeSettings.isDefined) {
          // local service discovery when running locally
          // dev mode, other service name, use Akka discovery to find it
          // the runtime has set up a mechanism that finds locally running
          // services. Since in dev mode blocking is probably fine for now.
          try {
            val result = Await.result(Discovery(system).discovery.lookup(clientKey.serviceName, 5.seconds), 5.seconds)
            val address = result.addresses.head
            // port is always set
            val port = address.port.get
            log.debug(
              "Creating dev mode gRPC client for Akka service [{}] found at [{}:{}]",
              clientKey.serviceName,
              address.address,
              port)
            GrpcClientSettings
              .connectToServiceAt(address.host, port)(system)
              // (No TLS locally)
              .withTls(false)
          } catch {
            case NonFatal(ex) =>
              throw new RuntimeException(
                s"Failed to look up service [${clientKey.serviceName}] in dev-mode, make sure that it is also running " +
                "with a separate port and service name correctly defined in its application.conf under 'akka.javasdk.dev-mode.service-name' " +
                "if it differs from the maven project name",
                ex)
          }
        } else {
          log.debug("Creating gRPC client for Akka service [{}]", clientKey.serviceName)
          GrpcClientSettings
            .connectToServiceAt(clientKey.serviceName, 80)(system)
            // (TLS is handled for us by Kalix infra)
            .withTls(false)
        }

        remoteIdentificationHeader match {
          case Some(auth) => settingsWithCallCredentials(auth.headerName, auth.headerValue)(akkaServiceClientSettings)
          case None       => akkaServiceClientSettings
        }
      } else {
        // external/public gRPC service
        log.debug("Creating gRPC client for external service [{}]", clientKey.serviceName)

        // FIXME we should probably not allow any grpc client setting but a subset?
        // external service, details defined in user config
        GrpcClientSettings.fromConfig(clientKey.serviceName, applicationConfig)(system)
      }

    // Java API - static create
    val create =
      clientKey.clientClass.getMethod("create", classOf[GrpcClientSettings], classOf[ClassicActorSystemProvider])
    val client = create.invoke(null, clientSettings, system).asInstanceOf[AkkaGrpcClient]

    client.closed().asScala.foreach { _ =>
      // user should not close client, but just to be sure we don't keep it around if they do
      clients.remove(clientKey, client)
    }

    client
  }

}
