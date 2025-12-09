/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.validation.ast;

/**
 * Represents a method parameter in the validation AST. This can be backed by either compile-time
 * (javax.lang.model.element.VariableElement) or runtime (java.lang.reflect.Parameter)
 * representations.
 */
public interface ParameterDef {

  /**
   * Returns the type of this parameter.
   *
   * @return the parameter type reference
   */
  TypeRefDef getType();

  /**
   * Returns the name of this parameter.
   *
   * <p>Note: Parameter names may not always be available in compiled bytecode unless compiled with
   * the -parameters flag.
   *
   * @return the parameter name, or a generated name if not available
   */
  String getName();
}
