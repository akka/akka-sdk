/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import org.scalatest.wordspec.AnyWordSpec

class HttpEndpointValidationSpec extends AnyWordSpec with CompilationTestSupport {

  "HttpEndpointValidationProcessor" should {

    "accept valid WebSocket endpoints with String, ByteString, and Message types" in {
      val result = compileTestSource("valid/ValidWebSocketEndpoint.java")
      assertCompilationSuccess(result)
    }

    "reject WebSocket method with wrong return type" in {
      val result = compileTestSource("invalid/InvalidWebSocketWrongReturnType.java")
      assertCompilationFailure(result, "WebSocket method must return akka.stream.javadsl.Flow", "wrongReturnType")
    }

    "reject WebSocket method with different input and output types" in {
      val result = compileTestSource("invalid/InvalidWebSocketDifferentTypes.java")
      assertCompilationFailure(result, "must have the same input and output message types", "differentInOut")
    }

    "reject WebSocket method with unsupported message type" in {
      val result = compileTestSource("invalid/InvalidWebSocketUnsupportedMessageType.java")
      assertCompilationFailure(result, "unsupported message type", "unsupportedType")
    }

    "reject WebSocket method with wrong materialized value type" in {
      val result = compileTestSource("invalid/InvalidWebSocketWrongMatType.java")
      assertCompilationFailure(result, "must have akka.NotUsed as materialized value type", "wrongMatType")
    }
  }
}
