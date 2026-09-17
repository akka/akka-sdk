/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.eval;

/** A customer as the CRM returns it. */
public record Customer(String id, String name, String tier) {}
