package com.example.trigger;

import kalix.javasdk.ServiceLifecycle;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Trigger;
import org.springframework.web.bind.annotation.PostMapping;


// tag::hook[]
// @KalixService (only one per service)
public class OnStartupAction implements ServiceLifecycle { // <1>

  @Override
  public void onStartup() {
    // Do some initial operations here
  }

}
// end::hook[]
