/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.keyvalueentity;

import akka.javasdk.annotations.Migration;

@Migration(TestVEState2Migration.class)
public record TestVEState2(String s, int i, String newValue) {}
