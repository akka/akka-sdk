/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.actor.typed.ActorSystem
import akka.grpc.AkkaGrpcGenerated
import akka.grpc.ServiceDescription
import akka.grpc.scaladsl.InstancePerRequestFactory
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.runtime.sdk.spi.GrpcEndpointDescriptor
import akka.runtime.sdk.spi.GrpcEndpointRequestConstructionContext

import scala.concurrent.Future

object GrpcEndpointDescriptorFactory {

  def apply[T](grpcEndpointClass: Class[T], factory: () => T)(implicit
      system: ActorSystem[_]): GrpcEndpointDescriptor[T] = {
    val serviceDefinitionClass: Class[_] = grpcEndpointClass.getSuperclass

    // Validate that it is a grpc service (no supertype so this is the best we can do)
    if (serviceDefinitionClass.getAnnotation(classOf[AkkaGrpcGenerated]) == null) {
      throw new IllegalArgumentException(
        s"Class [${grpcEndpointClass.getName}] is with @GrpcEndpoint but the direct supertype [${serviceDefinitionClass.getName}] generated by Akka gRPC. " +
        "This is not supported.")
    }

    // FIXME a derivative should be injectable into user code as well
    val instanceFactory = { (_: GrpcEndpointRequestConstructionContext) =>
      factory()
    }

    val handlerFactory =
      system.dynamicAccess
        .createInstanceFor[InstancePerRequestFactory[T]](serviceDefinitionClass.getName + "ScalaHandlerFactory", Nil)
        .get

    val routeFactory: (HttpRequest => T) => PartialFunction[HttpRequest, Future[HttpResponse]] = { serviceFactory =>
      handlerFactory.partialInstancePerRequest(
        serviceFactory,
        "",
        // FIXME default error handler, is it fine to leave like this, should runtime define?
        PartialFunction.empty,
        system)
    }

    // Pick up generated companion object for file descriptor (for reflection) and creating router
    // static akka.grpc.ServiceDescription description in generated service interface
    val description = serviceDefinitionClass.getField("description").get(null).asInstanceOf[ServiceDescription]
    if (description eq null)
      throw new RuntimeException(
        s"Could not access static description from gRPC service interface [${serviceDefinitionClass.getName}]")

    new GrpcEndpointDescriptor[T](
      grpcEndpointClass.getName,
      description.name,
      description.descriptor,
      instanceFactory,
      routeFactory)
  }

}
