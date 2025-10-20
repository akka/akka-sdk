/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

// View row type cannot be a primitive wrapper like String
@Component(id = "view-invalid-row-type")
public class ViewWithInvalidRowType extends View {

  @Query("SELECT * FROM users")
  public QueryEffect<UserRow> getUsers() {
    return null;
  }

  public static class UserRow {
    public String name;
  }

  @Consume.FromTopic("users-topic")
  public static class Users extends TableUpdater<String> {
    public Effect<String> onEvent(String event) {
      return effects().updateRow(event);
    }
  }
}
