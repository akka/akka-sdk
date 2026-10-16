package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.StartFrom;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "view-start-from")
public class ViewWithStartFrom extends View {

  @Query("SELECT * FROM users")
  public QueryEffect<UserRow> getUsers() {
    return null;
  }

  public static class UserRow {
    public String name;
  }

  @StartFrom(latest = true)
  @Consume.FromEventSourcedEntity(SimpleEventSourcedEntity.class)
  public static class Users extends TableUpdater<UserRow> {
    public Effect<UserRow> onEvent(SimpleEventSourcedEntity.CounterEvent event) {
      return effects().updateRow(new UserRow());
    }
  }
}
