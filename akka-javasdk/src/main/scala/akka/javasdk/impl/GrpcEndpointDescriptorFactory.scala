/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.actor.typed.ActorSystem
import akka.grpc.ServiceDescription
import akka.grpc.scaladsl.InstancePerRequestFactory
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.javasdk.annotations.Acl
import akka.javasdk.impl.AclDescriptorFactory.deriveAclOptions
import akka.javasdk.impl.ComponentDescriptorFactory.hasAcl
import akka.runtime.sdk.spi.ComponentOptions
import akka.runtime.sdk.spi.GrpcEndpointDescriptor
import akka.runtime.sdk.spi.GrpcEndpointRequestConstructionContext
import akka.runtime.sdk.spi.MethodOptions
import io.opentelemetry.api.trace.Span

import scala.concurrent.Future

object GrpcEndpointDescriptorFactory {

  val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(GrpcEndpointDescriptorFactory.getClass)

  def apply[T](grpcEndpointClass: Class[T], factory: Option[Span] => T)(implicit
      system: ActorSystem[_]): GrpcEndpointDescriptor[T] = {
    // FIXME now way right now to know that it is a gRPC service interface
    val serviceDefinitionClass: Class[_] = {
      val interfaces = grpcEndpointClass.getInterfaces
      if (interfaces.length != 1) {
        throw new IllegalArgumentException(
          s"Class [${grpcEndpointClass.getName}] must implement exactly one interface, the gRPC service generated by Akka gRPC.")
      }
      interfaces(0)
    }

    // FIXME a derivative should be injectable into user code as well
    val instanceFactory = { (ctx: GrpcEndpointRequestConstructionContext) =>
      factory(ctx.openTelemetrySpan)
    }

    val handlerFactory =
      system.dynamicAccess
        .createInstanceFor[InstancePerRequestFactory[T]](serviceDefinitionClass.getName + "ScalaHandlerFactory", Nil)
        .get

    // Pick up generated companion object for file descriptor (for reflection) and creating router
    // static akka.grpc.ServiceDescription description in generated service interface
    val description = serviceDefinitionClass.getField("description").get(null).asInstanceOf[ServiceDescription]
    if (description eq null)
      throw new RuntimeException(
        s"Could not access static description from gRPC service interface [${serviceDefinitionClass.getName}]")

    val routeFactory: (HttpRequest => T) => PartialFunction[HttpRequest, Future[HttpResponse]] = { serviceFactory =>
      handlerFactory.partialInstancePerRequest(
        serviceFactory,
        description.name,
        // FIXME default error handler, is it fine to leave like this, should runtime define?
        PartialFunction.empty,
        system)
    }

    val componentOptions =
      new ComponentOptions(deriveAclOptions(Option(grpcEndpointClass.getAnnotation(classOf[Acl]))), None)

    val methodOptions: Map[String, MethodOptions] = grpcEndpointClass.getMethods
      .filter(m => hasAcl(m))
      .map { m =>
        // FIXME just do to lower case and change runtime to handle it
        capitalizeFirstLetter(m.getName) -> new MethodOptions(
          deriveAclOptions(Option(m.getAnnotation(classOf[Acl]))),
          None)
      }
      .toMap
    new GrpcEndpointDescriptor[T](
      grpcEndpointClass.getName,
      description.name,
      description.descriptor,
      instanceFactory,
      routeFactory,
      componentOptions,
      methodOptions)
  }

  def capitalizeFirstLetter(str: String): String = {
    s"${str.charAt(0).toUpper}${str.substring(1)}"
  }

}
