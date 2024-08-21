/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;


import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.view.View;
import akka.platform.javasdk.view.TableUpdater;

@ComponentId("simple-view")
public class MultiView extends View {


  public static class OneTable extends TableUpdater<String> {

  }

  public static class AnotherTable extends TableUpdater<String> {

  }

}
