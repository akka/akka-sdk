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
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
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
   *   the name of the method
   * @param params
   *   Either a Map with properties by name, or a List of parameters.
   * @param id
   *   request identifier, must be present for requests with responses
   */
  final case class JsonRpcRequest(method: String, params: Option[Params], id: Option[Identifier]) {
    // if a request is a notification there is no response for it
    def isNotification = id.isEmpty
  }

  sealed trait JsonRpcResponse {
    def id: Option[Any]
  }

  final case class JsonRpcSuccessResponse(
      id: Option[Any],
      @JsonInclude(JsonInclude.Include.ALWAYS)
      result: Any)
      extends JsonRpcResponse
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

  final case class JsonRpcError(code: Int, message: String, data: Option[Any] = None)

  object Serialization {
    private[akka] val mapper: ObjectMapper = {
      val m = JsonSerializer.newObjectMapperWithDefaults()
      m.registerModule(DefaultScalaModule)
      m.setSerializationInclusion(Include.NON_EMPTY)
      val module = new SimpleModule()
      module.addDeserializer(classOf[JsonRpcRequest], new RequestDeserializer)
      module.addSerializer(classOf[JsonRpcRequest], new RequestSerializer)
      m.registerModule(module)
      m
    }

    private final class RequestDeserializer extends JsonDeserializer[JsonRpcRequest] {

      override def deserialize(p: JsonParser, ctxt: DeserializationContext): JsonRpcRequest = {
        // FIXME some specific errors in the format should lead to specific error codes
        val node = p.readValueAsTree[JsonNode]()
        val method = node.get("method") match {
          case null                         => throw new JsonMappingException(p, "JSON-RPC [method] missing")
          case nonNull if nonNull.isTextual => nonNull.asText()
          case _ => throw new JsonMappingException(p, "JSON-RPC [method] field has wrong type")
        }
        val id = node.get("id") match {
          case null => None
          case nonNull if nonNull.isTextual =>
            Some(TextId(nonNull.asText()))
          case nonNull if nonNull.isNumber =>
            Some(NumberId(nonNull.asLong()))
          case other =>
            throw new JsonMappingException(
              p,
              s"Unexpected JSON-RPC [id] field content type [${other.getNodeType}] should be text or non-fraction number")
        }
        val params = node.get("params") match {
          case null                   => None
          case array if array.isArray =>
            // FIXME actual parsed fields
            Some(ByPosition(array.elements().asScala.map(e => primitiveValue(e)).toVector))
          case obj if obj.isObject =>
            Some(ByName(obj.fields().asScala.map(entry => entry.getKey -> primitiveValue(entry.getValue)).toMap))
          case unknown =>
            throw new JsonMappingException(
              p,
              s"Unexpected JSON-RPC [params] field content type [${unknown.getNodeType}] should be text or non-fraction number")
        }
        JsonRpcRequest(method, params, id)
      }

      private def primitiveValue(node: JsonNode): Any = node.getNodeType match {
        case JsonNodeType.BOOLEAN => node.booleanValue()
        case JsonNodeType.STRING  => node.textValue()
        case JsonNodeType.NUMBER  => node.numberValue()
        case JsonNodeType.OBJECT  => node.fields().asScala.map(e => e.getKey -> primitiveValue(e.getValue)).toMap
        case JsonNodeType.NULL    => null
        case JsonNodeType.POJO    => ???
        case JsonNodeType.ARRAY   => ???
        case JsonNodeType.BINARY  => ???
        case JsonNodeType.MISSING => ???

      }

    }

    private final class RequestSerializer extends com.fasterxml.jackson.databind.JsonSerializer[JsonRpcRequest] {

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
        value.id match {
          case None =>
          case Some(NumberId(id)) =>
            gen.writeNumberField("id", id)
          case Some(TextId(id)) =>
            gen.writeStringField("id", id)
        }
        gen.writeEndObject()
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
      if (response.contains("error")) {
        mapper.readValue(response, classOf[JsonRpcErrorResponse])
      } else {
        mapper.readValue(response, classOf[JsonRpcSuccessResponse])
      }
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
              case _: JsonMappingException =>
                Future.successful(
                  HttpResponse(entity = HttpEntity(
                    ContentTypes.`application/json`,
                    Serialization.responseToJsonBytes(
                      JsonRpcErrorResponse(
                        id = None,
                        error = JsonRpcError(JsonRpcError.Codes.InvalidRequest, "Parse error", None))))))
            }
          }
        }

        future.asJava // runtime HTTP endpoints only support Java completion stages
      }

      def handleRequest(request: JsonRpcRequest): Future[Option[JsonRpcResponse]] = {
        methods.get(request.method) match {
          case Some(handler) =>
            try {
              // Note: we don't validate that there is no response for notification here
              handler(request)
            } catch {
              case NonFatal(ex) =>
                log.warn(s"JSON-RPC call to endpoint $path method ${request.method} id ${request.id} failed", ex)
                if (request.isNotification) Future.successful(None)
                else
                  Future.successful(
                    Some(
                      JsonRpcErrorResponse(
                        id = request.id,
                        error = JsonRpcError(JsonRpcError.Codes.InternalError, s"Call caused internal error", None))))
            }
          case None =>
            if (request.isNotification) {
              log.debug("Ignoring unhandled JSON-RPC notification for method [{}]", request.method)
              Future.successful(None)
            } else
              Future.successful(
                Some(
                  JsonRpcErrorResponse(
                    id = request.id,
                    error = new JsonRpcError(
                      JsonRpcError.Codes.MethodNotFound,
                      s"Method [${request.method}] not found",
                      None))))
        }

      }

    }
  }
}
