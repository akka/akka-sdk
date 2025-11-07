/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.validation.ast.compiletime;

import akka.javasdk.validation.ast.AnnotationDef;
import akka.javasdk.validation.ast.TypeRefDef;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

/**
 * Compile-time implementation of AnnotationDef backed by javax.lang.model.element.AnnotationMirror.
 *
 * @param annotationMirror the compile-time annotation mirror
 */
public record CompileTimeAnnotationDef(AnnotationMirror annotationMirror) implements AnnotationDef {

  @Override
  public String getAnnotationType() {
    return annotationMirror.getAnnotationType().toString();
  }

  @Override
  public Optional<String> getStringValue(String attributeName) {
    return getAttributeValue(attributeName)
        .map(AnnotationValue::getValue)
        .filter(v -> v instanceof String)
        .map(v -> (String) v);
  }

  @Override
  public Optional<Boolean> getBooleanValue(String attributeName) {
    return getAttributeValue(attributeName)
        .map(AnnotationValue::getValue)
        .filter(v -> v instanceof Boolean)
        .map(v -> (Boolean) v);
  }

  @Override
  public Optional<TypeRefDef> getClassValue(String attributeName) {
    return getAttributeValue(attributeName)
        .map(AnnotationValue::getValue)
        .filter(v -> v instanceof TypeMirror)
        .map(v -> (TypeMirror) v)
        .map(CompileTimeTypeRefDef::new);
  }

  /**
   * Gets the annotation value for the given attribute name, checking both explicit values and
   * defaults.
   */
  private Optional<AnnotationValue> getAttributeValue(String attributeName) {
    // First, check explicit values
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        annotationMirror.getElementValues().entrySet()) {
      if (entry.getKey().getSimpleName().toString().equals(attributeName)) {
        return Optional.of(entry.getValue());
      }
    }

    // If not found in explicit values, check all annotation elements (including defaults)
    for (ExecutableElement method :
        annotationMirror.getAnnotationType().asElement().getEnclosedElements().stream()
            .filter(e -> e instanceof ExecutableElement)
            .map(e -> (ExecutableElement) e)
            .toList()) {
      if (method.getSimpleName().toString().equals(attributeName)) {
        AnnotationValue value = annotationMirror.getElementValues().get(method);
        if (value != null) {
          return Optional.of(value);
        }
        // Check for default value
        AnnotationValue defaultValue = method.getDefaultValue();
        if (defaultValue != null) {
          return Optional.of(defaultValue);
        }
      }
    }

    return Optional.empty();
  }

  /** Returns the underlying AnnotationMirror for internal use. */
  @Override
  public AnnotationMirror annotationMirror() {
    return annotationMirror;
  }
}
