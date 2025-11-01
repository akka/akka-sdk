/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "view-missing-kve-transformation")
public class ViewMissingKVETransformationHandler extends View {

  @Query("SELECT * FROM rows")
  public QueryEffect<ViewRow> getRows() {
    return null;
  }

  public static class ViewRow {
    public String value;
  }

  @Consume.FromKeyValueEntity(SimpleKeyValueEntity.class)
  public static class Rows extends TableUpdater<ViewRow> {
    // SimpleKeyValueEntity has Integer state, ViewRow is different
    // Missing transformation handler: Effect<ViewRow> onChange(Integer state)
  }
}
