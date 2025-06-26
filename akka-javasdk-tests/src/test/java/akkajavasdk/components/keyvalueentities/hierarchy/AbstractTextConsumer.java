/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.keyvalueentities.hierarchy;

import akka.javasdk.consumer.Consumer;
import akkajavasdk.StaticTestBuffer;

public abstract class AbstractTextConsumer extends Consumer {

  public static final String BUFFER_KEY = "abstract-kv-text-consumer";

  protected void onText(String text) {
    StaticTestBuffer.addValue(BUFFER_KEY, text);
  }

}
