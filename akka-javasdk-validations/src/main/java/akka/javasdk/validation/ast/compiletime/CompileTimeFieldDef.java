/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.validation.ast.compiletime;

import akka.javasdk.validation.ast.FieldDef;
import akka.javasdk.validation.ast.TypeRefDef;
import javax.lang.model.element.VariableElement;

/**
 * Compile-time implementation of FieldDef backed by javax.lang.model.element.VariableElement.
 *
 * @param variableElement the compile-time variable element representing a field
 */
public record CompileTimeFieldDef(VariableElement variableElement) implements FieldDef {

  @Override
  public String getName() {
    return variableElement.getSimpleName().toString();
  }

  @Override
  public TypeRefDef getType() {
    return new CompileTimeTypeRefDef(variableElement.asType());
  }
}
