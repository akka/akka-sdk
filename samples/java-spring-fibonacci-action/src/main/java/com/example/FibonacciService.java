package com.example;

import com.example.fibonacci.FibonacciAction;
import kalix.javasdk.ServiceLifecycle;
import kalix.javasdk.annotations.Acl;
import kalix.javasdk.annotations.KalixService;
import kalix.javasdk.client.ComponentClient;
import kalix.javasdk.timer.TimerScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@KalixService
// NOTE: This default ACL settings is very permissive as it allows any traffic from the internet.
// Our samples default to this permissive configuration to allow users to easily try it out.
// However, this configuration is not intended to be reproduced in production environments.
// Documentation at https://docs.kalix.io/java/access-control.html
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class FibonacciService implements ServiceLifecycle {

    private final Logger logger = LoggerFactory.getLogger(FibonacciService.class);

    private final ComponentClient componentClient;
    private final TimerScheduler timerScheduler;

    // we can optionally inject these for scheduling or initial calls in onStartup
    public FibonacciService(ComponentClient componentClient, TimerScheduler timerScheduler) {
        this.componentClient = componentClient;
        this.timerScheduler = timerScheduler;
    }

    @Override
    public void onStartup() {
        logger.info("Fibonacci service started");
        componentClient.forAction()
                .method(FibonacciAction::getNumber)
                .invokeAsync(5L)
                .thenAccept(number ->
                        logger.info("Action call during startup, result: {}", number)
                );
        timerScheduler.startSingleTimer("fibonacci-in-the-future", Duration.ofSeconds(10),
                componentClient.forAction()
                        .method(FibonacciAction::getNumber)
                        .deferred(5L));
    }
}