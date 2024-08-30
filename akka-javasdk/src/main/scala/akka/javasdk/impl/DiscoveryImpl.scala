/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.Done
import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown
import akka.annotation.InternalApi
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.empty.Empty
import kalix.protocol.action.Actions
import kalix.protocol.discovery._
import akka.javasdk.BuildInfo
import org.slf4j.LoggerFactory

import java.util
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.io.Source
import scala.jdk.CollectionConverters._

/**
 * INTERNAL API
 */
@InternalApi
class DiscoveryImpl(
    system: ActorSystem,
    services: Map[String, Service],
    aclDescriptor: FileDescriptorProto,
    sdkName: String,
    serviceName: Option[String])
    extends Discovery {
  import DiscoveryImpl._

  private val log = LoggerFactory.getLogger(getClass)

  private val serviceIncarnationUuid = UUID.randomUUID().toString

  // Delay CoordinatedShutdown until the runtime has been terminated.
  // This is updated from the `discover` call with a new Promise. Completed in the `proxyTerminated` call.
  private val runtimeTerminatedRef = new AtomicReference[Promise[Done]](Promise.successful(Done))

  CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "wait-for-proxy-terminated") { () =>
    runtimeTerminatedRef.get().future
  }

  private def configuredOrElse(key: String, default: String): String =
    if (system.settings.config.hasPath(key)) system.settings.config.getString(key) else default

  private def configuredIntOrElse(key: String, default: Int): Int =
    if (system.settings.config.hasPath(key)) system.settings.config.getInt(key) else default

  // detect hybrid runtime version probes when protocol version 0.0
  private def isVersionProbe(info: ProxyInfo): Boolean = {
    info.protocolMajorVersion == 0 && info.protocolMinorVersion == 0
  }

  /**
   * Discover what components the user function wishes to serve.
   */
  override def discover(in: ProxyInfo): scala.concurrent.Future[Spec] = {
    if (in.devMode && BuildInfo.runtimeVersion.compareTo(in.proxyVersion) > 0) {
      log.warn(
        "Your service is using an outdated runtime version (version: {}). It's recommended to update your image to '{}' in your docker-compose.yml",
        in.proxyVersion,
        s"${BuildInfo.runtimeImage}:${BuildInfo.runtimeVersion}")
    }

    // FIXME pull this into parameters for the Runner StartContext instead of only available once discovery happened
    ProxyInfoHolder(system).setProxyInfo(in)

    // FIXME is this needed anymore, we are running in the same process, so ENV is available
    // possibly filtered or hidden env, passed along for substitution in descriptor options
    val env: Map[String, String] = {
      if (system.settings.config.getBoolean("akka.javasdk.discovery.pass-along-env-all"))
        sys.env
      else {
        system.settings.config.getAnyRef("akka.javasdk.discovery.pass-along-env-allow") match {
          case allowed: util.ArrayList[String @unchecked] =>
            allowed.asScala.flatMap(name => sys.env.get(name).map(value => name -> value)).toMap
          case unexpected =>
            throw new IllegalArgumentException(
              s"The setting 'akka.javasdk.discovery.pass-along-env-allow' must be a list of env val names, but was [${unexpected}]")
        }
      }
    }

    val serviceInfo = ServiceInfo(
      serviceName = serviceName.getOrElse(""),
      serviceRuntime =
        sys.props.getOrElse("java.runtime.name", "") + " " + sys.props.getOrElse("java.runtime.version", ""),
      supportLibraryName = sdkName,
      supportLibraryVersion = configuredOrElse("akka.javasdk.library.version", BuildInfo.version),
      protocolMajorVersion =
        configuredIntOrElse("akka.javasdk.library.protocol-major-version", BuildInfo.protocolMajorVersion),
      protocolMinorVersion =
        configuredIntOrElse("akka.javasdk.library.protocol-minor-version", BuildInfo.protocolMinorVersion),
      // passed along for substitution in options
      env = env,
      serviceIncarnationUuid = serviceIncarnationUuid)

    if (isVersionProbe(in)) {
      // only (silently) send service info for hybrid runtime version probe
      Future.successful(Spec(serviceInfo = Some(serviceInfo)))
    } else {
      // don't wait for runtime termination in dev-mode, because the user service may be stopped without stopping the runtime
      val runtimeTerminatedPromise = if (in.devMode) Promise.successful[Done](Done) else Promise[Done]()
      runtimeTerminatedRef.getAndSet(runtimeTerminatedPromise).trySuccess(Done)

      log.debug(s"Supported sidecar entity types: {}", in.supportedEntityTypes.mkString("[", ",", "]"))

      val unsupportedServices = services.values.filterNot { service =>
        in.supportedEntityTypes.contains(service.componentType)
      }

      if (unsupportedServices.nonEmpty) {
        log.error(
          "Runtime doesn't support the entity types for the following services: {}",
          unsupportedServices
            .map(s => s.descriptor.getFullName + ": " + s.componentType)
            .mkString(", "))
        // Don't fail though. The runtime may give us more information as to why it doesn't support them if we send back unsupported services.
        // eg, the runtime doesn't have a configured journal, and so can't support event sourcing.
      }

      val components = services.map { case (name, service) =>
        val forwardHeaders = service.componentOptions.map(_.forwardHeaders().asScala.toSeq).getOrElse(Seq.empty)
        service.componentType match {
          case Actions.name =>
            Component(
              service.componentType,
              name,
              Component.ComponentSettings.Component(GenericComponentSettings(forwardHeaders)))
          case _ =>
            Component(
              service.componentType,
              name,
              Component.ComponentSettings.Entity(
                EntitySettings(
                  service.serviceName,
                  None,
                  service.componentOptions.map(_.forwardHeaders().asScala.toSeq).getOrElse(Nil),
                  EntitySettings.SpecificSettings.Empty)))
        }
      }.toSeq

      val fileDescriptorsBuilder = fileDescriptorSetBuilder(services.values)

      // For the SpringSDK, the ACL default descriptor is provided programmatically
      fileDescriptorsBuilder.addFile(aclDescriptor)

      val fileDescriptors = fileDescriptorsBuilder.build()
      Future.successful(Spec(fileDescriptors.toByteString, components, Some(serviceInfo)))
    }
  }

  /**
   * Report an error back to the user function. This will only be invoked to tell the user function that it has done
   * something wrong, eg, violated the protocol, tried to use an entity type that isn't supported, or attempted to
   * forward to an entity that doesn't exist, etc. These messages should be logged clearly for debugging purposes.
   */
  override def reportError(in: UserFunctionError): scala.concurrent.Future[com.google.protobuf.empty.Empty] = {
    val sourceMsgs = in.sourceLocations.map { location =>
      loadSource(location) match {
        case None if location.startLine == 0 && location.startCol == 0 =>
          s"At ${location.fileName}"
        case None =>
          s"At ${location.fileName}:${location.startLine + 1}:${location.startCol + 1}"
        case Some(source) =>
          s"At ${location.fileName}:${location.startLine + 1}:${location.startCol + 1}:${"\n"}$source"
      }
    }.toList
    val severityString = in.severity.name.take(1) + in.severity.name.drop(1).toLowerCase
    val message = s"$severityString reported from Kalix system: ${in.code} ${in.message}"
    val detail = if (in.detail.isEmpty) Nil else List(in.detail)
    val seeDocs = DocLinks(sdkName).forErrorCode(in.code).map(link => s"See documentation: $link").toList
    val messages = message :: detail ::: seeDocs ::: sourceMsgs
    val logMessage = messages.mkString("\n\n")

    // ignoring waring for runtime version
    // TODO: remove it once we remove this check in the runtime
    if (in.code != "KLX-00010") {
      in.severity match {
        case UserFunctionError.Severity.ERROR   => log.error(logMessage)
        case UserFunctionError.Severity.WARNING => log.warn(logMessage)
        case UserFunctionError.Severity.INFO    => log.info(logMessage)
        case UserFunctionError.Severity.UNSPECIFIED | UserFunctionError.Severity.Unrecognized(_) =>
          log.error(logMessage)
      }
    }

    Future.successful(com.google.protobuf.empty.Empty.defaultInstance)
  }

  override def healthCheck(in: Empty): Future[HealthCheckResponse] =
    Future.successful(HealthCheckResponse(serviceIncarnationUuid))

  private def loadSource(location: UserFunctionError.SourceLocation): Option[String] =
    if (location.endLine == 0 && location.endCol == 0) {
      // It's been sent without line/col data
      None
    } else {
      val resourceStream = getClass.getClassLoader.getResourceAsStream(location.fileName)
      if (resourceStream != null) {
        val lines = Source
          .fromInputStream(resourceStream, "utf-8")
          .getLines()
          .slice(location.startLine, location.endLine + 1)
          .take(6) // Don't render more than 6 lines, we don't want to fill the logs too much
          .toList
        if (lines.size > 1) {
          Some(lines.mkString("\n"))
        } else {
          lines.headOption
            .map { line =>
              line + "\n" + line.take(location.startCol).map {
                case '\t' => '\t'
                case _    => ' '
              } + "^"
            }
        }
      } else None
    }

  override def proxyTerminated(in: Empty): Future[Empty] = {
    log.debug("Runtime terminated")
    runtimeTerminatedRef.get().trySuccess(Done)
    Future.successful(Empty.defaultInstance)
  }
}

object DiscoveryImpl {

  private[impl] def fileDescriptorSetBuilder(services: Iterable[Service]) = {

    val descriptors = Map.empty[String, DescriptorProtos.FileDescriptorProto]

    val allDescriptors =
      AnySupport.flattenDescriptors(services.flatMap(s => s.descriptor.getFile +: s.additionalDescriptors).toSeq)

    val builder = DescriptorProtos.FileDescriptorSet.newBuilder()

    val descriptorsWithSource = descriptors.filter { case (_, proto) =>
      proto.hasSourceCodeInfo
    }
    allDescriptors.values.foreach { fd =>
      val proto = fd.toProto
      // We still use the descriptor as passed in by the user, but if we have one that we've read from the
      // descriptors file that has the source info, we add that source info to the one passed in, and use that.
      val protoWithSource = descriptorsWithSource.get(proto.getName).fold(proto) { withSource =>
        proto.toBuilder.setSourceCodeInfo(withSource.getSourceCodeInfo).build()
      }
      builder.addFile(protoWithSource)
    }
    builder
  }

}
