/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import akka.javasdk.validation.ast.MethodDef;
import akka.javasdk.validation.ast.TypeDef;
import java.util.ArrayList;
import java.util.List;

/** Contains validation logic specific to Workflow components. */
public class WorkflowValidations {

  /**
   * Validates a Workflow component.
   *
   * @param typeDef the Workflow class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validate(TypeDef typeDef) {
    if (!typeDef.extendsType("akka.javasdk.workflow.Workflow")) {
      return Validation.Valid.instance();
    }

    String stepEffectType = "akka.javasdk.workflow.Workflow.StepEffect";

    String[] strictlyPublicEffectTypes = {
      "akka.javasdk.workflow.Workflow.Effect", "akka.javasdk.workflow.Workflow.ReadOnlyEffect"
    };

    return Validations
        // a workflow is not required to have step methods,
        // but must have at least one Effect or ReadOnlyEffect
        // although a single ReadOnlyEffect is questionable
        .hasEffectMethod(typeDef, strictlyPublicEffectTypes)
        // method returning Effect or ReadOnlyEffect must be public
        // users can still have private methods with arity > 1
        .combine(
            Validations.strictlyPublicCommandHandlerArityShouldBeZeroOrOne(
                typeDef, strictlyPublicEffectTypes))
        // method returning StepEffect can be private and therefore must comply with arity rule
        .combine(Validations.commandHandlerArityShouldBeZeroOrOne(typeDef, stepEffectType))
        .combine(functionToolMustNotBeOnStepEffect(typeDef))
        .combine(Validations.functionToolMustNotBeOnPrivateMethods(typeDef));
  }

  /**
   * Validates that @FunctionTool is not used on StepEffect methods.
   *
   * @param typeDef the Workflow class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation functionToolMustNotBeOnStepEffect(TypeDef typeDef) {
    List<String> errors = new ArrayList<>();

    for (MethodDef method : typeDef.getMethods()) {
      String returnTypeName = method.getReturnType().getQualifiedName();
      if (returnTypeName.equals("akka.javasdk.workflow.Workflow.StepEffect")
          || returnTypeName.startsWith("akka.javasdk.workflow.Workflow.StepEffect<")) {
        if (method.hasAnnotation("akka.javasdk.annotations.FunctionTool")) {
          errors.add(
              Validations.errorMessage(
                  method,
                  "Workflow methods annotated with @FunctionTool cannot return StepEffect. Only"
                      + " methods returning Effect or ReadOnlyEffect can be annotated with"
                      + " @FunctionTool."));
        }
      }
    }

    return Validation.of(errors);
  }
}
