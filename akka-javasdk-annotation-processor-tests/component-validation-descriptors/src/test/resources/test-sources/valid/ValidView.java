/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "valid-view")
public class ValidView extends View {

  @Query("SELECT * FROM users")
  public QueryEffect<UserRow> getUsers() {
    return null;
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
