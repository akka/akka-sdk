/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "view-with-function-tool-on-query-stream-effect")
public class ViewWithFunctionToolOnQueryStreamEffect extends View {

  @Query("SELECT * FROM users")
  public QueryEffect<UserRow> getUsers() {
    return queryResult();
  }

  // @FunctionTool is not allowed on QueryStreamEffect
  @FunctionTool(description = "This should not be allowed on QueryStreamEffect")
  @Query("SELECT * FROM users")
  public QueryStreamEffect<UserRow> streamUsers() {
    return queryStreamResult();
  }

  public static class UserRow {
    public String name;
    public String email;
  }

  @Consume.FromTopic("users-topic")
  public static class Users extends TableUpdater<UserRow> {
    public Effect<UserRow> onEvent(UserEvent event) {
      return effects().updateRow(new UserRow());
    }
  }

  public static class UserEvent {
    public String name;
  }
}
