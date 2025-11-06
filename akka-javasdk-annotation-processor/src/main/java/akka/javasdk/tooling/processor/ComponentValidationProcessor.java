/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import akka.javasdk.tooling.validation.Validation;
import akka.javasdk.tooling.validation.Validations;
import akka.javasdk.validation.ast.compiletime.CompileTimeTypeDef;
import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

/**
 * Annotation processor that performs compile-time validation on classes annotated with @Component,
 * or deprecated @ComponentId.
 */
@SupportedAnnotationTypes({
  "akka.javasdk.annotations.Component",
  "akka.javasdk.annotations.ComponentId"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class ComponentValidationProcessor extends BaseAkkaProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

    // Collect all validation errors before reporting them
    java.util.List<ValidationError> allErrors = new java.util.ArrayList<>();

    info("Validating Akka components...");
    for (TypeElement annotation : annotations) {
      var annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);

      for (TypeElement element : ElementFilter.typesIn(annotatedElements)) {
        debug("Validating " + element.getSimpleName());

        // Wrap TypeElement in CompileTimeTypeDef
        CompileTimeTypeDef typeDef = new CompileTimeTypeDef(element);
        Validation validation = Validations.validateComponent(typeDef);

        if (validation instanceof Validation.Invalid(java.util.List<String> errorMessages)) {
          debug("Component " + element.getSimpleName() + " is invalid");
          // Collect errors instead of reporting immediately
          for (String message : errorMessages) {
            allErrors.add(new ValidationError(element, message));
          }
        } else {
          debug("Component " + element.getSimpleName() + " is valid");
        }
      }
    }

    // Report all errors at once
    if (!allErrors.isEmpty()) {
      info("Found " + allErrors.size() + " validation error(s):");
      for (ValidationError error : allErrors) {
        processingEnv
            .getMessager()
            .printMessage(Diagnostic.Kind.ERROR, error.message, error.element);
      }
    }

    // if empty, we should return false to allow other processors to process these annotations
    // otherwise we can shortcut it because the build will fail anyway
    return !allErrors.isEmpty();
  }

  // Helper class to store validation errors
  private record ValidationError(TypeElement element, String message) {}
}
