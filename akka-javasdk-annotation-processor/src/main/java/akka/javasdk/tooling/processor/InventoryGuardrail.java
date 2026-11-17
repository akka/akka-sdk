/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import java.util.List;
import java.util.Optional;

/** An enabled guardrail entry of the service inventory. Lists are sorted and immutable. */
record InventoryGuardrail(
    String name,
    String className,
    String category,
    boolean reportOnly,
    String failMode,
    List<String> agents,
    List<String> agentRoles,
    List<String> tools,
    Optional<String> controlId) {}
