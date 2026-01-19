package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

import java.util.stream.Collectors;


@Component(id = "summarizer-agent")
public class SummarizerAgent extends Agent {

  private static final String SYSTEM_MESSAGE = """
      You will receive the original query and a message generate by different other agents.

      Your task is to build a new message using the message provided by the other agents.
      You are not allowed to add any new information, you should only re-phrase it to make
      them part of coherent message.

      The message to summarize will be provided between single quotes.

      ORIGINAL USER QUERY:
      
      %s
    """;


  public StreamEffect summarize(String request) {
    return streamEffects()
      .systemMessage(SYSTEM_MESSAGE)
      .userMessage(request)
      .thenReply();
  }
}

