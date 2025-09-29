/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.timedaction.TimedAction;

@Component(id = "some-timed-action")
public class SomeTimedAction extends TimedAction {}
