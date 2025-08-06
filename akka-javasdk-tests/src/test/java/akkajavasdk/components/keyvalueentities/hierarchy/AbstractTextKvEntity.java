/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.keyvalueentities.hierarchy;

import akka.javasdk.keyvalueentity.KeyValueEntity;

public class AbstractTextKvEntity extends KeyValueEntity<AbstractTextKvEntity.State> {

  public record State(String value) {}
}
