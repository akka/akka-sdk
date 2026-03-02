/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.validation.ast.runtime;

import akka.javasdk.validation.ast.AnnotationDef;
import akka.javasdk.validation.ast.MethodDef;
import akka.javasdk.validation.ast.ParameterDef;
import akka.javasdk.validation.ast.TypeRefDef;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Runtime implementation of MethodDef backed by java.lang.reflect.Method.
 *
 * <p>Note: This implementation normalizes class names to use '.' instead of '$' for nested classes,
 * matching the compile-time representation from javax.lang.model.
 *
 * @param method the runtime method
 */
public record RuntimeMethodDef(Method method) implements MethodDef {

  /**
   * Gets the canonical name for a class, handling nested classes properly.
   *
   * @param clazz the class to get the name for
   * @return the canonical class name
   */
  private static String getCanonicalClassName(Class<?> clazz) {
    String canonical = clazz.getCanonicalName();
    if (canonical != null) {
      return canonical;
    }
    // Fallback for anonymous/local classes
    return clazz.getName().replace('$', '.');
  }

  @Override
  public String getName() {
    return method.getName();
  }

  @Override
  public TypeRefDef getReturnType() {
    return new RuntimeTypeRefDef(method.getGenericReturnType());
  }

  @Override
  public List<ParameterDef> getParameters() {
    return Arrays.stream(method.getParameters())
        .map(RuntimeParameterDef::new)
        .map(p -> (ParameterDef) p)
        .toList();
  }

  @Override
  public boolean isPublic() {
    return Modifier.isPublic(method.getModifiers());
  }

  @Override
  public List<AnnotationDef> getAnnotations() {
    return Arrays.stream(method.getAnnotations())
        .map(RuntimeAnnotationDef::new)
        .map(a -> (AnnotationDef) a)
        .toList();
  }

  @Override
  public Optional<AnnotationDef> findAnnotation(String annotationName) {
    for (Annotation annotation : method.getAnnotations()) {
      if (getCanonicalClassName(annotation.annotationType()).equals(annotationName)) {
        return Optional.of(new RuntimeAnnotationDef(annotation));
      }
    }
    return Optional.empty();
  }
}
