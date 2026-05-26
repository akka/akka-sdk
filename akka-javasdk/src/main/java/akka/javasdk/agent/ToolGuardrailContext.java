/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Per-call context passed to a {@link ToolGuardrail} during {@link ToolGuardrail#evaluate}.
 *
 * <p>Carries data about the specific tool call being evaluated. Fields will be added as tool-side
 * boundaries are wired up; for now this is a placeholder.
 *
 * <p>For construction-time data that doesn't change per call (the guardrail's configured name and
 * its config section) accept a {@link GuardrailContext} parameter in the constructor.
 */
public interface ToolGuardrailContext {}
