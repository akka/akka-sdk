/*
 * Copyright 2021 Lightbend Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example;

import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;

import static com.example.CounterEvent.ValueIncreased;
import static com.example.CounterEvent.ValueMultiplied;

@TypeId("counter")
public class Counter extends EventSourcedEntity<Integer, CounterEvent> {

  @Override
  public Integer emptyState() {
    return 0;
  }


  public Effect<String> increase(Integer value) {
    return effects()
      .emitEvent(new ValueIncreased(value))
      .thenReply(Object::toString);
  }

  public Effect<String> get() {
    return effects().reply(currentState().toString());
  }

  public Effect<String> multiply(Integer value) {
    return effects()
      .emitEvent(new ValueMultiplied(value))
      .thenReply(Object::toString);
  }

  @Override
  public Integer applyEvent(CounterEvent event) {
    return switch (event) {
      case ValueIncreased evt -> currentState() + evt.value();
      case ValueMultiplied evt -> currentState() * evt.value();
    };
  }
}

