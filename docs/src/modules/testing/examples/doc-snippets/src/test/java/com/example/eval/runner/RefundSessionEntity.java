package com.example.eval.runner;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;

/**
 * Stands in for the project's own component, so the campaign has a state to arrange.
 *
 * <p>A scenario's precursor names a state; something in the service has to be able to put
 * it there. What that is belongs to the project, and the adapter only has to reach it.
 */
@Component(id = "refund-session")
public class RefundSessionEntity extends KeyValueEntity<String> {

  public Effect<String> signIn() {
    return effects().updateState("signed-in").thenReply(commandContext().entityId());
  }
}
