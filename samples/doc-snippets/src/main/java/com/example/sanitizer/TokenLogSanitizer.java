package com.example.sanitizer;

// tag::all[]
import akka.javasdk.LogSanitizer;
import akka.javasdk.SanitizerContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TokenLogSanitizer implements LogSanitizer {

  private final String key;
  private final Pattern keyValue;

  public TokenLogSanitizer(SanitizerContext context) {
    this.key = context.config().getString("key");
    this.keyValue = Pattern.compile(Pattern.quote(key) + "=(\\S+)");
  }

  @Override
  public String sanitize(String message) {
    // keeps the key and masks the value, for example token=****
    return keyValue
      .matcher(message)
      .replaceAll(m -> Matcher.quoteReplacement(key + "=" + "*".repeat(m.group(1).length())));
  }
}
// end::all[]
