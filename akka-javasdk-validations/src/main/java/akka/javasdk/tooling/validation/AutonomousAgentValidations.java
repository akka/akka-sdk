/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import static akka.javasdk.tooling.validation.Validations.effectMethods;

import akka.javasdk.validation.ast.AnnotationDef;
import akka.javasdk.validation.ast.TypeDef;
import java.util.Optional;

/** Contains validation logic specific to AutonomousAgent components. */
public class AutonomousAgentValidations {

  private static final String[] effectTypes = {
    "akka.javasdk.agent.Agent.Effect", "akka.javasdk.agent.Agent.StreamEffect"
  };

  /**
   * Validates an AutonomousAgent component. Must NOT have command handlers and must declare a
   * non-empty {@code @Component(description = ...)}.
   *
   * @param typeDef the AutonomousAgent class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validate(TypeDef typeDef) {
    if (!typeDef.extendsType("akka.javasdk.agent.autonomous.AutonomousAgent")) {
      return Validation.Valid.instance();
    }

    return mustNotHaveCommandHandler(typeDef).combine(mustHaveNonEmptyDescription(typeDef));
  }

  /** Validates that an AutonomousAgent has no command handlers. */
  private static Validation mustNotHaveCommandHandler(TypeDef typeDef) {
    int count = effectMethods(typeDef, effectTypes).size();
    if (count == 0) {
      return Validation.Valid.instance();
    } else {
      return Validation.of(
          Validations.errorMessage(
              typeDef,
              "AutonomousAgent must not define command handler methods"
                  + " returning Agent.Effect or Agent.StreamEffect."
                  + " Use strategy() to configure the agent's behavior."));
    }
  }

  /**
   * Validates that an AutonomousAgent declares a non-empty {@code @Component(description = ...)}.
   * The description is the agent's public identity, used by coordinators selecting delegation or
   * handoff targets and injected into the model's system message.
   */
  private static Validation mustHaveNonEmptyDescription(TypeDef typeDef) {
    Optional<AnnotationDef> componentAnn =
        typeDef.findAnnotation("akka.javasdk.annotations.Component");
    if (componentAnn.isEmpty()) {
      return Validation.Valid.instance();
    }
    Optional<String> description = componentAnn.get().getStringValue("description");
    if (description.isEmpty() || description.get().isBlank()) {
      return Validation.of(
          Validations.errorMessage(
              typeDef,
              "@Component description is mandatory for AutonomousAgent and must be a non-empty"
                  + " string. The description identifies the agent to coordinators selecting a"
                  + " delegation or handoff target, and is included in the model's system"
                  + " message."));
    }
    return Validation.Valid.instance();
  }
}
