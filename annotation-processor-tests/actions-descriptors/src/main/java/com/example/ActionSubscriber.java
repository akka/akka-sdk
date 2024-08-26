/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.platform.javasdk.timedaction.TimedAction;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.annotations.Consume;

@ComponentId("action-subscriber")
@Consume.FromTopic("topic")
public class ActionSubscriber extends TimedAction {


}
