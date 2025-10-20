/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

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
    return componentMustBePublic(element)
        .combine(mustHaveValidComponentId(element))
        .combine(validateTimedAction(element));
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
   * Validates that a component has a valid component ID. Checks for: - Presence of both @Component
   * and deprecated @ComponentId (error) - Non-empty component ID - No pipe character '|' in
   * component ID
   *
   * @param element the component class to validate
   * @return a Validation result indicating success or failure
   */
  public static Validation mustHaveValidComponentId(TypeElement element) {
    AnnotationMirror componentAnn = findAnnotation(element, "akka.javasdk.annotations.Component");
    AnnotationMirror componentIdAnn =
        findAnnotation(element, "akka.javasdk.annotations.ComponentId");

    if (componentAnn != null && componentIdAnn != null) {
      return Validation.of(
          errorMessage(
              element,
              "Component class '"
                  + element.getQualifiedName()
                  + "' has both @Component and deprecated @ComponentId annotations. Please remove"
                  + " @ComponentId and use only @Component."));
    } else if (componentAnn != null) {
      String componentId = getAnnotationValue(componentAnn, "id");
      if (componentId == null || componentId.isBlank()) {
        return Validation.of(
            errorMessage(element, "@Component id is empty, must be a non-empty string."));
      } else if (componentId.contains("|")) {
        return Validation.of(
            errorMessage(element, "@Component id must not contain the pipe character '|'."));
      } else {
        return Validation.Valid.instance();
      }
    } else if (componentIdAnn != null) {
      String componentId = getAnnotationValue(componentIdAnn, "value");
      if (componentId == null || componentId.isBlank()) {
        return Validation.of(
            errorMessage(element, "@ComponentId name is empty, must be a non-empty string."));
      } else if (componentId.contains("|")) {
        return Validation.of(
            errorMessage(element, "@ComponentId must not contain the pipe character '|'."));
      } else {
        return Validation.Valid.instance();
      }
    } else {
      // A missing annotation means that the component is disabled
      return Validation.Valid.instance();
    }
  }

  /**
   * Validates a TimedAction component. Checks for: - At least one method returning
   * TimedAction.Effect - Command handlers (methods returning Effect) must have zero or one
   * parameter
   *
   * @param element the component class to validate
   * @return a Validation result indicating success or failure
   */
  public static Validation validateTimedAction(TypeElement element) {
    if (!isTimedAction(element)) {
      return Validation.Valid.instance();
    }

    return hasEffectMethod(element, "akka.javasdk.timedaction.TimedAction.Effect")
        .combine(
            commandHandlerArityShouldBeZeroOrOne(
                element, "akka.javasdk.timedaction.TimedAction.Effect"));
  }

  /**
   * Checks if a component extends TimedAction.
   *
   * @param element the component class to check
   * @return true if the component extends TimedAction, false otherwise
   */
  private static boolean isTimedAction(TypeElement element) {
    return extendsClass(element, "akka.javasdk.timedaction.TimedAction");
  }

  /**
   * Checks if a component extends a specific class.
   *
   * @param element the component class to check
   * @param className the fully qualified class name to check for
   * @return true if the component extends the specified class
   */
  private static boolean extendsClass(TypeElement element, String className) {
    TypeMirror superclass = element.getSuperclass();
    if (superclass == null) {
      return false;
    }

    String superclassName = superclass.toString();
    if (superclassName.equals(className)) {
      return true;
    }

    // Check recursively up the hierarchy
    if (superclass instanceof DeclaredType declaredType) {
      Element superElement = declaredType.asElement();
      if (superElement instanceof TypeElement superType) {
        return extendsClass(superType, className);
      }
    }

    return false;
  }

  /**
   * Validates that a component has at least one method returning the specified effect type.
   *
   * @param element the component class to validate
   * @param effectTypeName the fully qualified effect type name
   * @return a Validation result indicating success or failure
   */
  private static Validation hasEffectMethod(TypeElement element, String effectTypeName) {
    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        String returnTypeName = method.getReturnType().toString();
        if (returnTypeName.equals(effectTypeName)) {
          return Validation.Valid.instance();
        }
      }
    }

    return Validation.of(
        "No method returning " + effectTypeName + " found in " + element.getQualifiedName());
  }

  /**
   * Validates that command handlers have zero or one parameter.
   *
   * @param element the component class to validate
   * @param effectTypeName the fully qualified effect type name to identify command handlers
   * @return a Validation result indicating success or failure
   */
  private static Validation commandHandlerArityShouldBeZeroOrOne(
      TypeElement element, String effectTypeName) {
    List<String> errors = new ArrayList<>();

    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        String returnTypeName = method.getReturnType().toString();
        if (returnTypeName.equals(effectTypeName)) {
          int paramCount = method.getParameters().size();
          if (paramCount > 1) {
            errors.add(
                errorMessage(
                    element,
                    "Method ["
                        + method.getSimpleName()
                        + "] must have zero or one argument. If you need to pass more arguments,"
                        + " wrap them in a class."));
          }
        }
      }
    }

    return Validation.of(errors);
  }

  /**
   * Finds an annotation on an element by its fully qualified name.
   *
   * @param element the element to search
   * @param annotationName the fully qualified annotation name
   * @return the AnnotationMirror if found, null otherwise
   */
  private static AnnotationMirror findAnnotation(Element element, String annotationName) {
    for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
      if (mirror.getAnnotationType().toString().equals(annotationName)) {
        return mirror;
      }
    }
    return null;
  }

  /**
   * Gets the value of an annotation attribute.
   *
   * @param annotation the annotation mirror
   * @param attributeName the attribute name
   * @return the attribute value as a String, or null if not found
   */
  private static String getAnnotationValue(AnnotationMirror annotation, String attributeName) {
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        annotation.getElementValues().entrySet()) {
      if (entry.getKey().getSimpleName().toString().equals(attributeName)) {
        Object value = entry.getValue().getValue();
        return value != null ? value.toString() : null;
      }
    }
    return null;
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
