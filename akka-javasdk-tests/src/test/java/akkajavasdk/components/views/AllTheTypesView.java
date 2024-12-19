/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.views;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@ComponentId("all_the_field_types_view")
public class AllTheTypesView extends View {


  @Consume.FromKeyValueEntity(AllTheTypesKvEntity.class)
  public static class Events extends TableUpdater<AllTheTypesKvEntity.AllTheTypes> { }

  @Query("SELECT * FROM events")
  public QueryStreamEffect<AllTheTypesKvEntity.AllTheTypes> allRows() {
    return queryStreamResult();
  }

}
