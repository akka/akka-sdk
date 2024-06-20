/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.client;

import akka.annotation.DoNotInherit;
import akka.japi.function.Function;
import akka.japi.function.Function10;
import akka.japi.function.Function11;
import akka.japi.function.Function12;
import akka.japi.function.Function13;
import akka.japi.function.Function14;
import akka.japi.function.Function15;
import akka.japi.function.Function16;
import akka.japi.function.Function17;
import akka.japi.function.Function18;
import akka.japi.function.Function19;
import akka.japi.function.Function2;
import akka.japi.function.Function20;
import akka.japi.function.Function21;
import akka.japi.function.Function22;
import akka.japi.function.Function3;
import akka.japi.function.Function4;
import akka.japi.function.Function5;
import akka.japi.function.Function6;
import akka.japi.function.Function7;
import akka.japi.function.Function8;
import akka.japi.function.Function9;
import com.google.protobuf.any.Any;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.Metadata;
import kalix.javasdk.impl.client.MethodRefResolver;
import kalix.javasdk.impl.client.ViewCallValidator;
import kalix.spring.impl.KalixClient;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/** Not for user extension */
@DoNotInherit
public interface ViewClient {

  /**
   * Pass in a View query method reference, e.g. <code>UserByCity::find</code> If no result is
   * found, the result of the request will be a {@link NoEntryFoundException}
   */
  <T, R> NativeComponentInvokeOnlyMethodRef<R> method(Function<T, R> methodRef);

  /**
   * Pass in a View query method reference, e.g. <code>UserByCity::find</code
   *
   * If no result is found, the result of the request will be a {@link NoEntryFoundException}
   */
  <T, A1, R> NativeComponentInvokeOnlyMethodRef1<A1, R> method(Function2<T, A1, R> methodRef);
}
