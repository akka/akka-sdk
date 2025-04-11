package counter.application;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import counter.domain.CounterEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CounterWithRealKafkaIntegrationTest extends TestKitSupport { // <1>

  // logger
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CounterWithRealKafkaIntegrationTest.class);

  // tag::kafka[]
  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
      .withEventingSupport(TestKit.Settings.EventingSupport.KAFKA);
  }
  // end::kafka[]

  @Test
  public void verifyCounterEventSourcedConsumesFromKafka() {
    // using random id to ensure isolation when running tests locally
    // with a kafka container since the container preserves state
    var counterId = UUID.randomUUID().toString();

    componentClient.forEventSourcedEntity(counterId)
        .method(CounterEntity::increase)
        .invoke(20);

    try (KafkaConsumer<String, byte[]> consumer = buildStringKafkaConsumer()) {
      consumer.subscribe(Collections.singletonList("counter-events-with-meta"));

      Awaitility.await()
          .ignoreExceptions()
          .atMost(ofSeconds(30))
          .untilAsserted(() -> {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(200));
            var foundRecord = false;
            for (ConsumerRecord<String, byte[]> r : records) {
              var increased = JsonSupport.decodeJson(CounterEvent.ValueIncreased.class, r.value());
              String subjectId = new String(r.headers().headers("ce-subject").iterator().next().value(), StandardCharsets.UTF_8);
              if (subjectId.equals(counterId) && increased.value() == 20) {
                foundRecord = true;
                break;
              }
            }

            assertTrue(foundRecord);
          });
    }
  }

  private static KafkaConsumer<String, byte[]> buildStringKafkaConsumer() {
    // Example Kafka consumer configuration
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-test-" + System.currentTimeMillis()); // using new consumer group every run
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    // Create Kafka consumer and subscribe to topic
    return new KafkaConsumer<>(props);
  }

}
