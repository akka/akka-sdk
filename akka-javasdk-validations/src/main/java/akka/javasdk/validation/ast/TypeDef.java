/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.validation.ast;

import java.util.List;
import java.util.Optional;

/**
 * Represents a type (class or interface) in the validation AST. This can be backed by either
 * compile-time (javax.lang.model.element.TypeElement) or runtime (java.lang.Class) representations.
 */
public interface TypeDef {

  /**
   * Returns the simple name of this type.
   *
   * <p>For example, for {@code com.example.MyClass}, this returns "MyClass".
   *
   * @return the simple type name
   */
  String getSimpleName();

  /**
   * Returns the fully qualified name of this type.
   *
   * <p>For example, for a class named MyClass in package com.example, this returns
   * "com.example.MyClass".
   *
   * @return the fully qualified type name
   */
  String getQualifiedName();

  /**
   * Checks if this type is public.
   *
   * @return true if the type has public modifier
   */
  boolean isPublic();

  /**
   * Checks if this type is sealed.
   *
   * @return true if the type is sealed
   */
  boolean isSealed();

  /**
   * Checks if this type is abstract.
   *
   * @return true if the type has abstract modifier
   */
  boolean isAbstract();

  /**
   * Returns all methods declared in this type.
   *
   * <p>This includes both public and non-public methods, but does not include inherited methods.
   *
   * @return list of methods
   */
  List<MethodDef> getMethods();

  /**
   * Returns only the public methods declared in this type.
   *
   * @return list of public methods
   */
  default List<MethodDef> getPublicMethods() {
    return getMethods().stream().filter(MethodDef::isPublic).toList();
  }

  /**
   * Returns all annotations present on this type.
   *
   * @return list of annotations
   */
  List<AnnotationDef> getAnnotations();

  /**
   * Finds an annotation by its fully qualified name.
   *
   * @param annotationName the fully qualified annotation name
   * @return the annotation if found, empty otherwise
   */
  Optional<AnnotationDef> findAnnotation(String annotationName);

  /**
   * Checks if this type has an annotation with the given fully qualified name.
   *
   * @param annotationName the fully qualified annotation name
   * @return true if the annotation is present
   */
  default boolean hasAnnotation(String annotationName) {
    return findAnnotation(annotationName).isPresent();
  }

  /**
   * Checks if this type has an annotation whose fully qualified name starts with the given prefix.
   *
   * <p>This is useful for checking annotation families, e.g., checking if any {@code @Consume.*}
   * annotation is present by using "akka.javasdk.annotations.Consume" as the prefix.
   *
   * @param annotationPrefix the annotation name prefix
   * @return true if any annotation with this prefix is present
   */
  default boolean hasAnnotationStartingWith(String annotationPrefix) {
    return getAnnotations().stream()
        .anyMatch(ann -> ann.getAnnotationType().startsWith(annotationPrefix));
  }

  /**
   * Returns the superclass of this type.
   *
   * @return the superclass type reference, or empty if this is Object or an interface
   */
  Optional<TypeRefDef> getSuperclass();

  /**
   * Checks if this type extends (directly or indirectly) the given class.
   *
   * @param className the fully qualified name of the class to check
   * @return true if this type extends the specified class
   */
  boolean extendsType(String className);

  /**
   * Returns the permitted subclasses for a sealed type.
   *
   * @return list of permitted subclass type references, empty if not sealed
   */
  List<TypeRefDef> getPermittedSubclasses();

  /**
   * Returns the type parameters of the superclass if it's generic.
   *
   * <p>For example, if this class is {@code class MyEntity extends EventSourcedEntity<String,
   * MyEvent>}, calling this method returns the type references for {@code String} and {@code
   * MyEvent}.
   *
   * @return list of superclass type arguments, empty if superclass is not generic
   */
  List<TypeRefDef> getSuperclassTypeArguments();

  /**
   * Returns all nested types (inner classes/interfaces) declared in this type.
   *
   * @return list of nested types
   */
  List<TypeDef> getNestedTypes();

  /**
   * Checks if this type is static.
   *
   * <p>This is primarily useful for nested classes to determine if they are static inner classes.
   *
   * @return true if the type has static modifier
   */
  boolean isStatic();
}
