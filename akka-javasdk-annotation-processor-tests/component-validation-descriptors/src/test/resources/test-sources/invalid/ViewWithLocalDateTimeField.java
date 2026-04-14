/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import java.time.LocalDateTime;

@Component(id = "view-localdatetime-field")
public class ViewWithLocalDateTimeField extends View {

  @Query("SELECT * FROM items")
  public QueryEffect<ItemRow> getItems() {
    return null;
  }

  public static class ItemRow {
    public String id;
    public LocalDateTime createdAt;
  }

  @Consume.FromTopic("items-topic")
  public static class Items extends TableUpdater<ItemRow> {
    public Effect<ItemRow> onEvent(String event) {
      return effects().updateRow(new ItemRow());
    }
  }
}
