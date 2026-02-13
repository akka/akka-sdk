/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;

@Component(id = "hierarchy-kv-entity")
public class HierarchyKvEntity extends AbstractInbetweenKvEntity2<AbstractInbetweenEsEntity.State> {
  public record State(String value) {}
}
