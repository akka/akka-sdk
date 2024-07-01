/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.pubsub;


import com.example.wiring.eventsourcedentities.counter.CounterEvent;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.example.wiring.pubsub.PublishESToTopic.COUNTER_EVENTS_TOPIC;
import static kalix.javasdk.impl.MetadataImpl.CeSubject;


@ViewId("counter_view_topic_sub")
@Table("counter_view_topic_sub")
@Subscribe.Topic(COUNTER_EVENTS_TOPIC)
public class ViewFromCounterEventsTopic extends View<CounterView> {

  private Logger logger = LoggerFactory.getLogger(getClass());

  public record QueryParameters(int counterValue) {}
  public record CounterViewList(List<CounterView> counters) {}

  @Override
  public CounterView emptyState() {
    return new CounterView("", 0);
  }

  @Query("SELECT * AS counters FROM counter_view_topic_sub WHERE value < :counterValue")
  public CounterViewList getCounter(QueryParameters params) {
    return null;
  }

  public Effect<CounterView> handleIncrease(CounterEvent.ValueIncreased increased) {
    String entityId = updateContext().metadata().get(CeSubject()).orElseThrow();
    logger.info("Consuming: " + increased + " from " + entityId);
    return effects().updateState(new CounterView(entityId, viewState().value() + increased.value()));
  }

  public Effect<CounterView> handleMultiply(CounterEvent.ValueMultiplied multiplied) {
    String entityId = updateContext().metadata().get(CeSubject()).orElseThrow();
    logger.info("Consuming: " + multiplied + " from " + entityId);
    return effects().updateState(new CounterView(entityId, viewState().value() * multiplied.value()));
  }
}
