package user.registry.application;


import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import user.registry.domain.UniqueEmail;

import java.time.Duration;

/**
 * This Consumer consumes from the UniqueEmailEntity state changes.
 * <p>
 * In the choreography, this subscriber will react to state changes from the UniqueEmailEntity.
 * <p>
 * When it sees an email address that is not confirmed, it will schedule a timer to fire in 10 seconds.
 * When it sees an email address that is confirmed, it will delete the timer (if it exists).
 * <p>
 * Note:
 * This is just an example of how to use timers. In a real application, you would probably want to use much longer timeout.
 * We use 10 seconds here to make the example easier to test locally.
 * <p>
 * Also, strictly speaking, we don't need to delete the timer when the email address is confirmed. If we don't delete it and the timer fires,
 * the UniqueEmailEntity will just ignore the message. But it is a good practice to clean up obsolete times and save resources.
 */
@ComponentId("unique-email-subscriber")
@Consume.FromKeyValueEntity(UniqueEmailEntity.class)
public class UniqueEmailConsumer extends Consumer {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private final ComponentClient client;
  private final Config config;

  public UniqueEmailConsumer(ComponentClient client, Config config) {
    this.client = client;
    this.config = config;
  }

  public Effect onChange(UniqueEmail email) {

    logger.info("Received update for address '{}'", email);
    var timerId = "timer-" + email.address();

    if (email.isReserved()) {
      // by default the timer will fire after 2h (see settings in src/resources/application.conf)
      // but we can override these settings using a -D argument
      // for example, calling: mvn compile exec:java -Demail.confirmation.timeout=10s will make the timer fire after 10 seconds
      Duration delay = config.getDuration("email.confirmation.timeout");
      logger.info("Email is not confirmed, scheduling timer '{}' to fire in '{}'", timerId, delay);
      var callToUnReserve =
        client
          .forKeyValueEntity(email.address())
          .method(UniqueEmailEntity::cancelReservation).deferred();

      timers().createSingleTimer(
        timerId,
        delay,
        callToUnReserve);

      return effects().done();

    } else if (email.isConfirmed()) {
      logger.info("Email is already confirmed, deleting timer (if exists) '{}'", timerId);
      timers().delete(timerId);
      return effects().done();

    } else {
      // Email is not reserved, so we don't need to do anything
      return effects().done();
    }
  }

}
