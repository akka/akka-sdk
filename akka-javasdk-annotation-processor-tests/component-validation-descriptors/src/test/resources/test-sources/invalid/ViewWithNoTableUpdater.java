/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.View;

// View must have at least one TableUpdater
@Component(id = "view-no-table-updater")
public class ViewWithNoTableUpdater extends View {

  @Query("SELECT * FROM users")
  public QueryEffect<UserRow> getUsers() {
    return null;
  }

  public static class UserRow {
    public String name;
  }
}
