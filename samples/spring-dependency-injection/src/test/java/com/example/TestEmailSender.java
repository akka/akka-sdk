package com.example;

import com.example.application.EmailSender;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class TestEmailSender implements EmailSender {

  private final List<String> sentEmails = new CopyOnWriteArrayList<>();

  @Override
  public void send(String email) {
    sentEmails.add(email);
  }

  public List<String> getSentEmails() {
    return sentEmails;
  }
}
