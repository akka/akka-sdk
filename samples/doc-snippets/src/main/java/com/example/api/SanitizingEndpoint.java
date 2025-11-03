package com.example.api;

import akka.javasdk.Sanitizer;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;

// tag::ad-hoc-sanitization[]
@HttpEndpoint("/example-with-ad-hoc-sanitization")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class SanitizingEndpoint {

  private final Sanitizer sanitizer;

  public SanitizingEndpoint(Sanitizer sanitizer) {
    this.sanitizer = sanitizer;
  }

  @Get("/somepath/{id}")
  public String returnSanitizedData(String id) {
    // String data from another component or a third party library/API
    String someText = loadText();
    String sanitizedText = sanitizer.sanitize(someText);
    return sanitizedText;
  }

  // end::ad-hoc-sanitization[]

  private String loadText() {
    return "";
  }
}
