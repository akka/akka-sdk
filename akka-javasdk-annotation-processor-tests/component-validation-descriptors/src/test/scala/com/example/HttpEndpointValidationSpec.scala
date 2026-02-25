/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.example.CompilationTestSupport.CompileTimeValidation
import com.example.CompilationTestSupport.RuntimeValidation
import com.example.CompilationTestSupport.ValidationMode
import org.scalatest.wordspec.AnyWordSpec

class CompileTimeHttpEndpointValidationSpec extends AbstractHttpEndpointValidationSpec(CompileTimeValidation)
class RuntimeHttpEndpointValidationSpec extends AbstractHttpEndpointValidationSpec(RuntimeValidation)

abstract class AbstractHttpEndpointValidationSpec(val validationMode: ValidationMode)
    extends AnyWordSpec
    with CompilationTestSupport {

  s"HttpEndpointValidationProcessor ($validationMode)" should {

    // ==================== Valid HTTP Endpoints ====================

    "accept valid public HTTP endpoint with basic CRUD operations" in {
      assertValid("valid/ValidHttpEndpoint.java")
    }

    "accept valid HTTP endpoint with multiple path parameters" in {
      assertValid("valid/ValidHttpEndpointWithMultiplePathParams.java")
    }

    "accept valid HTTP endpoint with wildcard at the end" in {
      assertValid("valid/ValidHttpEndpointWithWildcard.java")
    }

    "accept valid HTTP endpoint with root path" in {
      assertValid("valid/ValidHttpEndpointWithRootPath.java")
    }

    "accept valid HTTP endpoint with no body parameters" in {
      assertValid("valid/ValidHttpEndpointNoBody.java")
    }

    "accept valid HTTP endpoint with body parameter" in {
      assertValid("valid/ValidHttpEndpointWithBodyParam.java")
    }

    "accept valid HTTP endpoint with empty path annotation" in {
      assertValid("valid/ValidHttpEndpointEmptyPath.java")
    }

    "accept valid HTTP endpoint with leading slash in method path" in {
      assertValid("valid/ValidHttpEndpointWithLeadingSlashInMethod.java")
    }

    "accept valid HTTP endpoint with no leading slash in class path" in {
      assertValid("valid/ValidHttpEndpointWithNoLeadingSlash.java")
    }

    "accept valid HTTP endpoint with non-HTTP methods (ignored)" in {
      assertValid("valid/ValidHttpEndpointWithNonHttpMethods.java")
    }

    "accept valid HTTP endpoint with all HTTP method types" in {
      assertValid("valid/ValidHttpEndpointAllMethods.java")
    }

    // ==================== Public Modifier Validation ====================

    "reject non-public HTTP endpoint" in {
      assertInvalid(
        "invalid/HttpEndpointNotPublic.java",
        "HttpEndpointNotPublic is not marked with `public` modifier",
        "Components must be public")
    }

    "reject package-private HTTP endpoint" in {
      assertInvalid(
        "invalid/HttpEndpointPackagePrivate.java",
        "HttpEndpointPackagePrivate is not marked with `public` modifier",
        "Components must be public")
    }

    // ==================== Path Parameter Validation ====================

    "reject HTTP endpoint with missing path parameter" in {
      assertInvalid(
        "invalid/HttpEndpointMissingPathParam.java",
        "There are more parameters in the path expression",
        "HttpEndpointMissingPathParam.list1")
    }

    "reject HTTP endpoint with wrong parameter name" in {
      assertInvalid(
        "invalid/HttpEndpointWrongParamName.java",
        "The parameter [id]",
        "does not match the method parameter name [bob]",
        "HttpEndpointWrongParamName.list2")
    }

    "reject HTTP endpoint with wrong second parameter name" in {
      assertInvalid(
        "invalid/HttpEndpointWrongSecondParamName.java",
        "The parameter [bob]",
        "does not match the method parameter name [value]",
        "HttpEndpointWrongSecondParamName.list3")
    }

    "reject HTTP endpoint with too many parameters" in {
      assertInvalid(
        "invalid/HttpEndpointTooManyParams.java",
        "There are [2] parameters",
        "[value,body]",
        "not matched by the path expression",
        "HttpEndpointTooManyParams.list5")
    }

    // ==================== Wildcard Validation ====================

    "reject HTTP endpoint with wildcard not at the last segment" in {
      assertInvalid(
        "invalid/HttpEndpointWildcardNotLast.java",
        "Wildcard path can only be the last segment of the path")
    }

    "accept valid WebSocket endpoints with String, ByteString, and Message types" in {
      assertValid("valid/ValidWebSocketEndpoint.java")
    }

    "reject WebSocket method with wrong return type" in {
      assertInvalid(
        "invalid/InvalidWebSocketWrongReturnType.java",
        "WebSocket method must return akka.stream.javadsl.Flow",
        "wrongReturnType")
    }

    "reject WebSocket method with different input and output types" in {
      assertInvalid(
        "invalid/InvalidWebSocketDifferentTypes.java",
        "must have the same input and output message types",
        "differentInOut")
    }

    "reject WebSocket method with unsupported message type" in {
      assertInvalid(
        "invalid/InvalidWebSocketUnsupportedMessageType.java",
        "unsupported message type",
        "unsupportedType")
    }

    "reject WebSocket method with wrong materialized value type" in {
      assertInvalid(
        "invalid/InvalidWebSocketWrongMatType.java",
        "must have akka.NotUsed as materialized value type",
        "wrongMatType")
    }

    "reject WebSocket method with a body parameter" in {
      assertInvalid(
        "invalid/InvalidWebSocketBodyParam.java",
        "Request body parameter defined for WebSocket method",
        "withBodyParam",
        "this is not supported")
    }

  }
}
