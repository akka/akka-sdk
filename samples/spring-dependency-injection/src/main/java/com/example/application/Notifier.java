package com.example.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import com.example.domain.Counter;
import com.example.domain.CounterEvent.ValueIncreased;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "notifier")
@Consume.FromEventSourcedEntity(value = Counter.class, ignoreUnknown = true)
public class Notifier extends Consumer {

  private final Logger logger = LoggerFactory.getLogger(Notifier.class);
  private final EmailSender emailSender;
  private final EmailComposer emailComposer;

  public Notifier(EmailSender emailSender, EmailComposer emailComposer1) {
    this.emailSender = emailSender;
    this.emailComposer = emailComposer1;
  }

  public Effect onIncrease(ValueIncreased event) {
    String counterId = messageContext().eventSubject().orElseThrow();
    logger.info("Received increased event: {} (msg ce id {})", event.toString(), counterId);
    var email = emailComposer.composeEmail(counterId);
    emailSender.send(email);
    return effects().done();
  }
}
