/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.Consume;

@Consume.FromTopic("topic")
public class ActionSubscriber extends Action {


}
