/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl;

import akka.javasdk.annotations.Description;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

interface SomeToolInput {
  record SomeToolInput1(
      @Description("some description") String string,
      Optional<String> optionalString,
      boolean booleanPrimitive,
      int intPrimitive,
      long longPrimitive,
      double doublePrimitive,
      float floatPrimitive,
      byte bytePrimitive,
      Boolean boxedBoolean,
      Integer boxedInteger,
      Long boxedLong,
      Double boxedDouble,
      Float boxedFloat,
      Byte boxedByte,
      boolean[] primitiveBooleanArray,
      Boolean[] boxedBooleanArray) {}

  record SomeToolInput2(@Description("some strings") List<String> listOfStrings) {}

  record SomeToolInput3(@Description("a nested object") NestedObject nestedObject) {}

  record NestedObject(@Description("a value in the nested object") String someString) {}

  String someMethod(
      @Description("some tool input") SomeToolInput1 input1,
      SomeToolInput2 input2,
      SomeToolInput3 input3);

  record CommonStdlibTypes(Instant instant, Map<String, String> map, LocalDate localDate, LocalDateTime localDateTime, LocalTime localTime, ZonedDateTime zonedDateTime, Duration duration) {}

  record ClassWithRecursiveFields(String regular, ClassWithRecursiveFields recursive, NestedRecursiveClass nested) {}
  record NestedRecursiveClass(ClassWithRecursiveFields recursive) {}
}
