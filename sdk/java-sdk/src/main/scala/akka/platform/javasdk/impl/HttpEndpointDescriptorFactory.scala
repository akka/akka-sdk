/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl

import akka.http.scaladsl.model.HttpMethods
import akka.platform.javasdk.annotations.http.Delete
import akka.platform.javasdk.annotations.http.Endpoint
import akka.platform.javasdk.annotations.http.Get
import akka.platform.javasdk.annotations.http.Patch
import akka.platform.javasdk.annotations.http.Post
import akka.platform.javasdk.annotations.http.Put
import akka.platform.javasdk.impl.reflection.Reflect
import akka.platform.javasdk.spi.HttpEndpointConstructionContext
import akka.platform.javasdk.spi.HttpEndpointDescriptor
import akka.platform.javasdk.spi.HttpEndpointMethodDescriptor

object HttpEndpointDescriptorFactory {

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
        HttpEndpointMethodDescriptor(
          httpMethod = httpMethod,
          pathExpression = path,
          userMethod = method,
          // FIXME method level ACL will go here
          methodOptions = None)
      }
    }.toVector

    HttpEndpointDescriptor(
      mainPath = mainPath,
      instanceFactory = instanceFactory,
      methods = methods,
      // FIXME component level ACL will go here
      componentOptions = None)
  }

}
