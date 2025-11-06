/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.validation.ast;

import java.util.List;
import java.util.Optional;

/**
 * Represents a type reference in the validation AST. This can be backed by either compile-time
 * (javax.lang.model.type.TypeMirror) or runtime (java.lang.Class) type representations.
 */
public interface TypeRefDef {

  /**
   * Returns the fully qualified name of this type.
   *
   * <p>For generic types like {@code List<String>}, this returns the full representation including
   * type parameters (e.g., "java.util.List<java.lang.String>").
   *
   * @return the fully qualified type name
   */
  String getQualifiedName();

  /**
   * Returns the raw (non-generic) fully qualified name of this type.
   *
   * <p>For generic types like {@code List<String>}, this returns just the raw type without
   * parameters (e.g., "java.util.List").
   *
   * @return the raw fully qualified type name
   */
  String getRawQualifiedName();

  /**
   * Checks if this type is a generic type with type parameters.
   *
   * @return true if this type has type parameters
   */
  boolean isGeneric();

  /**
   * Returns the type arguments for a generic type.
   *
   * <p>For example, for {@code List<String>}, this returns a list containing the type reference for
   * {@code String}.
   *
   * @return list of type arguments, empty if not generic
   */
  List<TypeRefDef> getTypeArguments();

  /**
   * Resolves this type reference to a TypeDef if possible.
   *
   * <p>This allows navigating from a type reference to the actual type definition, which is useful
   * for examining class structure, methods, etc.
   *
   * @return the resolved TypeDef, or empty if resolution is not possible
   */
  Optional<TypeDef> resolveTypeDef();
}
