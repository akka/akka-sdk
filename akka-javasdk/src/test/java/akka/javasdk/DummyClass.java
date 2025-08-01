/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import akka.javasdk.annotations.Migration;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Objects;
import java.util.Optional;

@Migration(DummyClassMigration.class)
public class DummyClass {
  public String stringValue;
  public int intValue;
  public Optional<String> optionalStringValue;

  @JsonCreator
  public DummyClass(String stringValue, int intValue, Optional<String> optionalStringValue) {
    this.stringValue = stringValue;
    this.intValue = intValue;
    this.optionalStringValue = optionalStringValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DummyClass that = (DummyClass) o;
    return intValue == that.intValue
        && Objects.equals(stringValue, that.stringValue)
        && Objects.equals(optionalStringValue, that.optionalStringValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(stringValue, intValue, optionalStringValue);
  }
}
