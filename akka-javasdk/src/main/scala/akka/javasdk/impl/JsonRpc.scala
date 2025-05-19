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
import akka.runtime.sdk.spi.ACL
import akka.runtime.sdk.spi.All
import akka.runtime.sdk.spi.ComponentOptions
import akka.runtime.sdk.spi.HttpEndpointConstructionContext
import akka.runtime.sdk.spi.HttpEndpointDescriptor
import akka.runtime.sdk.spi.HttpEndpointMethodDescriptor
import akka.runtime.sdk.spi.MethodOptions
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.slf4j.LoggerFactory

import java.util.concurrent.CompletionStage
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters.FutureOps
import scala.util.control.NoStackTrace
import scala.util.control.NonFatal

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object JsonRpc {

  private val log = LoggerFactory.getLogger(classOf[JsonRpc.type])

  /*
   * Implementation of JSON-RPC 2.0 according to https://www.jsonrpc.org/specification
   *
   */

  // protocol messages

  sealed trait Identifier
  final case class TextId(id: String) extends Identifier
  final case class NumberId(id: Long) extends Identifier

  sealed trait Params
  final case class ByName(map: Map[String, Any]) extends Params
  final case class ByPosition(entries: Seq[Any]) extends Params

  /**
   * @param method
   *   The name of the method
   * @param params
   *   Either a Map with properties by name or a List of parameters.
   * @param requestId
   *   Request identifier. Must be present for requests with responses
   */
  final case class JsonRpcRequest(method: String, params: Option[Params], requestId: Option[Identifier]) {
    // if a request is a notification, there must be no response for it
    def isNotification = requestId.isEmpty
  }

  sealed trait JsonRpcResponse {}
  final case class JsonRpcSuccessResponse(id: Identifier, result: Option[Any]) extends JsonRpcResponse
  final case class JsonRpcErrorResponse(error: JsonRpcError) extends JsonRpcResponse

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

  final case class JsonRpcError(
      code: Int,
      message: String,
      data: Option[Any] = None,
      requestId: Option[Identifier] = None)
      extends RuntimeException
      with NoStackTrace

  object Serialization {
    private[akka] val mapper: ObjectMapper = {
      val m = JsonSerializer.newObjectMapperWithDefaults()
      m.registerModule(DefaultScalaModule)
      val protocolModule = new SimpleModule()
      protocolModule.addDeserializer(classOf[JsonRpcRequest], RequestDeserializer)
      protocolModule.addSerializer(classOf[JsonRpcRequest], RequestSerializer)
      protocolModule.addDeserializer(classOf[JsonRpcResponse], JsonRpcResponseDeserializer)
      protocolModule.addDeserializer(classOf[JsonRpcSuccessResponse], SuccessResponseDeserializer)
      protocolModule.addDeserializer(classOf[JsonRpcErrorResponse], ErrorResponseDeserializer)
      protocolModule.addSerializer(classOf[JsonRpcSuccessResponse], SuccessResponseSerializer)
      protocolModule.addSerializer(classOf[JsonRpcErrorResponse], ErrorResponseSerializer)
      m.registerModule(protocolModule)
      m
    }

    private def writeIdField(id: Identifier, gen: JsonGenerator): Unit = id match {
      case NumberId(id) =>
        gen.writeNumberField("id", id)
      case TextId(id) =>
        gen.writeStringField("id", id)
    }

    private def nodeToMap(node: JsonNode): Map[String, Any] = {
      node.properties().asScala.map(entry => entry.getKey -> valueOf(entry.getValue)).toMap
    }

    private def valueOf(node: JsonNode): Any = node.getNodeType match {
      case JsonNodeType.BOOLEAN => node.booleanValue()
      case JsonNodeType.STRING  => node.textValue()
      case JsonNodeType.NUMBER  => node.numberValue()
      case JsonNodeType.OBJECT  => nodeToMap(node)
      case JsonNodeType.NULL    => null
      case JsonNodeType.POJO    => node.properties().asScala.map(e => e.getKey -> valueOf(e.getValue)).toMap
      case JsonNodeType.ARRAY   => node.elements().asScala.map(e => valueOf(e)).toVector
      case JsonNodeType.BINARY  => node.binaryValue()
      case JsonNodeType.MISSING => null
    }

    private def nodeToId(node: JsonNode): Identifier = node match {
      case nonNull if nonNull.isTextual => TextId(nonNull.asText())
      case nonNull if nonNull.isNumber  => NumberId(nonNull.asLong())
      case other =>
        throw new JsonRpcError(
          JsonRpcError.Codes.InvalidRequest,
          s"Unexpected JSON-RPC [id] field content type [${other.getNodeType.name()}] should be text or non-fraction number")
    }

    // Note: handwritten ser/deser for performance, security, adhering to protocol spec (and also greater freedom in API design)
    private object RequestSerializer extends com.fasterxml.jackson.databind.JsonSerializer[JsonRpcRequest] {

      override def serialize(value: JsonRpcRequest, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
        gen.writeStartObject()
        gen.writeStringField("jsonrpc", "2.0")
        gen.writeStringField("method", value.method)
        value.params match {
          case None =>
          case Some(ByName(map)) =>
            gen.writeObjectFieldStart("params")
            map.foreach { case (key, value) =>
              gen.writeObjectField(key, value)
            }
            gen.writeEndObject()
          case Some(ByPosition(entries)) =>
            gen.writeArrayFieldStart("params")
            entries.foreach(value => gen.getCodec.writeValue(gen, value))
            gen.writeEndArray()
        }
        value.requestId match {
          case Some(id) => writeIdField(id, gen)
          case None     => // notification
        }

        gen.writeEndObject()
      }
    }

    private object RequestDeserializer extends JsonDeserializer[JsonRpcRequest] {

      override def deserialize(p: JsonParser, ctxt: DeserializationContext): JsonRpcRequest = {
        val node = p.readValueAsTree[JsonNode]()
        val id = node.get("id") match {
          case null    => None // notification
          case nonNull => Some(nodeToId(nonNull)) // regular request
        }
        val method = node.get("method") match {
          case null                         => throw new JsonMappingException(p, "JSON-RPC [method] missing")
          case nonNull if nonNull.isTextual => nonNull.asText()
          case strange =>
            throw new JsonRpcError(
              JsonRpcError.Codes.InvalidRequest,
              s"JSON-RPC [method] field has wrong type [${strange.getNodeType.name()}] should be [${JsonNodeType.STRING.name()}]",
              requestId = id)
        }
        val params = node.get("params") match {
          case null => None
          case array if array.isArray =>
            Some(ByPosition(array.elements().asScala.map(e => valueOf(e)).toVector))
          case obj if obj.isObject =>
            Some(ByName(nodeToMap(obj)))
          case _ =>
            throw new JsonRpcError(
              JsonRpcError.Codes.InvalidRequest, // surprisingly not InvalidParams but from spec
              s"Invalid Request, [params] field must be object or array",
              requestId = id)
        }
        JsonRpcRequest(method, params, id)
      }
    }

    private object SuccessResponseSerializer
        extends com.fasterxml.jackson.databind.JsonSerializer[JsonRpcSuccessResponse] {

      override def serialize(
          value: JsonRpcSuccessResponse,
          gen: JsonGenerator,
          serializers: SerializerProvider): Unit = {
        gen.writeStartObject()
        gen.writeStringField("jsonrpc", "2.0")
        writeIdField(value.id, gen)
        value.result match {
          case Some(value) =>
            gen.writeFieldName("result")
            gen.getCodec.writeValue(gen, value)
          case None =>
            gen.writeNullField("result") // result must always be present in successful responses
        }
        gen.writeEndObject()
      }
    }

    private object ErrorResponseSerializer extends com.fasterxml.jackson.databind.JsonSerializer[JsonRpcErrorResponse] {

      override def serialize(value: JsonRpcErrorResponse, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
        gen.writeStartObject()
        gen.writeStringField("jsonrpc", "2.0")
        val JsonRpcErrorResponse(JsonRpcError(code, message, data, requestId)) = value
        requestId match {
          case Some(id) => writeIdField(id, gen)
          case None     =>
            // Spec: If there was an error in detecting the id in the Request object (e.g. Parse error/Invalid Request), it MUST be Null.
            gen.writeNullField("id")
        }
        // The rest is in a nested error object
        gen.writeFieldName("error")
        gen.writeStartObject()
        gen.writeNumberField("code", code)
        gen.writeStringField("message", message)
        data match {
          case Some(value) =>
            gen.writeFieldName("data")
            gen.getCodec.writeValue(gen, value)
          case None => // omitted
        }
        gen.writeEndObject()
        gen.writeEndObject()
      }
    }

    private object JsonRpcResponseDeserializer extends JsonDeserializer[JsonRpcResponse] {

      override def deserialize(p: JsonParser, ctxt: DeserializationContext): JsonRpcResponse = {

        val node = p.readValueAsTree[JsonNode]()
        if (node.has("result")) {
          p.getCodec.treeToValue(node, classOf[JsonRpcSuccessResponse])
        } else if (node.has("error")) {
          p.getCodec.treeToValue(node, classOf[JsonRpcErrorResponse])
        } else {
          throw new JsonMappingException(
            p,
            s"Json payload is not a valid JSON-RPC response (missing both result and error fields) '${node.toPrettyString}'")
        }
      }

    }

    private object SuccessResponseDeserializer extends JsonDeserializer[JsonRpcSuccessResponse] {

      override def deserialize(p: JsonParser, ctxt: DeserializationContext): JsonRpcSuccessResponse = {
        val node = p.readValueAsTree[JsonNode]()
        if (node.has("result")) {
          val id = node.get("id") match {
            case null =>
              throw new JsonRpcError(JsonRpcError.Codes.InvalidRequest, "Invalid JSON-RPC response, missing [id] field")
            case nonNull => nodeToId(nonNull)
          }
          val result = Option(valueOf(node.get("result")))
          JsonRpcSuccessResponse(id, result)
        } else {
          throw new JsonMappingException(
            p,
            "Json payload is not a valid JSON-RPC success response (missing result field)")
        }
      }
    }

    private object ErrorResponseDeserializer extends JsonDeserializer[JsonRpcErrorResponse] {

      override def deserialize(p: JsonParser, ctxt: DeserializationContext): JsonRpcErrorResponse = {
        val node = p.readValueAsTree[JsonNode]()
        if (node.has("error")) {
          val id = node.get("id") match {
            case null    => None
            case nonNull => Option(nodeToId(nonNull))
          }
          // error
          val errorNode = node.get("error")
          val code = errorNode.get("code").asInt()
          val message = errorNode.get("message").asText()
          val data = errorNode.get("data") match {
            case null    => None
            case nonNull => Option(valueOf(nonNull))
          }
          JsonRpcErrorResponse(JsonRpcError(code, message, data, id))
        } else {
          throw new JsonMappingException(p, "Json payload is not a valid JSON-RPC error response (missing error field)")
        }
      }
    }

    def requestToJsonString(request: JsonRpcRequest): String =
      mapper.writeValueAsString(request)

    def parseRequest(bytes: ByteString): Seq[JsonRpcRequest] = {
      val jsonText = bytes.utf8String
      try {
        log.trace("Parsing JSON-RPC request: {}", jsonText)
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
      } catch {
        case NonFatal(ex) =>
          log.debug("Failed to parse JSON-RPC request: {}", jsonText, ex)
          throw ex
      }
    }

    def responseToJsonBytes(jsonRpcResponse: JsonRpcResponse): ByteString = {
      val bytes = ByteString.fromArrayUnsafe(mapper.writeValueAsBytes(jsonRpcResponse))
      if (log.isTraceEnabled) log.trace("Rendered JSON-RPC response: {}", bytes.utf8String)
      bytes
    }

    def parseResponse(response: String): JsonRpcResponse = {
      mapper.readValue(response, classOf[JsonRpcResponse])
    }

  }

  final class JsonRpcEndpoint(path: String, methods: Map[String, JsonRpcRequest => Future[Option[JsonRpcResponse]]])(
      implicit ec: ExecutionContext) {

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
      componentOptions =
        new ComponentOptions(Some(new ACL(Seq(All), Seq.empty, None, None)), None), // TODO more limiting ACL?
      implementationClassName = s"json-rpc-2-$path",
      objectMapper = None // we deserialize ourselves
    )

    class HttpRequestHandler(constructionContext: HttpEndpointConstructionContext) {

      def handle(entity: HttpEntity.Strict): CompletionStage[HttpResponse] = {
        // FIXME validate content type
        val future: Future[HttpResponse] = {
          if (entity.contentType != ContentTypes.`application/json`)
            Future.successful(HttpResponse(StatusCodes.UnsupportedMediaType))
          else {
            // FIXME handle SSE as well (what does it really mean, batch responses are already streamed?)
            // Note: HTTP transport for JSON-RPC isn't really well documented/spec:ed, looking at MCP and seeing what they dictate
            try {
              Serialization.parseRequest(entity.data) match {
                case Seq(single) =>
                  log.debug("Handling JSON-RPC request for [{}]", single.method)
                  handleRequest(single).map(maybeResponse =>
                    HttpResponse(entity = maybeResponse match {
                      case Some(response) =>
                        HttpEntity(ContentTypes.`application/json`, Serialization.responseToJsonBytes(response))
                      case None =>
                        HttpEntity.empty(ContentTypes.`application/json`) // FIXME still json even though empty?
                    }))
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
                      Source
                        .single(ByteString("["))
                        .concat(Source(batch)
                          .mapAsyncUnordered(8)(handleRequest) // FIXME parallelism
                          .mapConcat(_.map(response => Serialization.responseToJsonBytes(response))))
                        .concat(Source.single(ByteString("]"))))))
              }
            } catch {
              case jsonRpcException: JsonRpcError =>
                Future.successful(errorToHttpResponse(jsonRpcException))
              case ex: JsonMappingException =>
                if (log.isDebugEnabled())
                  log.debug("JSON-RPC server request payload JSON Mapping exception: {}", ex.getMessage)
                Future.successful(errorToHttpResponse(JsonRpcError(JsonRpcError.Codes.ParseError, "Parse error", None)))
            }
          }
        }

        future.asJava // runtime HTTP endpoints only support Java completion stages
      }

      private def errorToHttpResponse(error: JsonRpcError, requestId: Option[Identifier] = None) =
        HttpResponse(entity =
          HttpEntity(ContentTypes.`application/json`, Serialization.responseToJsonBytes(JsonRpcErrorResponse(error))))

      def handleRequest(request: JsonRpcRequest): Future[Option[JsonRpcResponse]] = {
        if (log.isTraceEnabled)
          log.trace("JSON-RPC request: {}", request)
        methods.get(request.method) match {
          case Some(handler) =>
            try {
              // Note: we don't validate that there is no response for notification here
              val result = handler(request)
              log.trace("JSON-RPC result: {}", result)
              result
            } catch {
              case error: JsonRpcError =>
                if (!request.isNotification) Future.successful(Some(JsonRpcErrorResponse(error)))
                else {
                  log.warn(
                    s"JSON-RPC notification to endpoint $path method ${request.method} id ${request.requestId} failed",
                    error)
                  Future.successful(None)
                }
              case NonFatal(ex) =>
                log.warn(s"JSON-RPC call to endpoint $path method ${request.method} id ${request.requestId} failed", ex)
                if (request.isNotification) Future.successful(None)
                else
                  Future.successful(
                    Some(
                      JsonRpcErrorResponse(error = JsonRpcError(
                        JsonRpcError.Codes.InternalError,
                        s"Call caused internal error",
                        None,
                        requestId = request.requestId))))
            }
          case None =>
            if (request.isNotification) {
              log.debug("Ignoring unhandled JSON-RPC notification for method [{}]", request.method)
              Future.successful(None)
            } else if (log.isTraceEnabled)
              log.trace("JSON-RPC method not found: [{}]", request.method)
            Future.successful(
              Some(
                JsonRpcErrorResponse(error = new JsonRpcError(
                  JsonRpcError.Codes.MethodNotFound,
                  s"Method [${request.method}] not found",
                  None,
                  requestId = request.requestId))))
        }

      }

    }
  }
}
