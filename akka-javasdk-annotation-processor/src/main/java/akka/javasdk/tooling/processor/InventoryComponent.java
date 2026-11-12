/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import java.util.Optional;

/** A component or endpoint entry of the service inventory. */
record InventoryComponent(
    String kind,
    Optional<String> id,
    String className,
    Optional<String> name,
    Optional<String> description,
    Optional<String> role) {}
