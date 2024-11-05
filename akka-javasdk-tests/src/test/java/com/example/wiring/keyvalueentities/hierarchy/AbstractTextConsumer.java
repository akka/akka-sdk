/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.keyvalueentities.hierarchy;

import akka.javasdk.consumer.Consumer;
import com.example.wiring.StaticTestBuffer;

public abstract class AbstractTextConsumer extends Consumer {

  public static final String BUFFER_KEY = "abstract-kv-text-consumer";

  protected void onText(String text) {
    StaticTestBuffer.addValue(BUFFER_KEY, text);
  }

}