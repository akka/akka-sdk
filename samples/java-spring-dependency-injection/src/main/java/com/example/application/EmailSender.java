package com.example.application;

import akka.Done;

import java.util.concurrent.CompletionStage;

public interface EmailSender {
  CompletionStage<Done> send(String email);
}
