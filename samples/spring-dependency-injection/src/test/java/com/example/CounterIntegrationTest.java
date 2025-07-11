package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import com.example.application.EmailSender;
import com.example.domain.Clock;
import com.example.domain.Counter;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class CounterIntegrationTest extends TestKitSupport {

  @Test
  public void verifyIfEmailWasSent() {
    FixedClock fixedClock = (FixedClock) getDependency(Clock.class);
    TestEmailSender emailSender = (TestEmailSender) getDependency(EmailSender.class);

    fixedClock.setNow(LocalDateTime.now().withHour(13));

    var counterClient = componentClient.forEventSourcedEntity("001");

    // increase counter (from 0 to 10)
    counterClient.method(Counter::increase).invoke(10);

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        assertThat(emailSender.getSentEmails()).contains("Counter [001] value is: 10");
      });
  }
}
