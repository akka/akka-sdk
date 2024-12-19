package com.example;

import akka.Done;
import com.example.application.EmailSender;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Profile("test")
public class TestEmailSender implements EmailSender {

  private List<String> sentEmails = new CopyOnWriteArrayList<>();

  @Override
  public CompletionStage<Done> send(String email) {
    sentEmails.add(email);
    return CompletableFuture.completedFuture(Done.getInstance());
  }

  public List<String> getSentEmails() {
    return sentEmails;
  }
}
