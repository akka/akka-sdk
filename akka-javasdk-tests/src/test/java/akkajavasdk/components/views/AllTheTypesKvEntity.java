/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.views;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Component(id = "all-the-types-kve")
public class AllTheTypesKvEntity extends KeyValueEntity<AllTheTypesKvEntity.AllTheTypes> {

  public enum AnEnum {
    ONE,
    TWO,
    THREE
  }

  // common query parameter for views in this file
  public record ByEmail(String email) {}

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
      // Note: not currently indexable
      AnEnum anEnum,
      Recursive recursive,
      BigDecimal bigDecimal) {}

  public Effect<String> store(AllTheTypes value) {
    return effects().updateState(value).thenReply("OK");
  }
}
