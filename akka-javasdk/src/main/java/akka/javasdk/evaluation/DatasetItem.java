/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import java.util.Map;
import java.util.Optional;

/**
 * One case in a {@link Dataset}.
 *
 * @param id the stable id of the item, authored alongside the data — not positional
 * @param input what is sent to the target
 * @param expectedOutput what a reference-requiring evaluator checks the target's response against,
 *     if the case has one
 * @param metadata arbitrary, authored metadata about the item
 */
public record DatasetItem(
    String id,
    String input,
    Optional<ExpectedOutput> expectedOutput,
    Map<String, String> metadata) {}
