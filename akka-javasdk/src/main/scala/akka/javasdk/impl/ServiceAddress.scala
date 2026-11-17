/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.runtime.sdk.spi.DeploymentInfo

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object ServiceAddress {

  // same rules as the runtime applies to the platform service name of a deployment
  private val MaxPlatformServiceNameLength = 26
  private val PlatformServiceNameRegex = "[a-z0-9](?:[a-z0-9-]*[a-z0-9])?".r

  /**
   * Returns the name if it is a valid platform service name, throws IllegalArgumentException otherwise.
   */
  def validPlatformService(name: String): String = {
    require(
      name != null && name.length <= MaxPlatformServiceNameLength && PlatformServiceNameRegex.matches(name),
      s"Invalid platform service name [$name], must be a lowercase DNS label of at most [$MaxPlatformServiceNameLength] characters")
    require(name != "svc", s"Invalid platform service name [$name], the name is reserved")
    name
  }

  /**
   * The platform service from an annotation value, where an empty value means a service in this project.
   */
  def platformServiceFromAnnotation(value: String): Option[String] =
    Option(value).filter(_.nonEmpty).map(validPlatformService)

  /**
   * The platform service of a `@Consume.FromServiceStream` annotation. A platform service only goes together with a
   * bare service name, not with a host name or a placeholder.
   */
  def streamPlatformService(service: String, platformService: String, componentClass: Class[_]): Option[String] =
    try {
      val result = platformServiceFromAnnotation(platformService)
      if (result.isDefined)
        require(
          isBareServiceName(service) && !service.contains("${"),
          s"Service [$service] must be a bare service name when a platform service is defined")
      result
    } catch {
      case e: IllegalArgumentException =>
        throw new IllegalArgumentException(
          s"Invalid @Consume.FromServiceStream on [${componentClass.getName}]: ${e.getMessage}",
          e)
    }

  private def isBareServiceName(name: String): Boolean =
    !name.contains('.') && !name.contains(':')

  /**
   * The platform service that service names are resolved in, for a provider scoped to `platformService`. None when the
   * deployment is itself part of that platform service, so that the bare name resolves in the own namespace.
   */
  def scopeFor(platformService: String, deploymentInfo: DeploymentInfo): Option[String] = {
    val valid = validPlatformService(platformService)
    deploymentInfo.platformService match {
      case Some(own) if own == valid => None
      case Some(own) =>
        throw new IllegalArgumentException(
          s"Cannot call services in platform service [$valid] from platform service [$own], " +
          "only services in the same platform service or the parent project can be called")
      case None => Some(valid)
    }
  }

  /**
   * The host to reach an Akka service at over the service mesh in production. A service in this project is reached by
   * its bare name in the own namespace. A service that belongs to a platform service enabled in this project runs in
   * the namespace `<project-id>-<platform-service>` and is reached by its fully qualified cluster name. The namespace
   * rule must match `DeploymentSettings.platformServiceNamespace` in the runtime.
   */
  def hostFor(serviceName: String, deploymentInfo: DeploymentInfo, platformService: Option[String]): String =
    platformService match {
      case Some(platformServiceName) =>
        require(
          deploymentInfo.projectId.nonEmpty,
          s"Cannot resolve service [$serviceName] in platform service [$platformServiceName], no project id known for this deployment")
        s"$serviceName.${deploymentInfo.projectId}-$platformServiceName.svc.cluster.local"
      case None => serviceName
    }
}
