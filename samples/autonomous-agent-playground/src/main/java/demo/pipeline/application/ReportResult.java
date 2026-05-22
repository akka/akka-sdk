/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package demo.pipeline.application;

/** Typed result for each pipeline phase. */
public record ReportResult(String phase, String content) {}
