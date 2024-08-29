/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.pubsub;

import akka.javasdk.testkit.AkkaSdkTestKit;

import static com.example.wiring.pubsub.PublishESToTopic.COUNTER_EVENTS_TOPIC;
import static com.example.wiring.pubsub.PublishVEToTopic.CUSTOMERS_TOPIC;
import static java.time.Duration.ofMillis;

public class TestkitConfigEventing {

  public AkkaSdkTestKit.Settings settingsMockedDestination() {
    // here only to show how to set different `Settings` in a test. See SpringSdkIntegrationTest.java
    return AkkaSdkTestKit.Settings.DEFAULT
        .withAclEnabled()
        .withAdvancedViews()
        .withWorkflowTickInterval(ofMillis(500))
        .withTopicOutgoingMessages(CUSTOMERS_TOPIC);
  }

  public AkkaSdkTestKit.Settings settingsMockedSubscription() {
    // here only to show how to set different `Settings` in a test. See SpringSdkIntegrationTest.java
    return AkkaSdkTestKit.Settings.DEFAULT
        .withAclEnabled()
        .withAdvancedViews()
        .withWorkflowTickInterval(ofMillis(500))
        .withTopicIncomingMessages(COUNTER_EVENTS_TOPIC)
        .withKeyValueEntityIncomingMessages("user");
  }
}
