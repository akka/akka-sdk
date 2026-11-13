/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory;

import akka.javasdk.annotations.Component;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "carts")
public class CartsView extends View {

  public static class Carts extends TableUpdater<String> {}
}
