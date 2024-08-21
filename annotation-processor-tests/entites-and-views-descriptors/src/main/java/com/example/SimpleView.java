/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.view.View;
import akka.platform.javasdk.view.TableUpdater;

@ComponentId("simple-view")
public class SimpleView extends View {

  public static class TheTable extends TableUpdater<String> {}

}
