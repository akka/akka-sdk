package com.example.sanitizer;

// tag::all[]
import akka.javasdk.SanitizerContext;
import akka.javasdk.TextSanitizer;

public class AccountSanitizer implements TextSanitizer {

  private final String prefix;

  public AccountSanitizer(SanitizerContext context) {
    this.prefix = context.config().getString("account-prefix");
  }

  @Override
  public String sanitize(String text) {
    StringBuilder masked = new StringBuilder();
    for (String word : text.split(" ", -1)) {
      if (!masked.isEmpty()) masked.append(" ");
      masked.append(word.startsWith(prefix) ? "*".repeat(word.length()) : word);
    }
    return masked.toString();
  }
}
// end::all[]
