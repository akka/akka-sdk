/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpResponse
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.ComponentOptions
import akka.runtime.sdk.spi.HttpEndpointConstructionContext
import akka.runtime.sdk.spi.HttpEndpointDescriptor
import akka.runtime.sdk.spi.HttpEndpointMethodDescriptor
import akka.runtime.sdk.spi.MethodOptions
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

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
  final case class JsonRpcRequest(jsonRpc: String, method: String, params: Option[AnyRef], id: Option[Any]) {
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
  final class JsonRpcEndpoint(path: String, methods: Map[String, JsonRpcRequest => Future[JsonRpcResponse]])(implicit
      ec: ExecutionContext) {

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

    private val mapper = JsonSerializer.internalObjectMapper.copy
    mapper.registerModule(DefaultScalaModule)

    class HttpRequestHandler(constructionContext: HttpEndpointConstructionContext) {

      def handle(entity: HttpEntity.Strict): Future[HttpResponse] = {
        // FIXME validate content type
        val jsonPayload = entity.data.utf8String
        // FIXME decouple from HTTP?
        // can either be a batch (in a JSON array) or a single request
        if (jsonPayload.startsWith("[")) {
          val requests =
            mapper
              .readerForListOf(classOf[JsonRpcRequest])
              .readValue[java.util.List[JsonRpcRequest]](jsonPayload)
              .asScala
              .toVector
          Future.successful(
            HttpResponse(entity = HttpEntity(
              ContentTypes.`application/json`,
              Source(requests)
                .mapAsyncUnordered(8)(handleRequest) // FIXME parallelism
                .map(responseToJson))))
        } else {
          val request = mapper.readValue(jsonPayload, classOf[JsonRpcRequest])
          handleRequest(request).map(jsonRpcResponse =>
            HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, responseToJson(jsonRpcResponse))))
        }
      }

      private def responseToJson(jsonRpcResponse: JsonRpcResponse) =
        ByteString.fromArrayUnsafe(mapper.writeValueAsBytes(jsonRpcResponse))

      def handleRequest(request: JsonRpcRequest): Future[JsonRpcResponse] = {
        methods.get(request.method) match {
          case Some(handler) => handler(request)
          case None =>
            Future.successful(
              JsonRpcErrorResponse(
                request.id,
                new JsonRpcError(JsonRpcError.Codes.MethodNotFound, "Method not found", None)))
        }

      }

    }
  }
}
