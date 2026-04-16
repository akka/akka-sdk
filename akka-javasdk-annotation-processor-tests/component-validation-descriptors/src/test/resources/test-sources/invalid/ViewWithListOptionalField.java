/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import java.util.List;
import java.util.Optional;

@Component(id = "view-list-optional-field")
public class ViewWithListOptionalField extends View {

  @Query("SELECT * FROM items")
  public QueryEffect<ItemRow> getItems() {
    return null;
  }

  public static class ItemRow {
    public String id;
    public List<Optional<String>> values;
  }

  @Consume.FromTopic("items-topic")
  public static class Items extends TableUpdater<ItemRow> {
    public Effect<ItemRow> onEvent(String event) {
      return effects().updateRow(new ItemRow());
    }
  }
}
