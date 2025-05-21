/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl;

import akka.javasdk.annotations.mcp.McpToolParameterDescription;

import java.util.Optional;

public record SomeToolInput(@McpToolParameterDescription("some description")
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
                            ) {
}
