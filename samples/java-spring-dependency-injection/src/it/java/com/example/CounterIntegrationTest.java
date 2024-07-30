package com.example;

import akka.platform.spring.testkit.KalixIntegrationTestKitSupport;
import com.example.application.EmailSender;
import com.example.domain.Clock;
import com.example.domain.Counter;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class CounterIntegrationTest extends KalixIntegrationTestKitSupport {

  @Test
  public void verifyIfEmailWasSent() {

    FixedClock fixedClock = (FixedClock) getDependency(Clock.class);
    TestEmailSender emailSender = (TestEmailSender) getDependency(EmailSender.class);

    fixedClock.setNow(LocalDateTime.now().withHour(13));

    var counterClient = componentClient.forEventSourcedEntity("001");

    // increase counter (from 0 to 10)
    counterClient
      .method(Counter::increase)
      .invokeAsync(10);

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        assertThat(emailSender.getSentEmails()).contains("Counter [001] value is: 10");
      });
  }
}
