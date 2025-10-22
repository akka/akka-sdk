/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ViewValidationSpec extends AnyWordSpec with Matchers with CompilationTestSupport {

  "View validation" should {

    "accept valid View" in {
      val result = compileTestSource("valid/ValidView.java")
      assertCompilationSuccess(result)
    }

    "reject View with @Table annotation" in {
      val result = compileTestSource("invalid/ViewWithTableAnnotation.java")
      assertCompilationFailure(result, "ViewWithTableAnnotation", "View itself should not be annotated with @Table")
    }

    "reject View with no TableUpdater" in {
      val result = compileTestSource("invalid/ViewWithNoTableUpdater.java")
      assertCompilationFailure(result, "ViewWithNoTableUpdater", "at least one", "TableUpdater")
    }

    "reject View with no query method" in {
      val result = compileTestSource("invalid/ViewWithNoQuery.java")
      assertCompilationFailure(result, "ViewWithNoQuery", "at least one", "@Query")
    }

    "reject View with invalid row type" in {
      val result = compileTestSource("invalid/ViewWithInvalidRowType.java")
      assertCompilationFailure(result, "ViewWithInvalidRowType", "row type", "java.lang.String", "not supported")
    }
  }
}
