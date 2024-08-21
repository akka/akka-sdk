/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.views;

import akka.platform.javasdk.view.TableUpdater;
import com.example.wiring.eventsourcedentities.counter.CounterEntity;
import com.example.wiring.eventsourcedentities.counter.CounterEvent;
import com.example.wiring.keyvalueentities.user.AssignedCounter;
import com.example.wiring.keyvalueentities.user.AssignedCounterEntity;
import com.example.wiring.keyvalueentities.user.User;
import com.example.wiring.keyvalueentities.user.UserEntity;
import akka.platform.javasdk.view.View;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.Table;
import akka.platform.javasdk.annotations.ComponentId;

import java.util.Optional;

@ComponentId("user-counters")
public class UserCountersView extends View {

  public record QueryParameters(String userId) {}
  public static QueryParameters queryParam(String userId) {
    return new QueryParameters(userId);
  }

  @Query("""
    SELECT users.*, counters.* as counters
    FROM users
    JOIN assigned ON assigned.assigneeId = users.id
    JOIN counters ON assigned.counterId = counters.id
    WHERE users.id = :userId
    ORDER BY counters.id
    """)
  public View.QueryEffect<UserCounters> get(QueryParameters params) {
    return queryResult();
  }

  @Table("users")
  @Consume.FromKeyValueEntity(UserEntity.class)
  public static class Users extends TableUpdater<UserWithId> {
    public Effect<UserWithId> onChange(User user) {
      return effects()
          .updateRow(
              new UserWithId(updateContext().eventSubject().orElse(""), user.email, user.name));
    }
  }

  @Table("counters")
  @Consume.FromEventSourcedEntity(CounterEntity.class)
  public static class Counters extends TableUpdater<UserCounter> {


    private UserCounter counterState() {
      return Optional.ofNullable(rowState())
          .orElseGet(() -> new UserCounter(updateContext().eventSubject().orElse(""), 0));
    }

    public Effect<UserCounter> onEvent(CounterEvent.ValueIncreased event) {
      return effects().updateRow(counterState().onValueIncreased(event));
    }

    public Effect<UserCounter> onEvent(CounterEvent.ValueMultiplied event) {
      return effects().updateRow(counterState().onValueMultiplied(event));
    }

    public Effect<UserCounter> onEvent(CounterEvent.ValueSet event) {
      return effects().updateRow(counterState().onValueSet(event));
    }
  }


  @Table("assigned")
  @Consume.FromKeyValueEntity(AssignedCounterEntity.class)
  public static class Assigned extends TableUpdater<AssignedCounter> {}
}
