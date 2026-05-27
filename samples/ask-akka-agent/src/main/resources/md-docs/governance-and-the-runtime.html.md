<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- [Governance & the runtime](governance-and-the-runtime.html)

<!-- </nav> -->

# Governance & the runtime

Akka embeds governance directly in the runtime — enforcing policies, producing explanations, and containing failures as they happen. This is fundamentally different from bolt-on governance tools that read logs after the fact.

## <a href="about:blank#_why_bolt_on_governance_fails"></a> Why Bolt-On Governance Fails

If governance is not inline to the runtime, you are trusting second-hand records instead of witnessing interactions as they happen. This is insufficient for any environment subject to the EU AI Act.

### <a href="about:blank#_immutable_records"></a> Immutable Records

Only a system inline to the runtime can witness and encode every interaction immutably. A log message from a third-party runtime is just a claim — there is no way to prove it was not modified, delayed, or selectively omitted.

### <a href="about:blank#_human_intervention"></a> Human Intervention

The EU AI Act requires that humans can pause, discontinue, override, review, or nudge an ongoing agentic process. A governance layer that only reads logs cannot stop or redirect a running process.

### <a href="about:blank#_authorization_capture"></a> Authorization Capture

The EU AI Act requires recording which authorizations and tools were in use at the time of every interaction. Only the runtime that executed it can produce that record authoritatively.

### <a href="about:blank#_pii_scrubbing_with_right_to_explain"></a> PII Scrubbing with Right to Explain

PII must be scrubbed, but decisions must still be explainable. If an AI rejects someone because they are too young, the decision must be explained without revealing the person’s age. Only the agentic runtime can make the decision, enforce scrubbing, and produce the explanation.

## <a href="about:blank#_how_akka_provides_built_in_governance"></a> How Akka Provides Built-In Governance

Akka’s governance is not a separate product or integration. It is a property of every deployment.

- **Runtime policy enforcement** — Guardrails, policies, LLMs-as-a-judge, and sanitizers are fully embedded within the runtime. Bad requests get stopped before they consume tokens, not after.
- **Self-explanation** — Every decision can be traced and explained, satisfying regulatory requirements for transparency.
- **Self-containment** — Failures are contained at the point of origin. A misbehaving agent cannot cascade into other parts of your system.
- **Interaction logging** — Every interaction is recorded immutably by the runtime that executed it.
- **Causal analysis** — Trace the chain of decisions that led to any outcome.

## <a href="about:blank#_compliance_certifications"></a> Compliance Certifications

Akka holds more than 19 compliance certifications including:

- EU AI Act
- Singapore Agent Framework
- ISO 42001
- SOC 2

## <a href="about:blank#_related_documentation"></a> Related Documentation

- [Guardrails](../sdk/agents/guardrails.html) — Configure runtime policy enforcement
- [Data sanitization](../sdk/sanitization.html) — PII scrubbing implementation
- [Access Control](../sdk/access-control.html) — Service-level access control lists

## <a href="about:blank#_see_also"></a> See Also

- [Trustworthy AI with Akka](https://akka.io/blog/trustworthy-ai-with-akka)
- [Webinar: Creating Certainty in the Age of Agentic AI](https://akka.io/blog/webinar-creating-certainty-in-the-age-of-agentic-ai)

<!-- <footer> -->
<!-- <nav> -->
[Access control lists](acls.html) [Developing](../sdk/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->