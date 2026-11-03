package com.example.evaluator;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id = "document-summarizer")
public class DocumentSummarizer extends Agent {

  public record Request(String document, String feedback) {}

  private static final String SYSTEM_MESSAGE =
    """
    Summarize the document. Keep the summary short, and do not add information that
    is not in the document.
    """;

  public Effect<String> summarize(Request request) {
    String userMessage = request.feedback().isEmpty()
      ? request.document()
      : "Improve your previous summary of the document. Feedback: " + request.feedback();
    return effects().systemMessage(SYSTEM_MESSAGE).userMessage(userMessage).thenReply();
  }
}
