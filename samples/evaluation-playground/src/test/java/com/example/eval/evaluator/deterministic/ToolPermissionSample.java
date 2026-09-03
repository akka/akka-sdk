package com.example.eval.evaluator.deterministic;

// tag::imports[]
import akka.evalkit.core.metric.Finding;
import akka.evalkit.core.metric.ToolPermission;

import java.util.List;
// end::imports[]

/**
 * Reads whether the agent called only tools the eval case allows. The evaluator produces one
 * finding per tool the agent called, then aggregates into a share the report reads.
 */
public class ToolPermissionSample {

    // tag::evaluator[]
    public double score(List<String> toolsCalled) {
        var evaluator = ToolPermission.allowing("search_kb", "get_order"); // <1>

        List<Finding> findings = evaluator.judge(toolsCalled); // <2>

        return evaluator.aggregate(findings); // <3>
    }
    // end::evaluator[]

    // tag::strict[]
    public double scoreStrict(List<String> toolsCalled) {
        var evaluator = ToolPermission.allowing("search_kb", "get_order")
            .strict(); // <1>

        return evaluator.aggregate(evaluator.judge(toolsCalled)); // <2>
    }
    // end::strict[]

    // tag::deny[]
    public double scoreWithDenyList(List<String> toolsCalled) {
        var evaluator = ToolPermission.allowing("search_kb", "get_order")
            .butNot("delete_order"); // <1>

        return evaluator.aggregate(evaluator.judge(toolsCalled));
    }
    // end::deny[]
}
