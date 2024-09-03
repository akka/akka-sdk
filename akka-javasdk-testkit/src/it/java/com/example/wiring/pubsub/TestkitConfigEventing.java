/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.pubsub;

import akka.javasdk.testkit.TestKit;

import static com.example.wiring.pubsub.PublishESToTopic.COUNTER_EVENTS_TOPIC;
import static com.example.wiring.pubsub.PublishVEToTopic.CUSTOMERS_TOPIC;
import static java.time.Duration.ofMillis;

public class TestkitConfigEventing {

  public TestKit.Settings settingsMockedDestination() {
    // here only to show how to set different `Settings` in a test. See SpringSdkIntegrationTest.java
    return TestKit.Settings.DEFAULT
        .withAclEnabled()
        .withAdvancedViews()
        .withWorkflowTickInterval(ofMillis(500))
        .withTopicOutgoingMessages(CUSTOMERS_TOPIC);
  }

  public TestKit.Settings settingsMockedSubscription() {
    // here only to show how to set different `Settings` in a test. See SpringSdkIntegrationTest.java
    return TestKit.Settings.DEFAULT
        .withAclEnabled()
        .withAdvancedViews()
        .withWorkflowTickInterval(ofMillis(500))
        .withTopicIncomingMessages(COUNTER_EVENTS_TOPIC)
        .withKeyValueEntityIncomingMessages("user");
  }
}
