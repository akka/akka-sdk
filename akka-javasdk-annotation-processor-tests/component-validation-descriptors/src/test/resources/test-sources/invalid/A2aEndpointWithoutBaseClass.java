/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.a2a.A2aEndpoint;

@A2aEndpoint(name = "Test", description = "A test agent", version = "1.0.0")
public class A2aEndpointWithoutBaseClass {}
