/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.view.View;
import akka.javasdk.view.TableUpdater;

@ComponentId("simple-view")
public class SimpleView extends View {

  public static class TheTable extends TableUpdater<String> {}

}
