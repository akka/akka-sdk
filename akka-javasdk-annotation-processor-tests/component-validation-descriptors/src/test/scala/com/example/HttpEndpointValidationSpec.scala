/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import org.scalatest.wordspec.AnyWordSpec

class HttpEndpointValidationSpec extends AnyWordSpec with CompilationTestSupport {

  "HttpEndpointValidationProcessor" should {

    // ==================== Valid HTTP Endpoints ====================

    "accept valid public HTTP endpoint with basic CRUD operations" in {
      val result = compileTestSource("valid/ValidHttpEndpoint.java")
      assertCompilationSuccess(result)
    }

    "accept valid HTTP endpoint with multiple path parameters" in {
      val result = compileTestSource("valid/ValidHttpEndpointWithMultiplePathParams.java")
      assertCompilationSuccess(result)
    }

    "accept valid HTTP endpoint with wildcard at the end" in {
      val result = compileTestSource("valid/ValidHttpEndpointWithWildcard.java")
      assertCompilationSuccess(result)
    }

    "accept valid HTTP endpoint with root path" in {
      val result = compileTestSource("valid/ValidHttpEndpointWithRootPath.java")
      assertCompilationSuccess(result)
    }

    "accept valid HTTP endpoint with no body parameters" in {
      val result = compileTestSource("valid/ValidHttpEndpointNoBody.java")
      assertCompilationSuccess(result)
    }

    "accept valid HTTP endpoint with body parameter" in {
      val result = compileTestSource("valid/ValidHttpEndpointWithBodyParam.java")
      assertCompilationSuccess(result)
    }

    "accept valid HTTP endpoint with empty path annotation" in {
      val result = compileTestSource("valid/ValidHttpEndpointEmptyPath.java")
      assertCompilationSuccess(result)
    }

    "accept valid HTTP endpoint with leading slash in method path" in {
      val result = compileTestSource("valid/ValidHttpEndpointWithLeadingSlashInMethod.java")
      assertCompilationSuccess(result)
    }

    "accept valid HTTP endpoint with no leading slash in class path" in {
      val result = compileTestSource("valid/ValidHttpEndpointWithNoLeadingSlash.java")
      assertCompilationSuccess(result)
    }

    "accept valid HTTP endpoint with non-HTTP methods (ignored)" in {
      val result = compileTestSource("valid/ValidHttpEndpointWithNonHttpMethods.java")
      assertCompilationSuccess(result)
    }

    "accept valid HTTP endpoint with all HTTP method types" in {
      val result = compileTestSource("valid/ValidHttpEndpointAllMethods.java")
      assertCompilationSuccess(result)
    }

    // ==================== Public Modifier Validation ====================

    "reject non-public HTTP endpoint" in {
      val result = compileTestSource("invalid/HttpEndpointNotPublic.java")
      assertCompilationFailure(
        result,
        "HttpEndpointNotPublic is not marked with `public` modifier",
        "Components must be public")
    }

    "reject package-private HTTP endpoint" in {
      val result = compileTestSource("invalid/HttpEndpointPackagePrivate.java")
      assertCompilationFailure(
        result,
        "HttpEndpointPackagePrivate is not marked with `public` modifier",
        "Components must be public")
    }

    // ==================== Path Parameter Validation ====================

    "reject HTTP endpoint with missing path parameter" in {
      val result = compileTestSource("invalid/HttpEndpointMissingPathParam.java")
      assertCompilationFailure(
        result,
        "There are more parameters in the path expression",
        "HttpEndpointMissingPathParam.list1")
    }

    "reject HTTP endpoint with wrong parameter name" in {
      val result = compileTestSource("invalid/HttpEndpointWrongParamName.java")
      assertCompilationFailure(
        result,
        "The parameter [id]",
        "does not match the method parameter name [bob]",
        "HttpEndpointWrongParamName.list2")
    }

    "reject HTTP endpoint with wrong second parameter name" in {
      val result = compileTestSource("invalid/HttpEndpointWrongSecondParamName.java")
      assertCompilationFailure(
        result,
        "The parameter [bob]",
        "does not match the method parameter name [value]",
        "HttpEndpointWrongSecondParamName.list3")
    }

    "reject HTTP endpoint with too many parameters" in {
      val result = compileTestSource("invalid/HttpEndpointTooManyParams.java")
      assertCompilationFailure(
        result,
        "There are [2] parameters",
        "[value,body]",
        "not matched by the path expression",
        "HttpEndpointTooManyParams.list5")
    }

    // ==================== Wildcard Validation ====================

    "reject HTTP endpoint with wildcard not at the last segment" in {
      val result = compileTestSource("invalid/HttpEndpointWildcardNotLast.java")
      assertCompilationFailure(result, "Wildcard path can only be the last segment of the path")
    }

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

    "reject WebSocket method with a body parameter" in {
      val result = compileTestSource("invalid/InvalidWebSocketBodyParam.java")
      assertCompilationFailure(
        result,
        "Request body parameter defined for WebSocket method",
        "withBodyParam",
        "this is not supported")
    }

  }
}
