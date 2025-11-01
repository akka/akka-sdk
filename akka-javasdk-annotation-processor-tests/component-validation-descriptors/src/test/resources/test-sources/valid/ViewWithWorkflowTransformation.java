/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "view-with-workflow-transformation")
public class ViewWithWorkflowTransformation extends View {

  @Query("SELECT * FROM rows")
  public QueryEffect<ViewRow> getRows() {
    return null;
  }

  public static class ViewRow {
    public Integer count;
  }

  @Consume.FromWorkflow(SimpleWorkflow.class)
  public static class Rows extends TableUpdater<ViewRow> {
    // SimpleWorkflow has String state, ViewRow is different
    // This handler transforms String -> ViewRow
    public Effect<ViewRow> onChange(String state) {
      return effects().updateRow(new ViewRow());
    }
  }
}
