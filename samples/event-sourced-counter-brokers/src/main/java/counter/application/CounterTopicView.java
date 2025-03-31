package counter.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import counter.domain.CounterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

// tag::class[]
@ComponentId("counter-topic-view")
@Consume.FromEventSourcedEntity(CounterEntity.class)

public class CounterTopicView extends View {

  private static final Logger logger = LoggerFactory.getLogger(CounterTopicView.class);

  public record CounterRow(String counterId, int value, Instant lastChange) {}

  @Consume.FromTopic("counters")  // <1>
  public static class CounterUpdater extends TableUpdater<CounterRow> {

    public Effect<CounterRow> onEvent(CounterEvent event) {
      String counterId = updateContext().metadata().asCloudEvent().subject().get(); // <2>
      var newValue = switch (event) {
        case CounterEvent.ValueIncreased increased -> increased.updatedValue();
        case CounterEvent.ValueMultiplied multiplied -> multiplied.updatedValue();
      };
      logger.info("Received new value for counter id {}: {}", counterId, event);

      return effects().updateRow(new CounterRow(counterId, newValue, Instant.now())); // <3>
    }
  }
}
// end::class[]