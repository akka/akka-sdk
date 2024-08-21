/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.pubsub;


import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.view.TableUpdater;
import akka.platform.javasdk.view.View;
import com.example.wiring.eventsourcedentities.counter.CounterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static akka.platform.javasdk.impl.MetadataImpl.CeSubject;
import static com.example.wiring.pubsub.PublishESToTopic.COUNTER_EVENTS_TOPIC;


@ComponentId("counter_view_topic_sub")
public class ViewFromCounterEventsTopic extends View {

  private static Logger logger = LoggerFactory.getLogger(ViewFromCounterEventsTopic.class);

  public record QueryParameters(int counterValue) {}
  public record CounterViewList(List<CounterView> counters) {}

  @Query("SELECT * AS counters FROM counter_view_topic_sub WHERE value < :counterValue")
  public QueryEffect<CounterViewList> getCounter(QueryParameters params) {
    return queryResult();
  }

  @Consume.FromTopic(COUNTER_EVENTS_TOPIC)
  public static class CounterViewTopicSub extends TableUpdater<CounterView> {

    @Override
    public CounterView emptyRow() {
      return new CounterView("", 0);
    }

    public Effect<CounterView> handleIncrease(CounterEvent.ValueIncreased increased) {
      String entityId = updateContext().metadata().get(CeSubject()).orElseThrow();
      logger.info("Consuming: " + increased + " from " + entityId);
      return effects().updateRow(new CounterView(entityId, rowState().value() + increased.value()));
    }

    public Effect<CounterView> handleMultiply(CounterEvent.ValueMultiplied multiplied) {
      String entityId = updateContext().metadata().get(CeSubject()).orElseThrow();
      logger.info("Consuming: " + multiplied + " from " + entityId);
      return effects().updateRow(new CounterView(entityId, rowState().value() * multiplied.value()));

    }
  }
}
