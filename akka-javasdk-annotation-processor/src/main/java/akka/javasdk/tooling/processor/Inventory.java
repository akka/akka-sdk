/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import java.util.List;

/** The service inventory of one artifact. */
record Inventory(
    String groupId,
    String artifactId,
    boolean componentLibrary,
    List<InventoryComponent> components) {}
