package com.example.fibonacci;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Setup;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timer.TimerScheduler;
import com.example.fibonacci.api.RequestValidator;
import com.example.fibonacci.application.FibonacciTimedAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@Setup
// NOTE: This default ACL settings is very permissive as it allows any traffic from the internet.
// Our samples default to this permissive configuration to allow users to easily try it out.
// However, this configuration is not intended to be reproduced in production environments.
// Documentation at https://docs.kalix.io/java/access-control.html
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class FibonacciSetup implements ServiceSetup {

    private final Logger logger = LoggerFactory.getLogger(FibonacciSetup.class);

    private final ComponentClient componentClient;
    private final TimerScheduler timerScheduler;

    // we can optionally inject these for scheduling or initial calls in onStartup
    public FibonacciSetup(ComponentClient componentClient, TimerScheduler timerScheduler) {
        this.componentClient = componentClient;
        this.timerScheduler = timerScheduler;
    }

    @Override
    public void onStartup() {
        logger.info("Fibonacci service started");
        timerScheduler.startSingleTimer("fibonacci-in-the-future", Duration.ofSeconds(10),
            componentClient.forTimedAction()
                .method(FibonacciTimedAction::calculateNextNumber)
                .deferred(5L));
    }

    @Override
    public DependencyProvider createDependencyProvider() {
        return DependencyProvider.single(new MyDependency(new RequestValidator()));
    }

}
