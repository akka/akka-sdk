/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;

@Subscribe.Topic("topic")
public class ActionSubscriber extends Action {


}
