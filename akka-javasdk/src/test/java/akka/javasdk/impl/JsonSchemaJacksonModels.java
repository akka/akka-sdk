/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public class JsonSchemaJacksonModels {

  public record WithStaticField(String name) {
    public static final String CONSTANT = "constant";
  }

  public record WithJacksonAnnotations(
      @JsonProperty("full_name") String name, @JsonIgnore String secret, int age) {}

  @JsonIgnoreProperties({"internal"})
  public record WithIgnoredProperties(String name, String internal) {}

  public static class WithTransientField {
    public String name = "name";
    public transient String cached = "cached";
  }

  public static class BaseClass {
    public String baseField = "base";
  }

  public static class SubClass extends BaseClass {
    public String subField = "sub";
  }

  // a non-static inner class has a synthetic field referencing the outer instance
  public class InnerClass {
    public String name = "name";
  }
}
