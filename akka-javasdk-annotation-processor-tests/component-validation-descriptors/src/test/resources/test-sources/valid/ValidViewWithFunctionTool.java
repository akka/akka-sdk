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

@Component(id = "valid-view-with-function-tool")
public class ValidViewWithFunctionTool extends View {

  @FunctionTool(description = "This is allowed on QueryEffect")
  @Query("SELECT * FROM users WHERE name = :name")
  public QueryEffect<UserRow> getUserByName(String name) {
    return queryResult();
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
