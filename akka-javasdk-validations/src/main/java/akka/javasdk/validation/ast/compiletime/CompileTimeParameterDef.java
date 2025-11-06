/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.validation.ast.compiletime;

import akka.javasdk.validation.ast.ParameterDef;
import akka.javasdk.validation.ast.TypeRefDef;
import javax.lang.model.element.VariableElement;

/**
 * Compile-time implementation of ParameterDef backed by javax.lang.model.element.VariableElement.
 *
 * @param variableElement the compile-time variable element representing the parameter
 */
public record CompileTimeParameterDef(VariableElement variableElement) implements ParameterDef {

  @Override
  public TypeRefDef getType() {
    return new CompileTimeTypeRefDef(variableElement.asType());
  }

  @Override
  public String getName() {
    return variableElement.getSimpleName().toString();
  }

  /** Returns the underlying VariableElement for internal use. */
  @Override
  public VariableElement variableElement() {
    return variableElement;
  }
}
