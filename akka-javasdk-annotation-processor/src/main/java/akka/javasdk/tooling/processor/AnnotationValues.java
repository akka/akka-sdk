/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import java.util.List;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/** Reads the annotation values that a source declares explicitly. */
final class AnnotationValues {

  private AnnotationValues() {}

  /** Returns the annotation of the given qualified name on the element. */
  static Optional<AnnotationMirror> find(Element element, String annotationName) {
    for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
      var annotationType = (TypeElement) mirror.getAnnotationType().asElement();
      if (annotationType.getQualifiedName().contentEquals(annotationName)) {
        return Optional.of(mirror);
      }
    }
    return Optional.empty();
  }

  /** Returns the explicit, non-empty string value of an annotation element. */
  static Optional<String> string(AnnotationMirror mirror, String name) {
    return value(mirror, name)
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .filter(value -> !value.isEmpty());
  }

  /** Returns the explicit int value of an annotation element. */
  static Optional<Integer> integer(AnnotationMirror mirror, String name) {
    return value(mirror, name).filter(Integer.class::isInstance).map(Integer.class::cast);
  }

  /** Returns the simple name of the explicit enum constant of an annotation element. */
  static Optional<String> enumConstant(AnnotationMirror mirror, String name) {
    return value(mirror, name).flatMap(AnnotationValues::enumName);
  }

  /** Returns the explicit string array of an annotation element, or an empty list. */
  static List<String> strings(AnnotationMirror mirror, String name) {
    return array(mirror, name).stream()
        .map(AnnotationValue::getValue)
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .toList();
  }

  /** Returns the simple names of the explicit enum array of an annotation element. */
  static List<String> enumConstants(AnnotationMirror mirror, String name) {
    return array(mirror, name).stream()
        .map(AnnotationValue::getValue)
        .flatMap(value -> enumName(value).stream())
        .toList();
  }

  /** Returns the explicit annotation array of an annotation element, or an empty list. */
  static List<AnnotationMirror> mirrors(AnnotationMirror mirror, String name) {
    return array(mirror, name).stream()
        .map(AnnotationValue::getValue)
        .filter(AnnotationMirror.class::isInstance)
        .map(AnnotationMirror.class::cast)
        .toList();
  }

  private static Optional<Object> value(AnnotationMirror mirror, String name) {
    return mirror.getElementValues().entrySet().stream()
        .filter(entry -> entry.getKey().getSimpleName().contentEquals(name))
        .map(entry -> entry.getValue().getValue())
        .findFirst();
  }

  private static List<? extends AnnotationValue> array(AnnotationMirror mirror, String name) {
    return value(mirror, name)
        .filter(List.class::isInstance)
        .<List<? extends AnnotationValue>>map(AnnotationValues::asValueList)
        .orElse(List.of());
  }

  @SuppressWarnings("unchecked")
  private static List<? extends AnnotationValue> asValueList(Object value) {
    return (List<? extends AnnotationValue>) value;
  }

  private static Optional<String> enumName(Object value) {
    return value instanceof VariableElement constant
        ? Optional.of(constant.getSimpleName().toString())
        : Optional.empty();
  }
}
