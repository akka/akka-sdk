/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@ComponentId("multi-view")
public class MultiView extends View {

  public static class OneTable extends TableUpdater<String> {}

  public static class AnotherTable extends TableUpdater<String> {}
}
