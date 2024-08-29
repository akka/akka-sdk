/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.http;

import akka.actor.typed.ActorSystem;
import akka.annotation.InternalApi;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ContentType;
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
import akka.javasdk.JsonSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * FIXME separate in internal IMPL and public API
 */
public class HttpClient {

  private final Http http;
  private final String baseUrl;
  private final Materializer materializer;
  private final Duration timeout;
  private final List<HttpHeader> defaultHeaders;

  public HttpClient(ActorSystem<?> system, String baseUrl) {
    this.http = Http.get(system);
    this.materializer = SystemMaterializer.get(system).materializer();
    this.timeout = system.settings().config()
      .getDuration("akka.http.server.request-timeout")
      .plusSeconds(10); // 10s higher than configured timeout, so configured timeout always win
    this.baseUrl = baseUrl;
    this.defaultHeaders = new ArrayList<>();
  }

  private HttpClient(Http http, String baseUrl, Materializer materializer, Duration timeout, List<HttpHeader> defaultHeaders) {
    this.http = http;
    this.materializer = materializer;
    this.timeout = timeout;
    this.baseUrl = baseUrl;
    this.defaultHeaders = defaultHeaders;
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

  /**
   * INTERNAL API
   */
  @InternalApi
  public HttpClient withDefaultHeaders(List<HttpHeader> headers) {
    return new HttpClient(http, baseUrl, materializer, timeout, headers);
  }

  private RequestBuilder<ByteString> forMethod(String uri, HttpMethod method) {
    HttpRequest req = HttpRequest.create(baseUrl + uri).withMethod(method);
    return new RequestBuilder<>(http, materializer, timeout, req, StrictResponse::new).withHeaders(defaultHeaders);
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

    /**
     * Prepare for sending an HTTP request with an arbitrary payload encoded as described by the content type
     */
    public RequestBuilder<R> withRequestBody(ContentType type, byte[] bytes) {
      var requestWithBody = request.withEntity(type, bytes);
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
          if (res.status().isFailure()) {
            // FIXME should we have a better way to deal with failure?
            // FIXME what about error responses with a body, now we can't expect/parse those
            var errorString = "HTTP request for [" + request.getUri() + "] failed with HTTP status " + res.status();
            if (res.entity().getContentType().binary()) {
              throw new RuntimeException(errorString);
            } else {
              throw new RuntimeException(errorString + ": " + bytes.utf8String());
            }
          } else {
            if (res.entity().getContentType().equals(ContentTypes.APPLICATION_JSON)) {
              return new StrictResponse(res, JsonSupport.parseBytes(bytes.toArrayUnsafe(), type));
            } else if (!res.entity().getContentType().binary() && type == String.class) {
              return new StrictResponse(res, new String(bytes.toArrayUnsafe(), res.entity().getContentType().getCharsetOption().map(c -> c.nioCharset()).orElse(StandardCharsets.UTF_8)));
            } else {
              throw new RuntimeException("Expected to parse the response for " + request.getUri() + " to " + type + " but response content type is " + res.entity().getContentType());
            }
          }
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
