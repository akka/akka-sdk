package com.example.infrastructure;

import akka.Done;
import com.example.application.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Service
@Profile("prod")
public class DefaultEmailSender implements EmailSender {
  private Logger logger = LoggerFactory.getLogger(DefaultEmailSender.class);

  @Override
  public CompletionStage<Done> send(String email) {
    logger.info("Sending email: {}", email);
    return CompletableFuture.completedFuture(Done.getInstance());
  }
}
