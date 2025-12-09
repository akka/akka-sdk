/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.validation.ast.compiletime;

import akka.javasdk.validation.ast.AnnotationDef;
import akka.javasdk.validation.ast.MethodDef;
import akka.javasdk.validation.ast.ParameterDef;
import akka.javasdk.validation.ast.TypeRefDef;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;

/**
 * Compile-time implementation of MethodDef backed by javax.lang.model.element.ExecutableElement.
 *
 * @param executableElement the compile-time executable element representing the method
 */
public record CompileTimeMethodDef(ExecutableElement executableElement) implements MethodDef {

  @Override
  public String getName() {
    return executableElement.getSimpleName().toString();
  }

  @Override
  public TypeRefDef getReturnType() {
    return new CompileTimeTypeRefDef(executableElement.getReturnType());
  }

  @Override
  public List<ParameterDef> getParameters() {
    return executableElement.getParameters().stream()
        .map(CompileTimeParameterDef::new)
        .map(p -> (ParameterDef) p)
        .toList();
  }

  @Override
  public boolean isPublic() {
    return executableElement.getModifiers().contains(Modifier.PUBLIC);
  }

  @Override
  public List<AnnotationDef> getAnnotations() {
    return executableElement.getAnnotationMirrors().stream()
        .map(CompileTimeAnnotationDef::new)
        .map(a -> (AnnotationDef) a)
        .toList();
  }

  @Override
  public Optional<AnnotationDef> findAnnotation(String annotationName) {
    for (AnnotationMirror mirror : executableElement.getAnnotationMirrors()) {
      if (mirror.getAnnotationType().toString().equals(annotationName)) {
        return Optional.of(new CompileTimeAnnotationDef(mirror));
      }
    }
    return Optional.empty();
  }

  /** Returns the underlying ExecutableElement for internal use. */
  @Override
  public ExecutableElement executableElement() {
    return executableElement;
  }
}
