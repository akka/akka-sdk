/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.http;

import akka.javasdk.CallerSpiffeContext;
import akka.javasdk.SpiffeContext;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpClientProvider;
import com.typesafe.config.Config;

@HttpEndpoint
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class SpiffeEndpoint extends AbstractHttpEndpoint {

  public record CallerInfo(String caller) {}

  private final HttpClientProvider httpClientProvider;
  private final int httpPort;

  public SpiffeEndpoint(HttpClientProvider httpClientProvider, Config config) {
    this.httpClientProvider = httpClientProvider;
    this.httpPort = config.getInt("akka.javasdk.testkit.http-port");
  }

  // reports the caller SPIFFE id observed on this request (empty if none)
  @Get("/spiffe/caller")
  public CallerInfo caller() {
    var caller =
        requestContext()
            .getSpiffeContext()
            .flatMap(SpiffeContext::getCallerContext)
            .map(CallerSpiffeContext::getSpiffeId)
            .orElse("");
    return new CallerInfo(caller);
  }

  // makes an outbound HTTP call to /spiffe/caller and returns the caller it observed; "service"
  // targets the Akka service by name (cross-service), "external" targets an http:// URL back to
  // self
  @Get("/spiffe/delegate/{kind}")
  public CallerInfo delegate(String kind) {
    var target =
        switch (kind) {
          case "service" -> "sdk-tests";
          case "external" -> "http://localhost:" + httpPort;
          default -> throw new IllegalArgumentException("Unknown kind [" + kind + "]");
        };
    return httpClientProvider
        .httpClientFor(target)
        .GET("/spiffe/caller")
        .responseBodyAs(CallerInfo.class)
        .invoke()
        .body();
  }
}
