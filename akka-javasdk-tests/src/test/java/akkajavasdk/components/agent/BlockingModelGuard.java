/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import static java.util.concurrent.CompletableFuture.completedFuture;

import akka.javasdk.agent.Decision;
import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.ModelGuardrail;
import java.util.concurrent.CompletionStage;

public class BlockingModelGuard implements ModelGuardrail {
  private final String blockReason;

  public BlockingModelGuard(GuardrailContext context) {
    this.blockReason = context.config().getString("block-reason");
  }

  @Override
  public CompletionStage<Decision> decide(CallContext ctx) {
    return completedFuture(new Decision.Deny(blockReason));
  }
}
