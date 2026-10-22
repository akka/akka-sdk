package com.example.multiregion;

import akka.Done;
import java.util.concurrent.CompletionStage;

/** A shared external payment destination, supplied through dependency injection. */
public interface PaymentProvider {
  /**
   * Atomically deduplicates concurrent and repeated requests by operation ID. Reusing an ID with
   * different payment details must fail. Successful completion confirms a durable payment result;
   * a lost response must be safe to retry with the same ID.
   */
  CompletionStage<Done> charge(String operationId, String orderId, long amountInCents);
}
