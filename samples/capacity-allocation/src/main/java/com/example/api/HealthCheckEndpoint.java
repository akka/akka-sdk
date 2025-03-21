package com.example.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.HttpResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/")
public class HealthCheckEndpoint {

  private static final Logger logger = LoggerFactory.getLogger(HealthCheckEndpoint.class);

  public record HealthStatus(String status, String message) {
    public static HealthStatus up() {
      return new HealthStatus("UP", "Service is running");
    }
  }

  @Get("/health")
  public HttpResponse healthCheck() {
    logger.debug("Health check requested");
    return HttpResponses.ok(HealthStatus.up());
  }
}
