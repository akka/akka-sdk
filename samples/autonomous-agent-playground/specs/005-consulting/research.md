# Research: Consulting — Delegation + Handoff

**Feature**: 005-consulting | **Date**: 2026-03-12

## R1: Composing delegation and handoff in a single agent

**Decision**: Use `canDelegateTo(ConsultingResearcher.class)` and `canHandoffTo(SeniorConsultant.class)` on the ConsultingCoordinator strategy. The LLM decides at runtime whether to delegate or hand off based on domain tool results.

**Rationale**: The autonomous-agents documentation shows this exact composition pattern — `canDelegateTo` creates child tasks (coordinator retains ownership), while `canHandoffTo` transfers the current task (coordinator is done). The framework provides separate tools to the LLM for each capability.

**Alternatives considered**:
- Separate triage agent + coordinator — adds unnecessary complexity; one agent can assess and route.
- Workflow-based orchestration — defeats the purpose of demonstrating autonomous agent coordination.

## R2: Delegation vs handoff — context and ownership flow

**Decision**:
- **Delegation**: Coordinator creates a RESEARCH child task → ConsultingResearcher works in isolated context → result flows back → coordinator synthesises. Coordinator pauses while worker executes.
- **Handoff**: Coordinator transfers ENGAGEMENT task to SeniorConsultant → senior takes full ownership → coordinator is done. Context accumulates forward.

**Rationale**: These are the two fundamental coordination patterns. Delegation = fan-out/fan-in (partitioned context). Handoff = relay (forwarded context). The distinction is the core learning objective of this sample.

## R3: Shared domain tools

**Decision**: Create a `ConsultingTools` class with `assessProblem()` and `checkComplexity()` methods annotated with `@FunctionTool`. Register via `tools(new ConsultingTools())` in both the coordinator's and senior consultant's strategies.

**Rationale**: The spec requires shared tools for consistent assessment. The autonomous-agents documentation shows tool objects registered via `tools()` in the strategy. Both agents get the same tool instance, ensuring consistent behavior.

**Alternatives considered**:
- Defining tools directly on each agent class — duplicates logic, risks inconsistency.
- Using component tools (entities/views) — overkill for simple string assessments.

## R4: Routing decision mechanism

**Decision**: The `checkComplexity` tool returns strings like "COMPLEX: Recommend escalation" or "STANDARD: Can be handled with research". The LLM uses this output, combined with the goal instruction, to decide whether to delegate or hand off.

**Rationale**: The spec says routing is LLM-driven using domain tools, not hardcoded. The tool provides a clear signal, and the agent's goal instructs it on the mapping between complexity levels and coordination actions.

## R5: Task types and result records

**Decision**:
- `ENGAGEMENT` task with `ConsultingResult(assessment, recommendation, escalated)` — used by coordinator and senior consultant
- `RESEARCH` task with `ResearchSummary(topic, findings)` — used by researcher only

**Rationale**: The spec defines these as the two task types. `escalated` boolean clearly distinguishes which path was taken. `findings` as String (not List) per spec assumption.

## R6: Max iterations per agent

**Decision**:
- ConsultingCoordinator: `maxIterations(10)` — needs to assess, potentially delegate, wait, and synthesise
- ConsultingResearcher: `maxIterations(3)` — focused research task
- SeniorConsultant: `maxIterations(5)` — needs to assess and resolve independently

**Rationale**: Follows the pattern from the documentation examples. Coordinator needs more iterations for the delegation round-trip. Researcher and senior have simpler focused tasks.
