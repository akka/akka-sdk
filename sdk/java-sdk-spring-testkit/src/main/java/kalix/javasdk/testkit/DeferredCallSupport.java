/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

 package kalix.javasdk.testkit;
 
import com.google.protobuf.any.Any;
import kalix.javasdk.DeferredCall;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.time.temporal.ChronoUnit.SECONDS;

public class DeferredCallSupport {


  static private final Duration defaultTimeout = Duration.of(10, SECONDS);

  static public  <T> T execute(DeferredCall<Any, T> deferredCall) {
    return execute(deferredCall, defaultTimeout);
  }

  static public  <T> T execute(DeferredCall<Any, T> deferredCall, Duration timeout) {
    try {
      return deferredCall.execute().toCompletableFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  static public <T>  Exception failedExec(DeferredCall<Any, T> deferredCall) {
    try {
      deferredCall.execute().toCompletableFuture().get(defaultTimeout.toMillis(), TimeUnit.MILLISECONDS);
      throw new RuntimeException("Expected call to fail but it succeeded");
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      return e;
    }
  }

}