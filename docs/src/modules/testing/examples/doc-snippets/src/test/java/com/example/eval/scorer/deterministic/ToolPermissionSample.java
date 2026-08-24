package com.example.eval.scorer.deterministic;

// tag::imports[]
import akka.evalkit.core.domain.RunOutcome;
import akka.evalkit.core.metric.ToolPermission;

import java.util.List;
// end::imports[]

/**
 * Reads whether the agent called only tools the scenario allows. The scorer produces one
 * finding per tool the agent called, then aggregates into a share the report reads.
 */
public class ToolPermissionSample {

    // tag::scorer[]
    public RunOutcome score(List<String> toolsCalled) {
        var scorer = ToolPermission.allowing("search_kb", "get_order"); // <1>

        var findings = scorer.judge(toolsCalled); // <2>

        return scorer.outcome(findings); // <3>
    }
    // end::scorer[]

    // tag::strict[]
    public RunOutcome scoreStrict(List<String> toolsCalled) {
        var scorer = ToolPermission.allowing("search_kb", "get_order")
            .strict(); // <1>

        return scorer.outcome(scorer.judge(toolsCalled)); // <2>
    }
    // end::strict[]

    // tag::deny[]
    public RunOutcome scoreWithDenyList(List<String> toolsCalled) {
        var scorer = ToolPermission.allowing("search_kb", "get_order")
            .butNot("delete_order"); // <1>

        return scorer.outcome(scorer.judge(toolsCalled));
    }
    // end::deny[]
}
