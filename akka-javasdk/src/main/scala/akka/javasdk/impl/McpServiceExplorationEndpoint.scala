/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.impl.Mcp.ResourceContents
import akka.javasdk.impl.Mcp.TextResourceContents
import akka.runtime.sdk.spi.GrpcEndpointDescriptor
import akka.runtime.sdk.spi.HttpEndpointDescriptor

import scala.jdk.CollectionConverters.CollectionHasAsScala

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object McpServiceExplorationEndpoint {

  def apply(httpEndpoints: Seq[HttpEndpointDescriptor], grpcEndpoints: Seq[GrpcEndpointDescriptor[_]])(implicit
      actorSystem: ActorSystem[_]): HttpEndpointDescriptor = {

    val httpEndpointsOverview = {
      if (httpEndpoints.isEmpty) None
      else {
        Some(
          "## The service contains the following HTTP endpoints\n" +
          httpEndpoints
            .map(endpoint =>
              s" * ${endpoint.implementationName} with root path ${endpoint.mainPath.getOrElse(
                "")} and expression ${endpoint.methods.map(_.pathExpression)}")
            .mkString("\n", "\n", "\n"))
      }
    }

    val grpcEndpointOverview = {
      if (grpcEndpoints.isEmpty) None
      else {
        Some("## The service contains the following gRPC endpoints\n" +
        grpcEndpoints.map(endpoint =>
          s" * ${endpoint.grpcServiceName} with methods ${endpoint.fileDescriptor.findServiceByName(endpoint.grpcServiceName).getMethods.asScala.map(_.getName)}"))
      }
    }

    val apiOverview = Seq(httpEndpointsOverview, grpcEndpointOverview).flatten

    val resources =
      if (apiOverview.nonEmpty)
        Seq[(Mcp.Resource, () => ResourceContents)](
          (
            Mcp.Resource(
              "fixme",
              "service API overview",
              Some("A listing of what endpoints are in this service"),
              mimeType = Some("text/markdown"),
              annotations = None,
              size = None),
            () => TextResourceContents(apiOverview.mkString("\n"))))
      else Seq.empty

    // reflect over components and describe
    new Mcp.StatelessMcpEndpoint(resources).httpEndpoint()
  }

}
