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

  /**
   * The host to reach an Akka service at over the service mesh in production. A service in this project is reached by
   * its bare name in the own namespace. A service in a system feature enabled in this project runs in the feature
   * namespace `<project-id>-<feature>` and is reached by its fully qualified cluster name.
   */
  def hostFor(serviceName: String, deploymentInfo: DeploymentInfo, systemFeature: Option[String]): String =
    systemFeature match {
      case Some(feature) =>
        require(
          deploymentInfo.projectId.nonEmpty,
          s"Cannot resolve service [$serviceName] in system feature [$feature], no project id known for this deployment")
        s"$serviceName.${deploymentInfo.projectId}-$feature.svc.cluster.local"
      case None => serviceName
    }
}
