/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * A capability of an agent, as listed in its {@link A2aAgentCard}.
 *
 * @param id the id of the skill, unique within the agent
 * @param name a short name
 * @param description what the skill does
 * @param tags keywords that describe the skill
 * @param examples example prompts
 * @param inputModes the media types the skill accepts, when they differ from the agent's default
 * @param outputModes the media types the skill produces, when they differ from the agent's default
 * @param securityRequirements the security this skill requires, when it differs from the agent's
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record A2aAgentSkill(
    String id,
    String name,
    String description,
    List<String> tags,
    List<String> examples,
    List<String> inputModes,
    List<String> outputModes,
    List<A2aSecurityRequirement> securityRequirements) {

  public A2aAgentSkill {
    tags = A2aValues.list(tags);
    examples = A2aValues.list(examples);
    inputModes = A2aValues.list(inputModes);
    outputModes = A2aValues.list(outputModes);
    securityRequirements = A2aValues.list(securityRequirements);
  }
}
