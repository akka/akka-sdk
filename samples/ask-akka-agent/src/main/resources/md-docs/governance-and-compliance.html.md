<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Use cases](index.html)
- [Governance & compliance](governance-and-compliance.html)

<!-- </nav> -->

# Governance & compliance

Enforce runtime policies, sanitize personally identifiable information (PII), and build toward EU AI Act compliance with Akka’s built-in governance capabilities. This pattern covers guardrails that validate agent inputs and outputs, sanitization pipelines that scrub sensitive data, and observability hooks for audit trails and compliance reporting.

|  | **Status: Partial** — This pattern guide describes the governance approach and available components. A dedicated governance-focused sample project is pending; a full code walkthrough will be added when that sample is available. |

## <a href="about:blank#_overview"></a> Overview

### <a href="about:blank#_when_to_use_this_pattern"></a> When to Use This Pattern

- You need to enforce content policies or safety guardrails on agent inputs and outputs
- Your application must sanitize PII before it reaches an LLM or is stored in logs
- You are building toward EU AI Act compliance and need audit trails for AI decisions
- You want human-in-the-loop verification steps for high-stakes agent actions

### <a href="about:blank#_akka_components_involved"></a> Akka Components Involved

- **Guardrails** — validate and filter agent inputs and outputs against defined policies
- **Sanitizers** — detect and redact PII and sensitive data in agent interactions
- **Agents** — integrate governance checks into the agent processing pipeline

## <a href="about:blank#_sample_projects"></a> Sample Projects

The following sample projects demonstrate aspects of this pattern:

- [medical-discharge-tagging](https://github.com/akka-samples/medical-tagging-agent) — human verification workflow for AI-generated medical classifications
- [transfer-workflow-compensation](https://github.com/akka-samples/transfer-workflow-compensation) — compensation logic demonstrating rollback and recovery controls

|  | A dedicated governance-focused sample may be needed for a full end-to-end walkthrough of this pattern. |

## <a href="about:blank#_see_also"></a> See Also

- [Guardrails](../agents/guardrails.html)
- [Data sanitization](../sanitization.html)
- [Governance & the runtime](../../concepts/governance-and-the-runtime.html)

<!-- <footer> -->
<!-- <nav> -->
[APIs & exposure](apis-and-exposure.html) [Enterprise patterns](enterprise-patterns.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->