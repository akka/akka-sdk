/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.eventsourcedentities.hierarchy;

import akka.javasdk.consumer.Consumer;
import akkajavasdk.StaticTestBuffer;

public abstract class AbstractTextConsumer extends Consumer {

  public static final String BUFFER_KEY = "abstract-es-text-consumer";

  protected void onText(String text) {
    StaticTestBuffer.addValue(BUFFER_KEY, text);
  }

}
