/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory;

import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.DurableEvaluator;
import akka.javasdk.evaluation.EvaluationContext;

@Component(id = "audit")
public class AuditEvaluator extends DurableEvaluator<String> {

  @Override
  public Effect onEvaluation(EvaluationContext context) {
    return effects().notApplicable("nothing to audit");
  }
}
