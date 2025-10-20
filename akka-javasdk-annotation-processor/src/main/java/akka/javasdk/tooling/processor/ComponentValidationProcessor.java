/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import akka.javasdk.tooling.validation.Validation;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

/**
 * Annotation processor that performs compile-time validation on classes annotated with @Component.
 */
@SupportedAnnotationTypes({
  "akka.javasdk.annotations.Component"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class ComponentValidationProcessor extends AbstractProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation : annotations) {
      Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);

      for (TypeElement element : ElementFilter.typesIn(annotatedElements)) {
        Validation validation = validateComponent(element);

        if (validation.isInvalid() && validation instanceof Validation.Invalid invalid) {
          // Report each validation error as a compile-time error
          for (String message : invalid.messages()) {
            processingEnv.getMessager().printMessage(
              Diagnostic.Kind.ERROR,
              message,
              element
            );
          }
        }
      }
    }

    return false; // Allow other processors to process these annotations
  }

  /**
   * Validates a component class.
   */
  private Validation validateComponent(TypeElement element) {
    return componentMustBePublic(element);
  }

  /**
   * Validates that the component class is public.
   */
  private Validation componentMustBePublic(TypeElement element) {
    if (element.getModifiers().contains(Modifier.PUBLIC)) {
      return Validation.Valid.instance();
    } else {
      return Validation.of(
        errorMessage(
          element,
          element.getSimpleName() + " is not marked with `public` modifier. Components must be public."
        )
      );
    }
  }

  /**
   * Creates a formatted error message for an element.
   */
  private String errorMessage(Element element, String message) {
    String elementStr;
    if (element instanceof TypeElement typeElement) {
      elementStr = typeElement.getQualifiedName().toString();
    } else {
      elementStr = element.toString();
    }
    return "On '" + elementStr + "': " + message;
  }
}
