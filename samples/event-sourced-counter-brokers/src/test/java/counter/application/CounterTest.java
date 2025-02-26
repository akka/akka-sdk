package counter.application;

import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;
import counter.domain.CounterEvent;
import jnr.ffi.annotations.In;
import org.junit.jupiter.api.Test;

import static counter.domain.CounterEvent.ValueIncreased;
import static counter.domain.CounterEvent.ValueMultiplied;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CounterTest {

  @Test
  public void testIncrease() {
    EventSourcedTestKit<Integer, CounterEvent, CounterEntity> testKit = EventSourcedTestKit.of(CounterEntity::new);
    EventSourcedResult<Integer> result = testKit.method(CounterEntity::increase).invoke(10);

    assertTrue(result.isReply());
    assertEquals(10, result.getReply());
    assertEquals(1, result.getAllEvents().size());
    result.getNextEventOfType(ValueIncreased.class);
    assertEquals(10, testKit.getState());
  }

  @Test
  public void testMultiply() {
    EventSourcedTestKit<Integer, CounterEvent, CounterEntity> testKit = EventSourcedTestKit.of(CounterEntity::new);
    // set initial value to 2
    testKit.method(CounterEntity::increase).invoke(2);

    EventSourcedResult<Integer> result = testKit.method(CounterEntity::multiply).invoke(10);
    assertTrue(result.isReply());
    assertEquals(20, result.getReply());
    assertEquals(1, result.getAllEvents().size());
    result.getNextEventOfType(ValueMultiplied.class);
    assertEquals(20, testKit.getState());
  }
}
