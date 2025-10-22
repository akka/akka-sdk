/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Table;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

// View should not have @Table annotation
@Component(id = "view-with-table")
@Table("wrong")
public class ViewWithTableAnnotation extends View {

  @Query("SELECT * FROM users")
  public QueryEffect<UserRow> getUsers() {
    return null;
  }

  public static class UserRow {
    public String name;
  }

  @Consume.FromTopic("users-topic")
  public static class Users extends TableUpdater<UserRow> {
    public Effect<UserRow> onEvent(String event) {
      return effects().updateRow(new UserRow());
    }
  }
}
