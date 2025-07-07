/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components;

import akka.javasdk.UserException;

public class MyException extends UserException {

  public record SomeData(String info) {}

  private final SomeData data;

  public MyException(String message, SomeData data) {
    super(message);
    this.data = data;
  }

  public SomeData getData() {
    return data;
  }
}
