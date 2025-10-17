/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.http;

import akka.javasdk.Sanitizer;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.AbstractHttpEndpoint;
import java.util.List;

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
}
