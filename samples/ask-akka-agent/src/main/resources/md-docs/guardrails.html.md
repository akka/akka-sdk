<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Components](../components/index.html)
- [Agents](../agents.html)
- [Guardrails](guardrails.html)

<!-- </nav> -->

# Guardrails

Guardrails can protect against harmful inputs, such as jailbreak attempts, and damaging output, such as mentions of a competitor’s product.

A specific guardrail implements the <a href="_attachments/api/akka/javasdk/agent/TextGuardrail.html">`TextGuardrail` interface</a>. It takes the input or output text as a parameter and a result if it passed the validation or not, including an explanation of why the decision was made. These results are included in metrics and traces. A guardrail can abort the interaction with the model, or only report the problem and continue anyway.

An example of a `Guardrail` implementation:

[ToxicGuard.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/guardrail/ToxicGuard.java)
```java
import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.TextGuardrail;

public class ToxicGuard implements TextGuardrail {

  private final String searchFor;

  public ToxicGuard(GuardrailContext context) {
    searchFor = context.config().getString("search-for");
  }

  @Override
  public Result evaluate(String text) {
    // this would typically be more advanced in a real implementation
    if (text.contains(searchFor)) {
      return new Result(false, "Toxic response '%s' not allowed.".formatted(searchFor));
    } else {
      return Result.OK;
    }
  }
}
```
Guardrails are enabled by configuration, to be able to enforce at deployment time that certain guardrails are always used.

src/main/resources/application.conf
```conf
akka.javasdk.agent.guardrails {
  "pii guard" {                                     // (1)
    class = "com.example.guardrail.PiiGuard"        // (2)
    agents = ["planner-agent"]                      // (3)
    agent-roles = ["worker"]                        // (4)
    category = PII                                  // (5)
    use-for = ["model-request", "mcp-tool-request"] // (6)
    report-only = false                             // (7)
  }

  "toxic guard" {
    class = "com.example.guardrail.ToxicGuard"
    agent-roles = ["worker"]
    category = TOXIC
    use-for = ["model-response", "mcp-tool-response"]
    report-only = false
    search-for = "bad stuff"
  }
}
```

| **1** | Each configured guardrail has a unique name. |
| **2** | Implementation class of the guardrail. |
| **3** | Enable this guardrail for agents with these component ids. |
| **4** | Enable this guardrail for agents with these roles. |
| **5** | The type of validation, such as PII and TOXIC. |
| **6** | Where to use the guardrail, such as for the model request or model response. |
| **7** | If it didn’t pass the evaluation criteria, the execution can either be aborted or continue anyway. In both cases, the result is tracked in logs, metrics and traces. |
The implementation class of the guardrail is configured with the `class` property. The class must implement the <a href="_attachments/api/akka/javasdk/agent/TextGuardrail.html">`TextGuardrail` interface</a>. The class may optionally have a constructor with a <a href="_attachments/api/akka/javasdk/agent/GuardrailContext.html">`GuardrailContext`</a> parameter, which includes the name and the config section for the specific guardrail. In above code example of the `ToxicGuard` you can see how the configuration property `search-for` is read from the configuration of the `GuardrailContext` parameter.

Agents are selected by matching `agent` or `agent-role` configuration.

- `agents`: enabled for agents with these component ids, if `agents` contain `"*"` the guardrail is enabled for all agents
- `agent-roles`: enabled for agents with these roles, if agent-roles contain `"*"` the guardrail is enabled for all agents that has a role, but not for agents without a role
If both `agents` and `agent-roles` are defined it’s enough that one of them matches to enable the guardrail for an agent.

This role is defined in the `@AgentRole` annotation.

The name and the category are reported in logs, metrics and traces. The `category` should classify the type of validation. It can be any value, but a few recommended categories are JAILBREAK, PROMPT_INJECTION, PII, TOXIC, HALLUCINATED, NSFW, FORMAT.

The guardrail can be enabled for certain inputs or outputs with the `use-for` property. The `use-for` property accepts the following values: `model-request`, `model-response`, `mcp-tool-request`, `mcp-tool-response`, and `*`.

## <a href="about:blank#_guardrail_of_similar_text"></a> Guardrail of similar text

The built-in <a href="_attachments/api/akka/javasdk/agent/SimilarityGuard.html">`SimilarityGuard`</a> evaluates the text by making a similarity search in a dataset of "bad examples". If the similarity exceeds a threshold, the result is flagged as blocked.

This is how to configure the `SimilarityGuard`:

src/main/resources/application.conf
```conf
akka.javasdk.agent.guardrails {
  "jailbreak guard" {
    class = "akka.javasdk.agent.SimilarityGuard"
    agents = ["planner-agent", "weather-agent"]
    category = JAILBREAK
    use-for = ["model-request"]
    threshold = 0.75
    bad-examples-resource-dir = "guardrail/jailbreak"
  }
}
```
Here, it’s using predefined examples of jailbreak prompts in `guardrail/jailbreak`. Those have been incorporated from [https://github.com/verazuo/jailbreak_llms](https://github.com/verazuo/jailbreak_llms/), but you can define your own examples and place in a subdirectory of `src/main/resources/`. All text files in the configured `bad-examples-resource-dir` are included in the similarity search.

This can be used for other things than jailbreak attempt detection.

<!-- <footer> -->
<!-- <nav> -->
[Orchestrating multiple agents](orchestrating.html) [LLM evaluation](llm_eval.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->