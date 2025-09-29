/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "multi-view")
public class MultiView extends View {

  public static class OneTable extends TableUpdater<String> {}

  public static class AnotherTable extends TableUpdater<String> {}
}
