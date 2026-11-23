/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components;

/** Custom dependency that {@link Bootstrap} provides with the name {@code service}. */
public record OverridableDependency(String name) {}
