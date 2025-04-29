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
        Some("## The service contains the following HTTP endpoints\n" +
        httpEndpoints
          .map { endpoint =>
            val mainPath = endpoint.mainPath.getOrElse("/")
            s" * ${endpoint.implementationName} with paths: ${endpoint.methods.map(method => mainPath + method.pathExpression).mkString(", ")}"
          }
          .mkString("\n", "\n", "\n"))
      }
    }

    val grpcEndpointOverview = {
      if (grpcEndpoints.isEmpty) None
      else {
        Some("## The service contains the following gRPC endpoints\n" +
        grpcEndpoints
          .map { endpoint =>
            val serviceName = endpoint.grpcServiceName.split("\\.").last // name without package for lookup
            s" * ${endpoint.grpcServiceName} with methods: ${endpoint.fileDescriptor.findServiceByName(serviceName).getMethods.asScala.map(_.getName).mkString(", ")}"
          }
          .mkString("\n", "\n", "\n"))
      }
    }

    val apiOverview = Seq(httpEndpointsOverview, grpcEndpointOverview).flatten

    val resources =
      if (apiOverview.nonEmpty) {
        val resourceUri = "file://service-overview.md"
        val markdown = "text/markdown"
        Seq[(Mcp.Resource, () => Seq[ResourceContents])](
          (
            Mcp.Resource(
              resourceUri,
              "Service overview",
              Some("An overview of this service and what APIs it provides"),
              mimeType = Some(markdown),
              annotations = None,
              size = None),
            () => Seq(TextResourceContents(apiOverview.mkString("\n"), mimeType = markdown, uri = resourceUri))))
      } else Seq.empty

    // reflect over components and describe
    new Mcp.StatelessMcpEndpoint(Mcp.McpDescriptor(resources, Seq.empty)).httpEndpoint()
  }

}
