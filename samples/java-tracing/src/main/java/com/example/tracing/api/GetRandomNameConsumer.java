package com.example.tracing.api;

import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.consumer.Consumer;
import com.example.tracing.domain.UserEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import akka.platform.javasdk.JsonSupport;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

@ComponentId("get-random-name")
@Consume.FromEventSourcedEntity(value = UserEntity.class, ignoreUnknown = true)
public class GetRandomNameConsumer extends Consumer {

  private static final Logger log = LoggerFactory.getLogger(GetRandomNameConsumer.class);

  private final ComponentClient componentClient;

  public GetRandomNameConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect handleAdd(UserEvent.UserAdded userAdded) {
    if (messageContext().eventSubject().isPresent()) {
      var randomNameFut = getRandomNameAsync().thenCompose(name ->
        componentClient
          .forEventSourcedEntity(messageContext().eventSubject().get())
          .method(UserEntity::updateName)
          .invokeAsync(new UserEntity.UserCmd.UpdateNameCmd(name)));

      return effects().asyncProduce(randomNameFut);
    } else {
      return effects().ignore();
    }
  }

  // gets random name from external API using an asynchronous call and traces that call
  private CompletableFuture<String> getRandomNameAsync() {
    var otelCurrentContext = messageContext().metadata().traceContext().asOpenTelemetryContext();
    Span span = messageContext().getTracer()
      .spanBuilder("random-name-async")
      .setParent(otelCurrentContext)
      .setSpanKind(SpanKind.CLIENT)
      .startSpan()
      .setAttribute("user.id", messageContext().eventSubject().orElse("unknown"));

    var client = HttpClient.newHttpClient();
    var request = HttpRequest.newBuilder().uri(URI.create("https://randomuser.me/api/?inc=name&noinfo")).GET().build();
    var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());

    CompletableFuture<RandomUserApi.Name> result = response
      .handle((httpResponse, ex) -> {
        if (ex != null) {
          span.setStatus(StatusCode.ERROR, ex.getMessage());
          throw new RuntimeException(ex);
        } else {
          // FIXME messy stdlib http client + jackson
          try {
            RandomUserApi.Name name = JsonSupport.getObjectMapper().readerFor(RandomUserApi.Name.class).readValue(httpResponse.body());
            span.setAttribute("random.name", name.name());
            return name;
          } catch (Exception e) {
            throw new RuntimeException(e);
          } finally {
            span.end();
          }
        }
      });

    return result.thenApply(RandomUserApi.Name::name);
  }
}
