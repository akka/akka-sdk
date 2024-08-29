/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.http.scaladsl.model.HttpMethods
import akka.javasdk.impl.reflection.Reflect
import AclDescriptorFactory.validateMatcher
import akka.annotation.InternalApi
import akka.javasdk.annotations.Acl
import akka.javasdk.annotations.http.Delete
import akka.javasdk.annotations.http.Endpoint
import akka.javasdk.annotations.http.Get
import akka.javasdk.annotations.http.Patch
import akka.javasdk.annotations.http.Post
import akka.javasdk.annotations.http.Put
import akka.runtime.sdk.spi.ACL
import akka.runtime.sdk.spi.All
import akka.runtime.sdk.spi.ComponentOptions
import akka.runtime.sdk.spi.HttpEndpointConstructionContext
import akka.runtime.sdk.spi.HttpEndpointDescriptor
import akka.runtime.sdk.spi.HttpEndpointMethodDescriptor
import akka.runtime.sdk.spi.Internet
import akka.runtime.sdk.spi.MethodOptions
import akka.runtime.sdk.spi.PrincipalMatcher
import akka.runtime.sdk.spi.ServiceNamePattern

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object HttpEndpointDescriptorFactory {

  def apply(
      endpointClass: Class[_],
      instanceFactory: HttpEndpointConstructionContext => Any): HttpEndpointDescriptor = {
    assert(Reflect.isRestEndpoint(endpointClass))

    val mainPath = Option(endpointClass.getAnnotation(classOf[Endpoint])).map(_.value()).filterNot(_.isEmpty)

    val methods = endpointClass.getDeclaredMethods.flatMap { method =>
      val maybePathMethod = if (method.getAnnotation(classOf[Get]) != null) {
        val path = method.getAnnotation(classOf[Get]).value()
        Some((path, HttpMethods.GET))
      } else if (method.getAnnotation(classOf[Post]) != null) {
        val path = method.getAnnotation(classOf[Post]).value()
        Some((path, HttpMethods.POST))
      } else if (method.getAnnotation(classOf[Put]) != null) {
        val path = method.getAnnotation(classOf[Put]).value()
        Some((path, HttpMethods.PUT))
      } else if (method.getAnnotation(classOf[Patch]) != null) {
        val path = method.getAnnotation(classOf[Patch]).value()
        Some((path, HttpMethods.PATCH))
      } else if (method.getAnnotation(classOf[Delete]) != null) {
        val path = method.getAnnotation(classOf[Delete]).value()
        Some((path, HttpMethods.DELETE))
      } else {
        // non HTTP-available user method
        None
      }

      maybePathMethod.map { case (path, httpMethod) =>
        new HttpEndpointMethodDescriptor(
          httpMethod = httpMethod,
          pathExpression = path,
          userMethod = method,
          methodOptions = deriveAclOptions(Option(method.getAnnotation(classOf[Acl]))).map(new MethodOptions(_)))
      }
    }.toVector

    new HttpEndpointDescriptor(
      mainPath = mainPath,
      instanceFactory = instanceFactory,
      methods = methods,
      componentOptions =
        deriveAclOptions(Option(endpointClass.getAnnotation(classOf[Acl]))).map(new ComponentOptions(_)))
  }

  // receives the method, checks if it is annotated with @Acl and if so,
  // converts that into ACL spi object
  private def deriveAclOptions(aclAnnotation: Option[Acl]): Option[ACL] =
    aclAnnotation.map { ann =>
      ann.allow().foreach(matcher => validateMatcher(matcher))
      ann.deny().foreach(matcher => validateMatcher(matcher))

      new ACL(
        allow = Option(ann.allow).map(toPrincipalMatcher).getOrElse(Nil),
        deny = Option(ann.deny).map(toPrincipalMatcher).getOrElse(Nil),
        denyHttpCode = None // FIXME we can probably use http codes instead of grpc ones
      )
    }

  private def toPrincipalMatcher(matchers: Array[Acl.Matcher]): List[PrincipalMatcher] =
    matchers.map { m =>
      m.principal match {
        case Acl.Principal.ALL         => All
        case Acl.Principal.INTERNET    => Internet
        case Acl.Principal.UNSPECIFIED => new ServiceNamePattern(m.service())
      }
    }.toList

}
