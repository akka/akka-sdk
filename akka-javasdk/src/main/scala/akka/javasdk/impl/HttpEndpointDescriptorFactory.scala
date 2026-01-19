/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.NotUsed

import java.lang.reflect.Method
import scala.annotation.tailrec
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import akka.annotation.InternalApi
import akka.http.javadsl.model.HttpEntity
import akka.http.javadsl.model.HttpRequest
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.Uri.Path
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
import akka.javasdk.impl.Validations.Invalid
import akka.javasdk.impl.Validations.Validation
import akka.javasdk.impl.reflection.Reflect
import akka.runtime.sdk.spi.ComponentOptions
import akka.runtime.sdk.spi.HttpEndpointConstructionContext
import akka.runtime.sdk.spi.HttpEndpointDescriptor
import akka.runtime.sdk.spi.HttpEndpointMethodDescriptor
import akka.runtime.sdk.spi.HttpEndpointMethodSpec
import akka.runtime.sdk.spi.MethodOptions
import akka.runtime.sdk.spi.SpiJsonSchema
import akka.util.ByteString
import org.slf4j.LoggerFactory

import java.lang.reflect.ParameterizedType

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object HttpEndpointDescriptorFactory {

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

    val methodsWithValidation: Seq[Either[Validation, HttpEndpointMethodDescriptor]] =
      endpointClass.getDeclaredMethods.toVector.flatMap { method =>

        def methodName = s"${endpointClass.getName}.${method.getName}"

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
          val parsedPath = Path(fullPathExpression)

          def invalid(message: String): (Validation, Int) =
            (Validations.Invalid(message), 0)

          @tailrec
          def validatePath(pathLeft: Path, parameterNames: List[String], count: Int = 0): (Validation, Int) =
            pathLeft match {
              case Path.Segment(segment, rest) if segment.startsWith("{") =>
                // path variable, match against parameter name
                val name = segment.drop(1).dropRight(1)
                if (parameterNames.isEmpty)
                  invalid(
                    s"There are more parameters in the path expression [$fullPathExpression] than there are parameters for [$methodName]")
                else {
                  val nextParamName = parameterNames.head
                  if (name != nextParamName)
                    invalid(
                      s"The parameter [$name] in the path expression [$fullPathExpression] does not match the method parameter name [$nextParamName] for [$methodName]. " +
                      "The parameter names in the expression must match the parameters of the method.")
                  else
                    validatePath(rest, parameterNames.tail, count + 1)
                }
              case Path.Segment("**", rest) if !rest.isEmpty =>
                invalid(s"Wildcard path can only be the last segment of the path [$fullPathExpression]")

              case Path.Segment(_, rest) =>
                // non variable segment
                validatePath(rest, parameterNames, count)
              case Path.Slash(rest) =>
                validatePath(rest, parameterNames, count)
              case Path.Empty =>
                // end of expression, either no more params or one more param for the request body
                if (parameterNames.length > 1)
                  invalid(
                    s"There are [${parameterNames.size}] parameters ([${parameterNames.mkString(",")}]) for endpoint method [$methodName] not matched by the path expression. " +
                    "The parameter count and names should match the expression, with one additional possible parameter in the end for the request body.")
                else Validations.Valid -> count
            }

          if (webSocket) {
            val returnType = method.getReturnType
            if (returnType != classOf[akka.stream.javadsl.Flow[_, _, _]])
              invalid(
                s"Wrong return type for WebSocket method [$methodName], must be [akka.stream.javadsl.Flow] but was [$returnType]")
            method.getGenericReturnType match {
              case p: ParameterizedType =>
                val typeArgs = p.getActualTypeArguments
                val in = typeArgs(0)
                val out = typeArgs(1)
                val mat = typeArgs(2)
                if (in != out)
                  invalid(
                    s"WebSocket method [$methodName] has different types of Flow in and out messages, both must be the same.")
                if (in != classOf[String] && in != classOf[ByteString] && in != classOf[
                    akka.http.javadsl.model.ws.Message])
                  invalid(
                    s"WebSocket method [$methodName] has unsupported message type [$in], must be String for text messages, " +
                    "akka.util.ByteString for binary messages or akka.http.javadsl.model.ws.Message for low level protocol handling.")
                if (mat != classOf[NotUsed])
                  invalid(
                    s"WebSocket method [$methodName] has unsupported materialized value type [$mat], must be akka.NotUsed")

              case huh =>
                // won't ever happen because check above
                throw new IllegalArgumentException(s"Unexpected WebSocket return type for [$methodName]: [$huh]")
            }
          }

          validatePath(parsedPath, method.getParameters.toList.map(_.getName)) match {
            case (Validations.Valid, pathParameterCount) =>
              val methodSpec = deriveSpec(method, pathParameterCount)
              if (webSocket && methodSpec.requestBody.isDefined)
                invalid(s"Request body parameter defined for WebSocket method [$methodName], this is not supported")

              Right(
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
                  methodSpec = methodSpec,
                  webSocket = webSocket))
            case (invalid, _) => Left(invalid)
          }
        }
      }

    val (errors, methods) = methodsWithValidation.partitionMap(identity)

    if (errors.nonEmpty) {
      val summedUp = errors
        .foldLeft(Validations.Valid: Validation)((validation, invalid) => validation ++ invalid)
        .asInstanceOf[Invalid]
      summedUp.throwFailureSummary()
    } else {
      new HttpEndpointDescriptor(
        mainPath = Some(mainPath),
        instanceFactory = instanceFactory,
        methods = methods.toVector,
        componentOptions = new ComponentOptions(
          deriveAclOptions(Option(endpointClass.getAnnotation(classOf[Acl]))),
          deriveJWTOptions(Option(endpointClass.getAnnotation(classOf[JWT])), endpointClass.getCanonicalName)),
        implementationClassName = endpointClass.getName,
        objectMapper = Some(JsonSupport.getObjectMapper))
    }
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
