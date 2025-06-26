/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.pubsub;


import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import akkajavasdk.components.eventsourcedentities.counter.CounterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static akka.javasdk.impl.MetadataImpl.CeSubject;


@ComponentId("counter_view_topic_sub")
public class ViewFromCounterEventsTopic extends View {

  public static final String COUNTER_EVENTS_TOPIC = "counter_events";

  private static Logger logger = LoggerFactory.getLogger(ViewFromCounterEventsTopic.class);

  public record QueryParameters(int counterValue) {}
  public record CounterViewList(List<CounterView> counters) {}

  @Query("SELECT * AS counters FROM counter_view_topic_sub WHERE value < :counterValue")
  public QueryEffect<CounterViewList> getCountersLessThan(QueryParameters params) {
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
