/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import akka.javasdk.tooling.validation.Validation;
import akka.javasdk.tooling.validation.Validations;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
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

    info("Validating Akka components...");
    for (TypeElement annotation : annotations) {
      var annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);

      for (TypeElement element : ElementFilter.typesIn(annotatedElements)) {
        debug("Validating " + element.getSimpleName());
        Validation validation = Validations.validateComponent(element);

        if (validation.isInvalid() && validation instanceof Validation.Invalid invalid) {
          // Report each validation error as a compile-time error
          for (String message : invalid.messages()) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
          }
        }
      }
    }

    return false; // Allow other processors to process these annotations
  }

}
