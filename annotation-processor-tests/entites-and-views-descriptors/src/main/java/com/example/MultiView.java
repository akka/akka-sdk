/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;


import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.view.View;

@ComponentId("simple-view")
public class MultiView {


  public static class OneView extends View<String> {

  }

  public static class AnotherView extends View<String> {

  }

}
