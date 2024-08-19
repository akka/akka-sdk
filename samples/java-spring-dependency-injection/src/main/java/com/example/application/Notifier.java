package com.example.application;

import akka.Done;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.consumer.Consumer;
import com.example.domain.Counter;
import com.example.domain.CounterEvent.ValueIncreased;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("notifier")
@Consume.FromEventSourcedEntity(value = Counter.class, ignoreUnknown = true)
public class Notifier extends Consumer {

  private Logger logger = LoggerFactory.getLogger(Notifier.class);
  private final EmailSender emailSender;
  private final EmailComposer emailComposer;

  public Notifier(EmailSender emailSender, EmailComposer emailComposer1) {
    this.emailSender = emailSender;
    this.emailComposer = emailComposer1;
  }

  public Effect onIncrease(ValueIncreased event) {
    String counterId = messageContext().eventSubject().orElseThrow();
    logger.info("Received increased event: {} (msg ce id {})", event.toString(), counterId);
    return effects().asyncProduce(
      emailComposer.composeEmail(counterId).thenCompose(
        emailSender::send));
  }
}