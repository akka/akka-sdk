/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package demo.docreview.application;

import java.util.List;

/** Typed result for document review tasks. */
public record ReviewResult(String assessment, List<String> findings, boolean compliant) {}
