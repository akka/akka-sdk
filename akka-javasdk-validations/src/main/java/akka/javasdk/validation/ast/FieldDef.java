/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.validation.ast;

/**
 * Represents a field in the validation AST. This can be backed by either compile-time
 * (javax.lang.model.element.VariableElement) or runtime (java.lang.reflect.Field) representations.
 */
public interface FieldDef {

  /**
   * Returns the name of this field.
   *
   * @return the field name
   */
  String getName();

  /**
   * Returns the type of this field.
   *
   * @return the field type reference
   */
  TypeRefDef getType();
}
