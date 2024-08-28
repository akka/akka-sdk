package com.example.tracing.api;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.consumer.Consumer;
import com.example.tracing.domain.UserEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import akka.javasdk.JsonSupport;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@ComponentId("get-random-photo")
@Consume.FromEventSourcedEntity(value = UserEntity.class, ignoreUnknown = true)
public class GetRandomPhotoConsumer extends Consumer {
  private static final Logger log = LoggerFactory.getLogger(GetRandomPhotoConsumer.class);

  private final ComponentClient componentClient;

  public GetRandomPhotoConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public CompletionStage<String> handleAdd(UserEvent.UserAdded userAdded) {
    // FIXME not sure what we should show here, I guess cross-Kalix-service tracing?
    throw new UnsupportedOperationException("FIXME");
    /*
    var photoReply = getRandomPhotoAsync();
    var updatePhoto = photoReply.thenCompose(randomPhotoUrl -> {
      // Example for manually injecting headers for tracing context propagation
      // NOTE: tracing context is automatically propagated when using component client, no need to manually inject headers
      // in this case we are using WebClient directly instead, only for demonstration purposes but if you're doing a local call
      // you should use the component client instead
      var tracingMap = Map.of(
        "traceparent", actionContext().metadata().traceContext().traceParent().orElse(""),
        "tracestate", actionContext().metadata().traceContext().traceState().orElse("")
      );

      var metadata = Metadata.EMPTY;
      tracingMap.forEach((key, value) -> metadata = metadata.add(key, value));

      return componentClient.forEventSourcedEntity(actionContext().eventSubject().get())
              .method(UserEntity::updatePhoto)
              .withMetadata()
              .invokeAsync(new UserEntity.UserCmd.UpdatePhotoCmd(randomPhotoUrl))
        .post()
        .uri(uriBuilder -> uriBuilder
          .path("/akka/v1.0/entity/user/{userId}/updatePhoto")
          .build(actionContext().eventSubject().get()))
        .bodyValue(n)
        .headers(h -> tracingMap.forEach(h::set))
        .retrieve()
        .bodyToMono(String.class)
        .toFuture();

    });

    return updatePhoto;
   */
  }

  // gets random name from external API using an asynchronous call and traces that call
  private CompletableFuture<String> getRandomPhotoAsync() {
    var otelCurrentContext = messageContext().metadata().traceContext().asOpenTelemetryContext();
    Span span = messageContext().getTracer()
      .spanBuilder("random-photo-async")
      .setParent(otelCurrentContext)
      .setSpanKind(SpanKind.CLIENT)
      .startSpan()
      .setAttribute("user.id", messageContext().eventSubject().orElse("unknown"));

    var httpClient = HttpClient.newHttpClient();
    var request = HttpRequest.newBuilder()
            .uri(URI.create("https://randomuser.me/api/?inc=picture&noinfo"))
            .GET()
            .build();

    return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .handleAsync((httpResponse, error) -> {
              if (error == null) {
                try {
                  RandomUserApi.Photo photoResult = JsonSupport.getObjectMapper().readerFor(RandomUserApi.Photo.class).readValue(httpResponse.body());
                  span.setAttribute("random.photo", photoResult.url());
                  span.end();
                  return photoResult.url();
                } catch (IOException e) {
                  span.setStatus(StatusCode.ERROR, "Failed to fetch name: " + error.getMessage());
                  span.end();
                  throw new RuntimeException(e);
                }
              } else {
                span.setStatus(StatusCode.ERROR, "Failed to fetch name: " + error.getMessage());
                span.end();
                throw new RuntimeException(error);
              }
      });
  }

}
