/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import static akka.javasdk.testkit.EntitySerializationChecker.verifySerDerWithExpectedType;

import akka.javasdk.Metadata;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.impl.client.MethodRefResolver;
import akka.javasdk.testkit.impl.ConsumerResultImpl;
import akka.javasdk.testkit.impl.TestKitMessageContext;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Consumer Testkit for use in unit tests for Consumers.
 *
 * <p>To test a Consumer create a testkit instance by calling {@code ConsumerTestKit.of}. The
 * returned testkit can be used as many times as you want. It doesn't preserve any state between
 * invocations.
 *
 * <p>Use the {@code method} methods to interact with the testkit.
 */
public class ConsumerTestKit<C extends Consumer> {

  private final Supplier<C> consumerFactory;

  private ConsumerTestKit(Supplier<C> consumerFactory) {
    this.consumerFactory = consumerFactory;
  }

  public static <C extends Consumer> ConsumerTestKit<C> of(Supplier<C> consumerFactory) {
    return new ConsumerTestKit<>(consumerFactory);
  }

  public final class MethodRef {
    private final akka.japi.function.Function<C, Consumer.Effect> func;
    private final Metadata metadata;

    public MethodRef(akka.japi.function.Function<C, Consumer.Effect> func, Metadata metadata) {
      this.func = func;
      this.metadata = metadata;
    }

    public MethodRef withMetadata(Metadata metadata) {
      return new MethodRef(func, metadata);
    }

    public ConsumerResult invoke() {
      return ConsumerTestKit.this.call(func, metadata);
    }
  }

  public final class MethodRef1<I> {
    private final akka.japi.function.Function2<C, I, Consumer.Effect> func;
    private final Metadata metadata;

    public MethodRef1(akka.japi.function.Function2<C, I, Consumer.Effect> func, Metadata metadata) {
      this.func = func;
      this.metadata = metadata;
    }

    public MethodRef1<I> withMetadata(Metadata metadata) {
      return new MethodRef1<>(func, metadata);
    }

    public ConsumerResult invoke(I input) {
      var method = MethodRefResolver.resolveMethodRef(func);
      var inputType = method.getParameterTypes()[0];
      C consumer = consumerFactory.get();

      verifySerDerWithExpectedType(inputType, input, consumer);
      return ConsumerTestKit.this.call(
          c -> {
            try {
              return func.apply(c, input);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          },
          metadata);
    }
  }

  /**
   * Pass in a Consumer message handler method reference without parameters, e.g. {@code
   * MyConsumer::onEvent}
   */
  public MethodRef method(akka.japi.function.Function<C, Consumer.Effect> func) {
    return new MethodRef(func, Metadata.EMPTY);
  }

  /**
   * Pass in a Consumer message handler method reference with a single parameter, e.g. {@code
   * MyConsumer::onEvent}
   */
  public <I> MethodRef1<I> method(akka.japi.function.Function2<C, I, Consumer.Effect> func) {
    return new MethodRef1<>(func, Metadata.EMPTY);
  }

  private ConsumerResult call(
      akka.japi.function.Function<C, Consumer.Effect> func, Metadata metadata) {
    TestKitMessageContext context = new TestKitMessageContext(metadata);
    C consumer = consumerFactory.get();
    consumer._internalSetMessageContext(Optional.of(context));
    try {
      Consumer.Effect effect = func.apply(consumer);
      return new ConsumerResultImpl(effect);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
