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

package com.example.domain;

import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.eventsourcedentity.EventSourcedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.domain.CounterEvent.ValueIncreased;

@ComponentId("counter")
public class Counter extends EventSourcedEntity<Integer, CounterEvent> {

  private Logger logger = LoggerFactory.getLogger(Counter.class);

  private final Clock clock;

  //injecting custom dependency to the Akka Platform component
  public Counter(Clock clock) {
    this.clock = clock;
  }

  @Override
  public Integer emptyState() {
    return 0;
  }


  public Effect<String> increase(Integer value) {

    //accept updates only after 12:00
    if (clock.now().getHour() > 12) {
      logger.info("Counter {} increased by {}", this.commandContext().entityId(), value);
      return effects()
        .persist(new ValueIncreased(value))
        .thenReply(Object::toString);
    } else {
      logger.info("Counter {} ignored increase {}", this.commandContext().entityId(), value);
      return effects()
        .reply("ignored");
    }
  }

  public Effect<Integer> get() {
    return effects().reply(currentState());
  }


  @Override
  public Integer applyEvent(CounterEvent event) {
    return switch (event) {
      case ValueIncreased evt -> currentState() + evt.value();
    };
  }
}

