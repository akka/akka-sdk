package com.example.tracing.application;


import akka.javasdk.annotations.ComponentId;
import akka.javasdk.timedaction.TimedAction;
import com.example.tracing.domain.Typicode;
import io.opentelemetry.api.trace.StatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("tracing-action")
public class TracingAction extends TimedAction {

    private final static Logger logger = LoggerFactory.getLogger(TracingAction.class);

    private final Typicode typicode = new Typicode();

    public Effect callAnotherService(String postID){
        logger.info("Calling to [{}].", Typicode.url + "/" + postID);
        var newSpan = commandContext().getTracer()
                .spanBuilder("ad-hoc span calling to: " + Typicode.url)
                .setParent(commandContext().metadata().traceContext().asOpenTelemetryContext())
                .startSpan()
                .setAttribute("post", postID);

        return effects().asyncEffect(
                typicode.callAsyncService(postID)
                        .whenComplete((response, ex) -> {
                            if (ex != null) {
                                newSpan.setStatus(StatusCode.ERROR, ex.getMessage()).end();
                            } else {
                                newSpan.setAttribute("result", response.body().title()).end();
                            }
                        })
                        .thenApply(__ -> effects().done() ));
    }
}