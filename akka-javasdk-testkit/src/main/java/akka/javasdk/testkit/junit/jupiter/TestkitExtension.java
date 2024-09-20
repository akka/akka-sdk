/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.junit.jupiter;

import akka.actor.typed.ActorSystem;
import akka.stream.Materializer;
import akka.javasdk.testkit.EventingTestKit;
import akka.javasdk.testkit.EventingTestKit.IncomingMessages;
import akka.javasdk.testkit.EventingTestKit.OutgoingMessages;
import akka.javasdk.testkit.TestKit;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * A JUnit 5 "Jupiter" Extension for {@link TestKit}, which automatically manages the lifecycle of
 * the testkit. The testkit will be automatically stopped when the test completes or fails.
 */
public final class TestkitExtension implements BeforeAllCallback, AfterAllCallback {

  private final TestKit testKit;

  public TestkitExtension() {
    this.testKit = new TestKit();
  }


  public TestkitExtension(TestKit.Settings settings) {
    this.testKit = new TestKit(settings);
  }

  /**
   * JUnit5 support - extension based
   */
  @Override
  public void afterAll(ExtensionContext extensionContext) throws Exception {
    testKit.stop();
  }

  /**
   * JUnit5 support - extension based
   */
  @Override
  public void beforeAll(ExtensionContext extensionContext) throws Exception {
    testKit.start();
  }


  /**
   * Get incoming messages for ValueEntity.
   *
   * @param typeId @TypeId or entity_type of the ValueEntity (depending on the used SDK)
   */
  public IncomingMessages getValueEntityIncomingMessages(String typeId) {
    return testKit.getKeyValueEntityIncomingMessages(typeId);
  }

  /**
   * Get incoming messages for EventSourcedEntity.
   *
   * @param typeId @TypeId or entity_type of the EventSourcedEntity (depending on the used SDK)
   */
  public IncomingMessages getEventSourcedEntityIncomingMessages(String typeId) {
    return testKit.getEventSourcedEntityIncomingMessages(typeId);
  }

  /**
   * Get incoming messages for Stream (eventing.in.direct in case of protobuf SDKs).
   *
   * @param service  service name
   * @param streamId service stream id
   */
  public IncomingMessages getStreamIncomingMessages(String service, String streamId) {
    return testKit.getStreamIncomingMessages(service, streamId);
  }

  /**
   * Get incoming messages for Topic.
   *
   * @param topic topic name
   */
  public IncomingMessages getTopicIncomingMessages(String topic) {
    return testKit.getTopicIncomingMessages(topic);
  }

  /**
   * Get mocked topic destination.
   *
   * @param topic topic name
   */
  public OutgoingMessages getTopicOutgoingMessages(String topic) {
    return testKit.getTopicOutgoingMessages(topic);
  }

  /**
   * Returns {@link EventingTestKit.MessageBuilder} utility
   * to create {@link EventingTestKit.Message}s for the eventing testkit.
   */
  public EventingTestKit.MessageBuilder getMessageBuilder() {
    return testKit.getMessageBuilder();
  }

  /**
   * Get the host name/IP address where the service is available. This is relevant in certain
   * Continuous Integration environments.
   */
  public String getHost() {
    return testKit.getHost();
  }

  /**
   * Get the local port where the Kalix service is available.
   */
  public int getPort() {
    return testKit.getPort();
  }

  /**
   * An Akka Stream materializer to use for running streams. Needed for example in a command handler
   * which accepts streaming elements but returns a single async reply once all streamed elements
   * has been consumed.
   */
  public Materializer getMaterializer() {
    return testKit.getMaterializer();
  }

  /**
   * Get an {@link ActorSystem} for creating Akka HTTP clients.
   *
   * @return test actor system
   */
  public ActorSystem<?> getActorSystem() {
    return testKit.getActorSystem();
  }


}
