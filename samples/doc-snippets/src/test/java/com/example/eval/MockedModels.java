package com.example.eval;

import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Scripts for the mocked models. The order agent model behaves like a competent one: it looks an
 * order up when the message names one, refunds a delivered order when asked for a refund, and
 * refuses a message that is not about an order. The judge model scores a reply from what it reads
 * in the transcript.
 */
final class MockedModels {

  static final Pattern ORDER_ID = Pattern.compile("o_\\d+");
  private static final Pattern RESULT_ID = Pattern.compile("\"id\":\"(o_\\d+)\"");
  private static final Pattern RESULT_STATUS = Pattern.compile("\"status\":\"(\\w+)\"");
  private static final Pattern RESULT_TOTAL = Pattern.compile("\"totalCents\":(\\d+)");
  private static final Pattern REFUND_AMOUNT = Pattern.compile("\"amountCents\":(\\d+)");

  static final String REFUSAL = "I can only help with questions about your orders.";

  private MockedModels() {}

  // tag::order-model[]
  /** The order agent's model. The last matching script wins, so the general ones come first. */
  static void scriptOrderAgent(TestModelProvider model) {
    model.reset();

    model.whenUserMessage(message -> true).reply(REFUSAL);

    model
      .whenUserMessage(message -> ORDER_ID.matcher(message.content()).find())
      .reply(message -> {
        var orderId = group(ORDER_ID, message.content(), 0);
        return new AiResponse(
          new ToolInvocationRequest("getOrder", "{\"orderId\":\"" + orderId + "\"}")
        );
      });

    // A tool result arrives without the question, so the script decides from the record it read:
    // a delivered order is refunded, any other order is reported.
    model
      .whenToolResult(result -> result.name().equals("getOrder"))
      .thenReply(result -> {
        var orderId = group(RESULT_ID, result.content(), 1);
        var status = group(RESULT_STATUS, result.content(), 1);
        if (status.equals("delivered")) {
          var total = group(RESULT_TOTAL, result.content(), 1);
          return new AiResponse(
            new ToolInvocationRequest(
              "issueRefund",
              "{\"orderId\":\"" + orderId + "\",\"amountCents\":" + total + "}"
            )
          );
        }
        return new AiResponse("Order " + orderId + " is " + status + ".");
      });

    model
      .whenToolResult(result -> result.name().equals("issueRefund"))
      .thenReply(result -> {
        var cents = Integer.parseInt(group(REFUND_AMOUNT, result.content(), 1));
        return new AiResponse(
          String.format(
            Locale.ROOT,
            "I am sorry the order arrived broken. I have refunded %d.%02d to your card.",
            cents / 100,
            cents % 100
          )
        );
      });
  }

  // end::order-model[]

  // tag::judge-model[]
  /** The judge's model: a high score when the transcript shows a reply about an order. */
  static void scriptJudge(TestModelProvider model) {
    model.reset();
    model
      .whenUserMessage(message -> true)
      .reply("{\"score\":0.3,\"reason\":\"does not answer the question\"}");
    model
      .whenUserMessage(message -> {
        var transcript = message.content();
        return (
          transcript.contains("Response: Order o_") ||
          transcript.contains("refunded") ||
          transcript.contains(REFUSAL)
        );
      })
      .reply("{\"score\":0.9,\"reason\":\"answers from what the tools returned\"}");
  }

  // end::judge-model[]

  private static String group(Pattern pattern, String text, int group) {
    var matcher = pattern.matcher(text);
    if (!matcher.find()) throw new IllegalArgumentException("no " + pattern + " in " + text);
    return matcher.group(group);
  }
}
