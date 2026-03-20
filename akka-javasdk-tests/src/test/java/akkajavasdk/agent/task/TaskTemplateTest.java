/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.agent.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.agent.task.TaskTemplate;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TaskTemplateTest {

  public record CodeDeliverable(String feature, String implementation, String tests) {}

  @Test
  public void shouldDefineTemplateWithParameters() {
    TaskTemplate<CodeDeliverable> template =
        TaskTemplate.define("Implement a feature")
            .description("Implement a feature with clean, tested code")
            .resultConformsTo(CodeDeliverable.class)
            .instructionTemplate("Implement: {feature}. Requirements: {requirements}.");

    assertThat(template.name()).isEqualTo("Implement a feature");
    assertThat(template.description()).isEqualTo("Implement a feature with clean, tested code");
    assertThat(template.resultType()).isEqualTo(CodeDeliverable.class);
    assertThat(template.instructionTemplate())
        .isEqualTo("Implement: {feature}. Requirements: {requirements}.");
  }

  @Test
  public void shouldExtractParameterNames() {
    var template =
        TaskTemplate.define("task")
            .instructionTemplate("Implement: {feature}. Requirements: {requirements}.");

    assertThat(template.templateParameterNames()).containsExactly("feature", "requirements");
  }

  @Test
  public void shouldResolveTemplateParameters() {
    var template =
        TaskTemplate.define("task")
            .resultConformsTo(CodeDeliverable.class)
            .instructionTemplate("Implement: {feature}. Requirements: {requirements}.");

    var task = template.params(Map.of("feature", "auth module", "requirements", "OAuth2 support"));

    assertThat(task.instructions())
        .isEqualTo("Implement: auth module. Requirements: OAuth2 support.");
    assertThat(task.name()).isEqualTo("task");
    assertThat(task.resultType()).isEqualTo(CodeDeliverable.class);
  }

  @Test
  public void shouldThrowOnMissingParameter() {
    var template =
        TaskTemplate.define("task")
            .instructionTemplate("Implement: {feature}. Requirements: {requirements}.");

    assertThatThrownBy(() -> template.params(Map.of("feature", "auth")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requirements");
  }

  @Test
  public void shouldOverrideTemplateWithFreeformInstructions() {
    var template =
        TaskTemplate.define("task")
            .resultConformsTo(CodeDeliverable.class)
            .instructionTemplate("Implement: {feature}.");

    var task = template.instructions("Just do everything");

    assertThat(task.instructions()).isEqualTo("Just do everything");
  }

  @Test
  public void shouldBeImmutable() {
    var original = TaskTemplate.define("task").description("original");
    var modified = original.description("modified");

    assertThat(original.description()).isEqualTo("original");
    assertThat(modified.description()).isEqualTo("modified");
  }
}
