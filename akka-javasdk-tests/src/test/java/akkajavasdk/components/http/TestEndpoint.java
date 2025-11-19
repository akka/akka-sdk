/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.http;

import akka.NotUsed;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.Sanitizer;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import akka.stream.javadsl.Source;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@HttpEndpoint()
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class TestEndpoint extends AbstractHttpEndpoint {

  private final Sanitizer sanitizer;

  public TestEndpoint(Sanitizer sanitizer) {
    this.sanitizer = sanitizer;
  }

  private boolean constructedOnVt = Thread.currentThread().isVirtual();

  @Get("/query/{name}")
  public String getQueryParams(String name) {
    String a = requestContext().queryParams().getString("a").get();
    Integer b = requestContext().queryParams().getInteger("b").get();
    Long c = requestContext().queryParams().getLong("c").get();
    return "name: " + name + ", a: " + a + ", b: " + b + ", c: " + c;
  }

  public record SomeRecord(String text, int number) {}

  @Post("/list-body")
  public List<SomeRecord> postListBody(List<SomeRecord> records) {
    return records;
  }

  @Get("/on-virtual")
  public String getOnVirtual() {
    if (Thread.currentThread().isVirtual() && constructedOnVt) return "ok";
    else throw new RuntimeException("Endpoint not executing on virtual thread");
  }

  @Get("/sanitized")
  public String sanitized() {
    return sanitizer.sanitize("Here's a string to sanitize: sanitizesanitizesanitize");
  }

  public record BigDecimalRequest(BigDecimal value) {}

  @Post("/big-decimal")
  public BigDecimalRequest postBigDecimal(BigDecimalRequest request) {
    return request;
  }

  public record MyEvent(String id, String payload) {}

  record SseRequestPayload(String text) {}

  @Get("/serversentevents")
  public HttpResponse sse(Optional<SseRequestPayload> payload) {
    final Source<MyEvent, NotUsed> source;
    if (requestContext().lastSeenSseEventId().isPresent()) {
      var lastSeenId = Long.parseLong(requestContext().lastSeenSseEventId().get());
      source =
          Source.single(new MyEvent(Long.toString(lastSeenId + 1), "text")).concat(Source.maybe());
    } else {
      source =
          Source.from(Arrays.asList(new MyEvent("1", "text"), new MyEvent("2", "text")))
              .concat(Source.maybe());
    }

    return HttpResponses.serverSentEvents(source, MyEvent::id, event -> "sometype");
  }
}
