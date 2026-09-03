package com.example.eval.evaluator.deterministic;

// tag::imports[]
import akka.evalkit.core.domain.RunOutcome;
import akka.evalkit.core.metric.ToolPermission;

import java.util.List;
// end::imports[]

/**
 * Reads whether the agent called only tools the eval case allows. The evaluator produces one
 * finding per tool the agent called, then aggregates into a share the report reads.
 */
public class ToolPermissionSample {

    // tag::evaluator[]
    public RunOutcome score(List<String> toolsCalled) {
        var evaluator = ToolPermission.allowing("search_kb", "get_order"); // <1>

        var findings = evaluator.judge(toolsCalled); // <2>

        return evaluator.outcome(findings); // <3>
    }
    // end::evaluator[]

    // tag::strict[]
    public RunOutcome scoreStrict(List<String> toolsCalled) {
        var evaluator = ToolPermission.allowing("search_kb", "get_order")
            .strict(); // <1>

        return evaluator.outcome(evaluator.judge(toolsCalled)); // <2>
    }
    // end::strict[]

    // tag::deny[]
    public RunOutcome scoreWithDenyList(List<String> toolsCalled) {
        var evaluator = ToolPermission.allowing("search_kb", "get_order")
            .butNot("delete_order"); // <1>

        return evaluator.outcome(evaluator.judge(toolsCalled));
    }
    // end::deny[]
}
