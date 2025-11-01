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

@Component(id = "multi-table-view-with-multiple-queries")
public class MultiTableViewWithMultipleQueries extends View {

  @Query("SELECT * FROM users")
  public QueryEffect<UserRow> getAllUsers() {
    return null;
  }

  @Query("SELECT * FROM users WHERE email = :email")
  public QueryEffect<UserRow> getUserByEmail(String email) {
    return null;
  }

  public static class UserRow {
    public String name;
    public String email;
  }

  @Table("users")
  @Consume.FromTopic("users-topic")
  public static class Users extends TableUpdater<UserRow> {
    public Effect<UserRow> onEvent(UserEvent event) {
      return effects().updateRow(new UserRow());
    }
  }

  @Table("admins")
  @Consume.FromTopic("admins-topic")
  public static class Admins extends TableUpdater<UserRow> {
    public Effect<UserRow> onEvent(UserEvent event) {
      return effects().updateRow(new UserRow());
    }
  }

  public static class UserEvent {
    public String name;
  }
}
