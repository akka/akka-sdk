/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Table;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "view-with-function-tool-on-private-method")
public class ViewWithFunctionToolOnPrivateMethod extends View {

  @Query("SELECT * FROM view_table")
  public QueryEffect<ViewRow> getAll() {
    return queryResult();
  }

  // @FunctionTool is not allowed on private methods
  @FunctionTool(description = "This should not be allowed on private methods")
  private QueryEffect<ViewRow> privateMethod() {
    return queryResult();
  }

  @Table("view_table")
  @Consume.FromTopic("my-topic")
  public static class ViewTableUpdater extends TableUpdater<ViewRow> {
    public Effect<ViewRow> onEvent(ViewRow row) {
      return effects().updateRow(row);
    }
  }

  public record ViewRow(String id) {}
}
