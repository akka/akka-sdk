/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

// The concrete view declares an updater for the same (guessed) table as the inherited one, which
// must override it rather than be treated as a second table updater requiring @Table.
@Component(id = "valid-view-overriding-updater")
public class ValidViewOverridingTableUpdater extends BaseViewToOverride {

  @Query("SELECT * FROM users")
  public QueryEffect<UserRow> getUsers() {
    return null;
  }

  @Consume.FromTopic("users-topic")
  public static class Users extends TableUpdater<UserRow> {
    public Effect<UserRow> onEvent(UserEvent event) {
      return effects().updateRow(new UserRow());
    }
  }
}

abstract class BaseViewToOverride extends View {

  public static class UserRow {
    public String name;
  }

  public static class UserEvent {
    public String name;
  }

  @Consume.FromTopic("users-topic")
  public static class Users extends TableUpdater<UserRow> {
    public Effect<UserRow> onEvent(UserEvent event) {
      return effects().updateRow(new UserRow());
    }
  }
}
