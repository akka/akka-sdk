/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl;

import akka.javasdk.annotations.Description;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

// Models with Jackson annotations and class shapes that affect the mapped properties.
class JsonSchemaJacksonModels {

  public record WithStaticField(String name) {
    public static final String CONSTANT = "constant";
  }

  public record WithJacksonAnnotations(
      @JsonProperty("full_name") String name, @JsonIgnore String secret, int age) {}

  @JsonIgnoreProperties({"internal"})
  public record WithIgnoredProperties(String name, String internal) {}

  public static class WithTransientField {
    public String name;
    public transient String cached;

    public WithTransientField() {}

    public WithTransientField(String name, String cached) {
      this.name = name;
      this.cached = cached;
    }
  }

  public static class TransientWithProperty {
    public String name;
    @JsonProperty public transient String cached;

    public TransientWithProperty() {}

    public TransientWithProperty(String name, String cached) {
      this.name = name;
      this.cached = cached;
    }
  }

  public static class BaseClass {
    public String baseField;
  }

  public static class SubClass extends BaseClass {
    public String subField;

    public SubClass() {}

    public SubClass(String baseField, String subField) {
      this.baseField = baseField;
      this.subField = subField;
    }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record WithNamingStrategy(String firstName, @JsonProperty("explicit") String lastName) {}

  static class PrefixedNaming extends PropertyNamingStrategies.NamingBase {
    @Override
    public String translate(String propertyName) {
      return "x_" + propertyName;
    }
  }

  @JsonNaming(PrefixedNaming.class)
  public record WithCustomNaming(String firstName, int age) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  interface SnakeNamed {}

  public record ViaInterfaceNaming(String firstName) implements SnakeNamed {}

  @JsonIgnoreProperties({"internal"})
  interface IgnoresInternal {}

  public record ViaInterfaceIgnore(String name, String internal) implements IgnoresInternal {}

  @JsonIgnoreProperties({"a"})
  public static class IgnoreBase {
    public String a;
    public String b;
  }

  // the nearest @JsonIgnoreProperties applies, the superclass one does not
  @JsonIgnoreProperties({"b"})
  public static class IgnoreSub extends IgnoreBase {
    public String c;

    public IgnoreSub() {}

    public IgnoreSub(String a, String b, String c) {
      this.a = a;
      this.b = b;
      this.c = c;
    }
  }

  public static class GetterRenamed {
    private String name;

    public GetterRenamed() {}

    public GetterRenamed(String name) {
      this.name = name;
    }

    @JsonProperty("full_name")
    public String getName() {
      return name;
    }
  }

  public static class CreatorRenamed {
    private final String name;

    @JsonCreator
    public CreatorRenamed(@JsonProperty("full_name") String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  public static class GetterIgnored {
    public String name;
    private String secret;

    public GetterIgnored() {}

    public GetterIgnored(String name, String secret) {
      this.name = name;
      this.secret = secret;
    }

    @JsonIgnore
    public String getSecret() {
      return secret;
    }
  }

  public record AccessorIgnored(String a, String b) {
    @JsonIgnore
    @Override
    public String b() {
      return b;
    }
  }

  public static class ShadowBase {
    private int name = 1;
  }

  // the subclass field wins over the shadowed superclass field
  public static class ShadowSub extends ShadowBase {
    private String name;

    public ShadowSub() {}

    public ShadowSub(String name) {
      this.name = name;
    }
  }

  public static class ShadowRenamedBase {
    @JsonProperty("a")
    public String x = "base";
  }

  // the subclass field replaces the superclass field with the same Java name, including its rename
  public static class ShadowRenamedSub extends ShadowRenamedBase {
    public String x;

    public ShadowRenamedSub() {}

    public ShadowRenamedSub(String x) {
      this.x = x;
    }
  }

  public static class DescribedPojo {
    @Description("the name")
    private String name;

    public DescribedPojo() {}

    public DescribedPojo(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  // a non-static inner class has a synthetic field referencing the outer instance
  public class InnerClass {
    public String name = "name";
  }
}
