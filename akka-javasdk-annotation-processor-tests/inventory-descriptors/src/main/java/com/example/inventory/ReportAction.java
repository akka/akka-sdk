/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory;

import akka.javasdk.annotations.Component;

@Component(id = "report")
public class ReportAction extends BaseAction {

  public Effect report() {
    return effects().done();
  }
}
