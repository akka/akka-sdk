package com.example.infrastructure;

import com.example.application.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("prod")
public class DefaultEmailSender implements EmailSender {
  private final Logger logger = LoggerFactory.getLogger(DefaultEmailSender.class);

  @Override
  public void send(String email) {
    logger.info("Sending email: {}", email);
  }
}
