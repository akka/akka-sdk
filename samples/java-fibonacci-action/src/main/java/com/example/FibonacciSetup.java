package com.example;

import akka.platform.javasdk.DependencyProvider;
import akka.platform.javasdk.ServiceSetup;
import com.example.fibonacci.FibonacciAction;
import akka.platform.javasdk.ServiceLifecycle;
import akka.platform.javasdk.annotations.Acl;
import akka.platform.javasdk.annotations.PlatformServiceSetup;
import akka.platform.javasdk.client.ComponentClient;
import akka.platform.javasdk.timer.TimerScheduler;
import com.example.fibonacci.RequestValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@PlatformServiceSetup
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
    public ServiceLifecycle serviceLifecycle() {
        return new ServiceLifecycle() {

            @Override
            public void onStartup() {
                //TODO revert that after introducing TimedAction
//                logger.info("Fibonacci service started");
//                componentClient.forAction()
//                  .method(FibonacciAction::getNumber)
//                  .invokeAsync(5L)
//                  .thenAccept(number ->
//                    logger.info("Action call during startup, result: {}", number)
//                  );
//                timerScheduler.startSingleTimer("fibonacci-in-the-future", Duration.ofSeconds(10),
//                  componentClient.forAction()
//                    .method(FibonacciAction::getNumber)
//                    .deferred(5L));
            }
        };
    }

    @Override
    public DependencyProvider createDependencyProvider() {
        return DependencyProvider.single(new MyContext(new RequestValidator()));
    }

}
