package com.example.tracing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Typicode {

  private static final Logger log = LoggerFactory.getLogger(Typicode.class);

  public static final String url = "https://jsonplaceholder.typicode.com/posts";

  // using a third party HTTP client here rather than the built in `akka.javasdk.http.HttpClient`
  // in order to showcase external/manual tracing and propagating context manually
  private final HttpClient httpClient = HttpClient.newHttpClient();

  private final TextMapSetter<HttpRequest.Builder> setter = (carrier, key, value) ->
    carrier.setHeader(key, value);

  public record TypicodePost(String userId, String id, String title, String body) {}

  public CompletionStage<HttpResponse<TypicodePost>> callAsyncService(
    String postID,
    Optional<Span> parentSpan
  ) {
    var requestBuilder = HttpRequest.newBuilder().uri(URI.create(url + "/" + postID));

    parentSpan.ifPresent(span -> {
      // propagate trace parent to third party service
      var contextWithSpan = Context.current().with(span);
      W3CTraceContextPropagator.getInstance().inject(contextWithSpan, requestBuilder, setter);
    });

    HttpRequest httpRequest = requestBuilder.build();

    parentSpan.ifPresent(__ -> {
      log.info(
        "Request headers propagating open telemetry trace parent: {}",
        httpRequest.headers().toString()
      );
    });

    //Async call to external service
    return httpClient.sendAsync(httpRequest, new JsonResponseHandler<>(TypicodePost.class));
  }

  private static class JsonResponseHandler<T> implements HttpResponse.BodyHandler<T> {

    private final Class<T> responseType;

    public JsonResponseHandler(Class<T> responseType) {
      this.responseType = responseType;
    }

    @Override
    public HttpResponse.BodySubscriber<T> apply(HttpResponse.ResponseInfo responseInfo) {
      return HttpResponse.BodySubscribers.mapping(
        HttpResponse.BodySubscribers.ofString(Charsets.UTF_8),
        responseBody -> {
          try {
            return new ObjectMapper().readValue(responseBody, responseType);
          } catch (IOException e) {
            throw new RuntimeException("Failed to parse JSON");
          }
        }
      );
    }
  }
}
