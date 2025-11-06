/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.timedaction.TimedAction;

// This component has multiple validation errors:
// 1. Not public (package-private)
// 2. Empty component id
@Component(id = "")
class MultipleErrorsComponent extends TimedAction {
}