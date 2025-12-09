/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

// View must have at least one @Query method
@Component(id = "view-no-query")
public class ViewWithNoQuery extends View {

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
