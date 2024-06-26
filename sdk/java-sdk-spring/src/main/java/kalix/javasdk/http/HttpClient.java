/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.http;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpMethod;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.headers.HttpCredentials;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import akka.util.ByteString;
import com.fasterxml.jackson.core.JsonProcessingException;
import kalix.javasdk.JsonSupport;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

public class HttpClient {

  private final Http http;
  private final String baseUrl;
  private final Materializer materializer;
  private final Duration timeout;

  public HttpClient(ActorSystem system, String baseUrl) {
    this.http = Http.get(system);
    this.materializer = SystemMaterializer.get(system).materializer();
    this.timeout = system.settings().config()
      .getDuration("akka.http.server.request-timeout")
      .plusSeconds(10); // 10s higher than configured timeout, so configured timeout always win
    this.baseUrl = baseUrl;
  }

  public RequestBuilder<ByteString> GET(String uri) {
    return forMethod(uri, HttpMethods.GET);
  }

  public RequestBuilder<ByteString> POST(String uri) {
    return forMethod(uri, HttpMethods.POST);
  }

  public RequestBuilder<ByteString> PUT(String uri) {
    return forMethod(uri, HttpMethods.PUT);
  }

  public RequestBuilder<ByteString> PATCH(String uri) {
    return forMethod(uri, HttpMethods.PATCH);
  }

  public RequestBuilder<ByteString> DELETE(String uri) {
    return forMethod(uri, HttpMethods.DELETE);
  }

  private RequestBuilder<ByteString> forMethod(String uri, HttpMethod method) {
    HttpRequest req = HttpRequest.create(baseUrl + uri).withMethod(method);
    return new RequestBuilder<>(http, materializer, timeout, req, StrictResponse::new);
  }

  public record RequestBuilder<R>(Http http,
                                  Materializer materializer,
                                  Duration timeout,
                                  HttpRequest request,
                                  BiFunction<HttpResponse, ByteString, StrictResponse<R>> bodyParser) {

    private RequestBuilder<R> withRequest(HttpRequest request) {
      return new RequestBuilder<>(http, materializer, timeout, request, bodyParser);
    }

    public RequestBuilder<R> addHeader(String header, String value) {
      return addHeader(HttpHeader.parse(header, value));
    }

    public RequestBuilder<R> addHeader(HttpHeader header) {
      return withRequest(request.addHeader(header));
    }

    public RequestBuilder<R> withHeaders(Iterable<HttpHeader> headers) {
      return withRequest(request.withHeaders(headers));
    }

    public RequestBuilder<R> addCredentials(HttpCredentials credentials) {
      return withRequest(request.addCredentials(credentials));
    }

    public RequestBuilder<R> withTimeout(Duration timeout) {
      return new RequestBuilder<>(http, materializer, timeout, request, bodyParser);
    }

    /**
     * Transform the request before sending it.
     * This method allows for extra request configuration.
     */
    public RequestBuilder<R> modifyRequest(Function<HttpRequest, HttpRequest> adapter) {
      return withRequest(adapter.apply(request));
    }


    /**
     * Prepare for sending an HTTP request with an application/json body.
     * The passed Object is serialized to json using the application's default Jackson serializer.
     */
    public RequestBuilder<R> withRequestBody(Object object) {
      if (object == null) throw new IllegalArgumentException("object must not be null");
      try {
        byte[] body = JsonSupport.encodeToBytes(object).toByteArray();
        var requestWithBody = request.withEntity(ContentTypes.APPLICATION_JSON, body);
        return withRequest(requestWithBody);
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }

    /**
     * Prepare for sending an HTTP request with a text/plain body.
     */
    public RequestBuilder<R> withRequestBody(String text) {
      if (text == null) throw new IllegalArgumentException("text must not be null");
      var requestWithBody = request.withEntity(ContentTypes.TEXT_PLAIN_UTF8, text);
      return withRequest(requestWithBody);
    }

    /**
     * Prepare for sending an HTTP request with an application/octet-stream body.
     */
    public RequestBuilder<R> withRequestBody(byte[] bytes) {
      var requestWithBody = request.withEntity(ContentTypes.APPLICATION_OCTET_STREAM, bytes);
      return withRequest(requestWithBody);
    }

    public CompletionStage<StrictResponse<R>> invokeAsync() {
      return http.singleRequest(request)
        .thenCompose(response ->
          response.entity().toStrict(timeout.toMillis(), materializer)
            .thenApply(entity -> bodyParser.apply(response, entity.getData()))
        );
    }


    /**
     * Converts the response body to the specified type.
     * <p>
     * The response body payload is expected to be a JSON object and will be deserialized to the
     * specified type using the application's default Jackson deserializer.
     *
     * @param type the expected class type of the response body
     * @return a RequestBuilder configured to produce a StrictResponse with a deserialized response body of type T
     */
    public <T> RequestBuilder<T> responseBodyAs(Class<T> type) {
      return new RequestBuilder<>(http, materializer, timeout, request, (res, bytes) -> {
        try {
          return new StrictResponse<>(res, JsonSupport.parseBytes(bytes.toArrayUnsafe(), type));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    }

    /**
     * Converts the response body to the specified type using the provided parser function.
     * <p>
     *
     * @param parse the function to parse the response body
     * @return a RequestBuilder configured to produce a StrictResponse with a deserialized response body of type T
     */
    public <T> RequestBuilder<T> parseResponseBody(Function<byte[], T> parse) {
      return new RequestBuilder<>(http, materializer, timeout, request, (res, bytes) -> {
        return new StrictResponse<>(res, parse.apply(bytes.toArray()));
      });
    }

  }

}
