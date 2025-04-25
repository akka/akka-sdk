/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.ComponentOptions
import akka.runtime.sdk.spi.HttpEndpointConstructionContext
import akka.runtime.sdk.spi.HttpEndpointDescriptor
import akka.runtime.sdk.spi.HttpEndpointMethodDescriptor
import akka.runtime.sdk.spi.MethodOptions
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

/**
 * INTERNAL API
 */
@InternalApi
object JsonRpc {

  /*
   * Implementation of JSON-RPC 2.0 according to https://www.jsonrpc.org/specification
   *
   */

  // protocol messages

  /**
   * @param jsonRpc
   *   must be "2.0"
   * @param method
   *   the name of the method
   * @param params
   *   Either a Map with properties by name, or a List of parameters.
   * @param id
   *   request identifier, it must be present for requests with responses, must be a String, Number or null, field is
   *   omitted for notifications. The id is passed back through responses to identify which request a response was for
   *   and to allow out of request order responses. FIXME maybe we dont support Null which is just weird
   */
  final case class JsonRpcRequest(jsonRpc: String = "2.0", method: String, params: Option[AnyRef], id: Option[Any]) {
    def isNotification = id.isEmpty
  }

  sealed trait JsonRpcResponse {
    def id: Option[Any]
  }
  final case class JsonRpcSuccessResponse(id: Option[Any], result: Any) extends JsonRpcResponse
  final case class JsonRpcErrorResponse(id: Option[Any], error: JsonRpcError) extends JsonRpcResponse
  object JsonRpcError {
    object Codes {
      val ParseError = -32700
      val InvalidRequest = -32600
      val MethodNotFound = -32601
      val InvalidParams = -32602
      val InternalError = -32603
      // Note: -32000 - 32099 are reserved for "implementation-defined server-errors".
    }
  }

  final case class JsonRpcError(code: Int, message: String, data: Option[Any])

  // FIXME authentication/authorization

  object Serialization {
    private val mapper = JsonSerializer.newObjectMapperWithDefaults()
    mapper.registerModule(DefaultScalaModule)
    mapper.setSerializationInclusion(Include.NON_EMPTY)

    def requestToJsonString(request: JsonRpcRequest): String =
      mapper.writeValueAsString(request)

    def parseRequest(bytes: ByteString): Seq[JsonRpcRequest] = {
      val jsonText = bytes.utf8String
      if (jsonText.startsWith("[")) {
        // batch
        mapper
          .readerForListOf(classOf[JsonRpcRequest])
          .readValue[java.util.List[JsonRpcRequest]](jsonText)
          .asScala
          .toVector
      } else if (jsonText.isEmpty) {
        // empty request (also ok for some reason)
        Seq.empty
      } else {
        // single request
        Seq(mapper.readValue(jsonText, classOf[JsonRpcRequest]))
      }
    }
    def responseToJsonBytes(jsonRpcResponse: JsonRpcResponse) =
      ByteString.fromArrayUnsafe(mapper.writeValueAsBytes(jsonRpcResponse))

    def parseResponse(response: String): JsonRpcResponse = {
      if (response.contains("error")) {
        mapper.readValue(response, classOf[JsonRpcErrorResponse])
      } else {
        mapper.readValue(response, classOf[JsonRpcSuccessResponse])
      }
    }

  }

  final class JsonRpcEndpoint(path: String, methods: Map[String, JsonRpcRequest => Future[JsonRpcResponse]])(implicit
      ec: ExecutionContext) {

    private final val log = LoggerFactory.getLogger(getClass)

    val httpEndpointDescriptor: HttpEndpointDescriptor = new HttpEndpointDescriptor(
      mainPath = Some(path),
      instanceFactory = constructionContext => new HttpRequestHandler(constructionContext),
      methods = Seq(
        new HttpEndpointMethodDescriptor(
          HttpMethods.POST,
          "",
          classOf[HttpRequestHandler].getMethod("handle", classOf[HttpEntity.Strict]),
          new MethodOptions(None, None))),
      componentOptions = new ComponentOptions(None, None), // TODO ACL
      implementationClassName = s"json-rpc-2-$path",
      objectMapper = None // we deserialize ourselves
    )

    class HttpRequestHandler(constructionContext: HttpEndpointConstructionContext) {

      def handle(entity: HttpEntity.Strict): Future[HttpResponse] = {
        // FIXME validate content type
        if (entity.contentType != ContentTypes.`application/json`)
          Future.successful(HttpResponse(StatusCodes.UnsupportedMediaType))
        else {
          // FIXME handle SSE as well (what does it really mean, batch responses are already streamed?)
          // Note: HTTP transport for JSON-RPC isn't really well documented/spec:ed, looking at MCP and seeing what they dictate
          Serialization.parseRequest(entity.data) match {
            case Seq(single) =>
              log.debug("Handling JSON-RPC request for [{}]", single.method)
              handleRequest(single).map(jsonRpcResponse =>
                HttpResponse(entity =
                  HttpEntity(ContentTypes.`application/json`, Serialization.responseToJsonBytes(jsonRpcResponse))))
            case Seq() =>
              log.debug("Empty JSON-RPC request, returning HTTP 202 Accepted")
              Future.successful(HttpResponse(StatusCodes.Accepted))
            case batch =>
              if (log.isDebugEnabled)
                log.debug(
                  "Handling batch of {} JSON-RPC requests for [{}]",
                  batch.size,
                  batch.map(_.method).mkString(","))
              Future.successful(
                HttpResponse(entity = HttpEntity(
                  ContentTypes.`application/json`,
                  Source(batch)
                    .mapAsyncUnordered(8)(handleRequest) // FIXME parallelism
                    .map(Serialization.responseToJsonBytes))))
          }
        }
      }

      def handleRequest(request: JsonRpcRequest): Future[JsonRpcResponse] = {
        methods.get(request.method) match {
          case Some(handler) =>
            try {
              handler(request)
            } catch {
              case NonFatal(ex) =>
                log.warn(s"JSON-RPC call to endpoint $path method ${request.method} id ${request.id} failed", ex)
                Future.successful(
                  JsonRpcErrorResponse(
                    request.id,
                    JsonRpcError(JsonRpcError.Codes.InternalError, s"Call caused internal error", None)))
            }
          case None =>
            Future.successful(
              JsonRpcErrorResponse(
                request.id,
                new JsonRpcError(JsonRpcError.Codes.MethodNotFound, s"Method [${request.method}] not found", None)))
        }

      }

    }
  }
}
