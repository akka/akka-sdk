package com.example.api;

import akka.javasdk.SanitizerClient;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;

// tag::ad-hoc-sanitization[]
@HttpEndpoint("/example-with-ad-hoc-sanitization")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class SanitizingEndpoint {

  private final SanitizerClient sanitizerClient;

  public SanitizingEndpoint(SanitizerClient sanitizerClient) {
    this.sanitizerClient = sanitizerClient;
  }

  @Get("/somepath/{id}")
  public String returnSanitizedData(String id) {
    // String data from another component or a third party library/API
    String someText = loadText();
    return sanitizerClient.sanitize("customer-ids", someText);
  }

  // end::ad-hoc-sanitization[]

  private String loadText() {
    return "";
  }
}
