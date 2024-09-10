package com.example.tracing.api;

import akka.Done;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import com.example.tracing.application.TracingAction;
import akka.javasdk.timer.TimerScheduler;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

@HttpEndpoint("/tracing")
public class TracingEndpoint {

    private final ComponentClient componentClient;
    private final TimerScheduler timerScheduler;


    public TracingEndpoint(ComponentClient componentClient, TimerScheduler timerScheduler) {
        this.componentClient = componentClient;
        this.timerScheduler = timerScheduler;
    }

    private record PostId(String id){}


    @Post("/")
    public CompletionStage<Done> postDelayed(PostId id) {
        return timerScheduler.startSingleTimer(
                UUID.randomUUID().toString(), //not planning to cancel the timer
                Duration.ofSeconds(1L),
                componentClient.forTimedAction().method(TracingAction::callAnotherService).deferred(id.id)
        );
    }

}
