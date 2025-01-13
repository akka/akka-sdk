/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.views;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@ComponentId("all-the-types-kve")
public class AllTheTypesKvEntity extends KeyValueEntity<AllTheTypesKvEntity.AllTheTypes> {

  public enum AnEnum {
    ONE, TWO, THREE
  }

  // common query parameter for views in this file
  public record ByEmail(String email) {
  }

  public record Recursive(Recursive recurse, String field) {}

  public record AllTheTypes(
      int intValue,
      long longValue,
      float floatValue,
      double doubleValue,
      boolean booleanValue,
      String stringValue,
      Integer wrappedInt,
      Long wrappedLong,
      Float wrappedFloat,
      Double wrappedDouble,
      Boolean wrappedBoolean,
      // time and date types
      Instant instant,
      ZonedDateTime zonedDateTime,
      // FIXME bytes does not work yet in runtime Byte[] bytes,
      Optional<String> optionalString,
      List<String> repeatedString,
      ByEmail nestedMessage,
      AnEnum anEnum,
      Recursive recursive
  ) {}



  public Effect<String> store(AllTheTypes value) {
    return effects().updateState(value).thenReply("OK");
  }
}
