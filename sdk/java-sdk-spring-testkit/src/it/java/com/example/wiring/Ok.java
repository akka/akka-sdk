/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring;

public record Ok() {
   public static Ok instance = new Ok();
}
