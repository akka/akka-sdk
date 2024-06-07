/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.views;

import com.example.wiring.eventsourcedentities.counter.CounterEntity;
import com.example.wiring.eventsourcedentities.counter.CounterEvent;
import com.example.wiring.valueentities.user.AssignedCounter;
import com.example.wiring.valueentities.user.AssignedCounterEntity;
import com.example.wiring.valueentities.user.User;
import com.example.wiring.valueentities.user.UserEntity;
import kalix.javasdk.view.View;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;

import java.util.Optional;

@ViewId("user-counters")
public class UserCountersView {

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
  public UserCounters get(QueryParameters params) {
    return null;
  }

  @Table("users")
  public static class Users extends View<UserWithId> {
    @Subscribe.ValueEntity(UserEntity.class)
    public UpdateEffect<UserWithId> onChange(User user) {
      return effects()
          .updateState(
              new UserWithId(updateContext().eventSubject().orElse(""), user.email, user.name));
    }
  }

  @Table("counters")
  @Subscribe.EventSourcedEntity(CounterEntity.class)
  public static class Counters extends View<UserCounter> {
    private UserCounter counterState() {
      return Optional.ofNullable(viewState())
        .orElseGet(() -> new UserCounter(updateContext().eventSubject().orElse(""), 0));
    }

    public UpdateEffect<UserCounter> onEvent(CounterEvent.ValueIncreased event) {
      return effects().updateState(counterState().onValueIncreased(event));
    }

    public UpdateEffect<UserCounter> onEvent(CounterEvent.ValueMultiplied event) {
      return effects().updateState(counterState().onValueMultiplied(event));
    }

    public UpdateEffect<UserCounter> onEvent(CounterEvent.ValueSet event) {
      return effects().updateState(counterState().onValueSet(event));
    }
  }

  @Table("assigned")
  @Subscribe.ValueEntity(AssignedCounterEntity.class)
  public static class Assigned extends View<AssignedCounter> {}
}
