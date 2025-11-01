/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "view-duplicated-delete-handlers")
public class ViewDuplicatedDeleteHandlers extends View {

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

    @DeleteHandler
    public Effect<UserRow> onDelete() {
      return effects().deleteRow();
    }

    @DeleteHandler
    public Effect<UserRow> onDelete2() {
      return effects().deleteRow();
    }
  }

  public static class UserEvent {
    public String name;
  }
}
