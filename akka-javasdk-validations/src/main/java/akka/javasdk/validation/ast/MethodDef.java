/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.validation.ast;

import java.util.List;
import java.util.Optional;

/**
 * Represents a method in the validation AST. This can be backed by either compile-time
 * (javax.lang.model.element.ExecutableElement) or runtime (java.lang.reflect.Method)
 * representations.
 */
public interface MethodDef {

  /**
   * Returns the name of this method.
   *
   * @return the method name
   */
  String getName();

  /**
   * Returns the return type of this method.
   *
   * @return the return type reference
   */
  TypeRefDef getReturnType();

  /**
   * Returns the list of parameters for this method.
   *
   * @return the list of parameters, empty if the method has no parameters
   */
  List<ParameterDef> getParameters();

  /**
   * Checks if this method is public.
   *
   * @return true if the method has public modifier
   */
  boolean isPublic();

  /**
   * Returns all annotations present on this method.
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
   * Checks if this method has an annotation with the given fully qualified name.
   *
   * @param annotationName the fully qualified annotation name
   * @return true if the annotation is present
   */
  default boolean hasAnnotation(String annotationName) {
    return findAnnotation(annotationName).isPresent();
  }
}
