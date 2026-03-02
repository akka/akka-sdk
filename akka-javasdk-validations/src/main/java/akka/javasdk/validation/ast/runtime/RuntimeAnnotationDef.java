/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.validation.ast.runtime;

import akka.javasdk.validation.ast.AnnotationDef;
import akka.javasdk.validation.ast.TypeRefDef;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Runtime implementation of AnnotationDef backed by java.lang.annotation.Annotation.
 *
 * <p>Note: This implementation normalizes class names to use '.' instead of '$' for nested classes,
 * matching the compile-time representation from javax.lang.model.
 *
 * @param annotation the runtime annotation instance
 */
public record RuntimeAnnotationDef(Annotation annotation) implements AnnotationDef {

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
  public String getAnnotationType() {
    return getCanonicalClassName(annotation.annotationType());
  }

  @Override
  public Optional<String> getStringValue(String attributeName) {
    return getAttributeValue(attributeName).filter(v -> v instanceof String).map(v -> (String) v);
  }

  @Override
  public Optional<Boolean> getBooleanValue(String attributeName) {
    return getAttributeValue(attributeName).filter(v -> v instanceof Boolean).map(v -> (Boolean) v);
  }

  @Override
  public Optional<TypeRefDef> getClassValue(String attributeName) {
    return getAttributeValue(attributeName)
        .filter(v -> v instanceof Class)
        .map(v -> (Class<?>) v)
        .map(RuntimeTypeRefDef::new);
  }

  /**
   * Gets the annotation attribute value for the given attribute name using reflection.
   *
   * @param attributeName the name of the attribute method
   * @return the attribute value, or empty if not found or error occurred
   */
  private Optional<Object> getAttributeValue(String attributeName) {
    try {
      Method method = annotation.annotationType().getDeclaredMethod(attributeName);
      method.setAccessible(true);
      Object value = method.invoke(annotation);
      return Optional.ofNullable(value);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      return Optional.empty();
    }
  }
}
