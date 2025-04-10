package counter.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import counter.domain.CounterEvent;
import counter.domain.CounterEvent.ValueIncreased;
import counter.domain.CounterEvent.ValueMultiplied;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

// tag::class[]
@ComponentId("counter-topic-view")
public class CounterTopicView extends View {

  private static final Logger logger = LoggerFactory.getLogger(CounterTopicView.class);

  public record CounterRow(String counterId, int value, Instant lastChange) {}

  public record CountersResult(List<CounterRow> foundCounters) {}

  @Consume.FromTopic("counter-events-with-meta")  // <1>
  public static class CounterUpdater extends TableUpdater<CounterRow> {

    public Effect<CounterRow> onEvent(CounterEvent event) {
      String counterId = updateContext().metadata().asCloudEvent().subject().get(); // <2>
      var newValue = switch (event) {
        case ValueIncreased increased -> increased.updatedValue();
        case ValueMultiplied multiplied -> multiplied.updatedValue();
      };
      logger.info("Received new value for counter id {}: {}", counterId, event);

      return effects().updateRow(new CounterRow(counterId, newValue, Instant.now())); // <3>
    }
  }

  @Query("SELECT * AS foundCounters FROM counters WHERE value >= :minimum")
  public View.QueryEffect<CountersResult> countersHigherThan(int minimum) {
    return queryResult();
  }
}
// end::class[]