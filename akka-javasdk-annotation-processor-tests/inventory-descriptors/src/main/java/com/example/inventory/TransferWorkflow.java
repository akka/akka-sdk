/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory;

import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;

@Component(id = "transfer")
public class TransferWorkflow extends Workflow<String> {}
