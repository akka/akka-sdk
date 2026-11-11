package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

@Component(id = "view-invalid-platform-service")
public class ViewWithInvalidPlatformService extends View {

  @Consume.FromServiceStream(id = "my-stream", service = "my-service", platformService = "svc")
  public static class Rows extends TableUpdater<String> {
    public Effect<String> onEvent(String event) {
      return effects().updateRow(event);
    }
  }

  @Query("SELECT * FROM view_invalid_platform_service")
  public QueryEffect<String> getAll() {
    return queryResult();
  }
}
