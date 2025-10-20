/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

/**
 * Contains validation logic for component classes. This class encapsulates all validation rules
 * that are applied during annotation processing.
 */
public class Validations {

  /**
   * Validates a component class.
   *
   * @param element the component class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validateComponent(TypeElement element) {
    return componentMustBePublic(element);
  }

  /**
   * Validates that the component class is public.
   *
   * @param element the component class to validate
   * @return a Validation result indicating success or failure
   */
  public static Validation componentMustBePublic(TypeElement element) {
    if (element.getModifiers().contains(Modifier.PUBLIC)) {
      return Validation.Valid.instance();
    } else {
      return Validation.of(
          errorMessage(
              element,
              element.getSimpleName()
                  + " is not marked with `public` modifier. Components must be public."));
    }
  }

  /**
   * Creates a formatted error message for an element.
   *
   * @param element the element to create an error message for
   * @param message the error message
   * @return a formatted error message string
   */
  private static String errorMessage(Element element, String message) {
    String elementStr;
    if (element instanceof TypeElement typeElement) {
      elementStr = typeElement.getQualifiedName().toString();
    } else {
      elementStr = element.toString();
    }
    return "On '" + elementStr + "': " + message;
  }
}
