/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.library;

import akka.javasdk.annotations.Component;
import akka.javasdk.timedaction.TimedAction;

@Component(id = "library-action")
public class LibraryAction extends TimedAction {

  public Effect hello() {
    return effects().done();
  }
}
