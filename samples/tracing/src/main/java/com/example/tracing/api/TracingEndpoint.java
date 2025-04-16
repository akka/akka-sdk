package com.example.tracing.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import akka.javasdk.http.RequestContext;
import akka.javasdk.timer.TimerScheduler;
import com.example.tracing.application.TracingAction;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/tracing")
public class TracingEndpoint {

    private final ComponentClient componentClient;
    private final TimerScheduler timerScheduler;
    private final RequestContext requestContext;

    public TracingEndpoint(ComponentClient componentClient, TimerScheduler timerScheduler, RequestContext requestContext) {
        this.componentClient = componentClient;
        this.timerScheduler = timerScheduler;
        this.requestContext = requestContext;
    }

    private record PostId(String id){}


    @Post("/")
    public void postDelayed(PostId id) {
        timerScheduler.createSingleTimer(
            UUID.randomUUID().toString(), //not planning to cancel the timer
            Duration.ofSeconds(1L),
            componentClient.forTimedAction().method(TracingAction::callAnotherService).deferred(id.id));
    }

    @Post("/custom/{id}")
    public CompletionStage<HttpResponse> customSpan(String id) {
        Optional<Span> maybeSpan = requestContext.tracing().startSpan("ad-hoc endpoint span");

        maybeSpan.ifPresent(span -> span.setAttribute("id", id));

        // do some stuff
        // potentially async, might throw
        var result = CompletableFuture.supplyAsync(() -> {
            maybeSpan.ifPresent(span -> span.addEvent("Spawned async task"));
            return Integer.valueOf(id);
        });

        return result.handle((ok, error) -> {
            if (error != null) {
                maybeSpan.ifPresent(span -> {
                    span.setStatus(StatusCode.ERROR, error.getMessage());
                    span.end();
                });
                return HttpResponses.internalServerError("Boom");
            } else {
                maybeSpan.ifPresent(span -> {
                    span.setStatus(StatusCode.OK);
                    span.end();
                });
                return HttpResponses.ok("Ok!");
            }
        });
    }

}
