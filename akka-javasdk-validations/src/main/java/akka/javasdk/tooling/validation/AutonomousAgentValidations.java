/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import static akka.javasdk.tooling.validation.Validations.effectMethods;

import akka.javasdk.validation.ast.TypeDef;

/** Contains validation logic specific to AutonomousAgent components. */
public class AutonomousAgentValidations {

  private static final String[] effectTypes = {
    "akka.javasdk.agent.Agent.Effect", "akka.javasdk.agent.Agent.StreamEffect"
  };

  /**
   * Validates an AutonomousAgent component. Must NOT have command handlers.
   *
   * @param typeDef the AutonomousAgent class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validate(TypeDef typeDef) {
    if (!typeDef.extendsType("akka.javasdk.agent.autonomous.AutonomousAgent")) {
      return Validation.Valid.instance();
    }

    return AgentValidations.mustHaveValidAgentDescription(typeDef)
        .combine(mustNotHaveCommandHandler(typeDef));
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
}
