/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl;

import akka.javasdk.annotations.mcp.McpTool;
import akka.javasdk.annotations.mcp.McpToolParameterDescription;

import java.util.List;
import java.util.Optional;

interface SomeToolInput {
  record SomeToolInput1(@McpToolParameterDescription("some description")
                              String string,
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
                              Byte boxedByte
  ) { }

  record SomeToolInput2(
      @McpToolParameterDescription("some strings")
      List<String> listOfStrings) {}

  record SomeToolInput3(
      @McpToolParameterDescription("a nested object")
      NestedObject nestedObject) {}
  record NestedObject(
      @McpToolParameterDescription("a value in the nested object")
      String someString) {}
}