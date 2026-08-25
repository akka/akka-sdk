package com.example.eval.runner;

// tag::imports[]
import akka.evalkit.core.domain.Precursor;
import akka.evalkit.core.domain.SystemUnderTest;
import akka.javasdk.client.ComponentClient;

import java.util.Map;
import java.util.Optional;
// end::imports[]

/**
 * Reaches the customer's Akka SDK service by its ComponentClient. The adapter is one
 * interface — put the service in the state a scenario names, submit the graded turn,
 * return what came back.
 */
// tag::class[]
public class RefundAgentRunner implements SystemUnderTest {

    private final ComponentClient componentClient; // <1>

    public RefundAgentRunner(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Override
    public Prepared prepare(Precursor precursor) { // <2>
        if (precursor instanceof Precursor.Fixture fixture
            && fixture.name().equals("signed-in")) {
            String sessionId = componentClient
                .forKeyValueEntity("session-42")
                .method(RefundSessionEntity::signIn)
                .invoke();
            return new Prepared.Ready(sessionId, "");
        }
        return new Prepared.Failed(precursor.describe() + " cannot be arranged"); // <3>
    }

    @Override
    public Reply submit(String sessionId, String userText) { // <4>
        String answer = componentClient
            .forAgent()
            .inSession(sessionId)
            .method(RefundAgent::respond)
            .invoke(userText);
        return Reply.of(answer);
    }

    @Override
    public Map<String, String> fixtures() { // <5>
        return Map.of("signed-in", "a signed-in customer session");
    }
}
// end::class[]
