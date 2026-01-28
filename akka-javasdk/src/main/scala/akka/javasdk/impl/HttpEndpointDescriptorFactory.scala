/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.lang.reflect.Method

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.annotation.InternalApi
import akka.http.javadsl.model.HttpEntity
import akka.http.javadsl.model.HttpRequest
import akka.http.scaladsl.model.HttpMethods
import akka.javasdk.JsonSupport
import akka.javasdk.annotations.Acl
import akka.javasdk.annotations.JWT
import akka.javasdk.annotations.http.Delete
import akka.javasdk.annotations.http.Get
import akka.javasdk.annotations.http.HttpEndpoint
import akka.javasdk.annotations.http.Patch
import akka.javasdk.annotations.http.Post
import akka.javasdk.annotations.http.Put
import akka.javasdk.annotations.http.WebSocket
import akka.javasdk.impl.AclDescriptorFactory.deriveAclOptions
import akka.javasdk.impl.JwtDescriptorFactory.deriveJWTOptions
import akka.javasdk.impl.reflection.Reflect
import akka.runtime.sdk.spi.ComponentOptions
import akka.runtime.sdk.spi.HttpEndpointConstructionContext
import akka.runtime.sdk.spi.HttpEndpointDescriptor
import akka.runtime.sdk.spi.HttpEndpointMethodDescriptor
import akka.runtime.sdk.spi.HttpEndpointMethodSpec
import akka.runtime.sdk.spi.MethodOptions
import akka.runtime.sdk.spi.SpiJsonSchema
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object HttpEndpointDescriptorFactory {

  private val PathVariablePattern = """\{[^}]+\}""".r

  val logger = LoggerFactory.getLogger(classOf[HttpEndpointDescriptorFactory.type])

  def apply(
      endpointClass: Class[_],
      instanceFactory: HttpEndpointConstructionContext => Any): HttpEndpointDescriptor = {
    assert(Reflect.isRestEndpoint(endpointClass))

    // make sure it is predictable - always starts with slash and ends with slash, if undefined, always "/"
    val mainPath = Option(endpointClass.getAnnotation(classOf[HttpEndpoint])).map(_.value()) match {
      case None     => "/"
      case Some("") => "/"
      case Some(path) =>
        val startingWithSlash =
          if (path.startsWith("/")) path
          else "/" + path
        val startingAndEndingWithSlash =
          if (startingWithSlash.endsWith("/")) startingWithSlash
          else startingWithSlash + "/"
        startingAndEndingWithSlash
    }

    // Note: validation is now done at compile-time by HttpEndpointValidations
    val methods: Vector[HttpEndpointMethodDescriptor] =
      endpointClass.getDeclaredMethods.toVector.flatMap { method =>

        val maybePathMethod = if (method.getAnnotation(classOf[Get]) != null) {
          val path = method.getAnnotation(classOf[Get]).value()
          Some((path, HttpMethods.GET, false))
        } else if (method.getAnnotation(classOf[Post]) != null) {
          val path = method.getAnnotation(classOf[Post]).value()
          Some((path, HttpMethods.POST, false))
        } else if (method.getAnnotation(classOf[Put]) != null) {
          val path = method.getAnnotation(classOf[Put]).value()
          Some((path, HttpMethods.PUT, false))
        } else if (method.getAnnotation(classOf[Patch]) != null) {
          val path = method.getAnnotation(classOf[Patch]).value()
          Some((path, HttpMethods.PATCH, false))
        } else if (method.getAnnotation(classOf[Delete]) != null) {
          val path = method.getAnnotation(classOf[Delete]).value()
          Some((path, HttpMethods.DELETE, false))
        } else if (method.getAnnotation(classOf[WebSocket]) != null) {
          val path = method.getAnnotation(classOf[WebSocket]).value()
          Some((path, HttpMethods.GET, true))
        } else {
          // non HTTP-available user method
          None
        }

        maybePathMethod.map { case (rawPath, httpMethod, webSocket) =>
          // make sure individual method paths are consistently relative to the prefix, which always starts with slash
          val path =
            if (rawPath.startsWith("/")) rawPath.drop(1)
            else rawPath

          val fullPathExpression = mainPath + path
          val pathParameterCount = PathVariablePattern.findAllIn(fullPathExpression).length

          new HttpEndpointMethodDescriptor(
            httpMethod = httpMethod,
            pathExpression = path,
            userMethod = method,
            methodOptions = new MethodOptions(
              deriveAclOptions(Option(method.getAnnotation(classOf[Acl]))),
              deriveJWTOptions(
                Option(method.getAnnotation(classOf[JWT])),
                endpointClass.getCanonicalName,
                Some(method))),
            methodSpec = deriveSpec(method, pathParameterCount),
            webSocket = webSocket)
        }
      }

    new HttpEndpointDescriptor(
      mainPath = Some(mainPath),
      instanceFactory = instanceFactory,
      methods = methods,
      componentOptions = new ComponentOptions(
        deriveAclOptions(Option(endpointClass.getAnnotation(classOf[Acl]))),
        deriveJWTOptions(Option(endpointClass.getAnnotation(classOf[JWT])), endpointClass.getCanonicalName)),
      implementationClassName = endpointClass.getName,
      objectMapper = Some(JsonSupport.getObjectMapper))
  }

  private[impl] def deriveSpec(method: Method, pathParameterCount: Int): HttpEndpointMethodSpec = {
    val typedParameters = JsonSchema.typedParametersFor(method)
    val hasBodyParameter = pathParameterCount + 1 == typedParameters.size
    val (pathParameters, bodyParameter) =
      if (hasBodyParameter) (typedParameters.dropRight(1), typedParameters.lastOption)
      else (typedParameters, None)
    val parameters = pathParameters.map { parameter =>
      new HttpEndpointMethodSpec.PathParameter(parameter.name, parameter.schemaType)
    }
    val requestBody = bodyParameter.map { parameter =>
      val parameterClass = parameter.parameterClass
      if (parameterClass == classOf[HttpRequest] || parameterClass == classOf[HttpEntity.Strict])
        new HttpEndpointMethodSpec.LowLevelRequestBody(parameter.description, parameter.required)
      else
        parameter.schemaType match {
          case string: SpiJsonSchema.JsonSchemaString =>
            new HttpEndpointMethodSpec.TextRequestBody(string.description)
          case json =>
            new HttpEndpointMethodSpec.JsonRequestBody(json)
        }
    }
    new HttpEndpointMethodSpec(parameters, requestBody)
  }

  private[impl] def extractEnvVars(claimValueContent: String, claimRef: String): String = {
    val pattern = """\$\{([A-Z_][A-Z0-9_]*)\}""".r

    pattern.replaceAllIn(
      claimValueContent,
      matched => {
        val varName = matched.group(1)
        Try(sys.env(varName)) match {
          case Success(varValue) => varValue
          case Failure(ex) =>
            throw new IllegalArgumentException(
              s"[${ex.getMessage}] env var is missing but it is used in claim [$claimValueContent] in [$claimRef].")
        }
      })
  }

}
