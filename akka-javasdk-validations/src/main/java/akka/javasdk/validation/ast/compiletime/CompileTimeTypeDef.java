/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.validation.ast.compiletime;

import akka.javasdk.validation.ast.AnnotationDef;
import akka.javasdk.validation.ast.MethodDef;
import akka.javasdk.validation.ast.TypeDef;
import akka.javasdk.validation.ast.TypeRefDef;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Compile-time implementation of TypeDef backed by javax.lang.model.element.TypeElement.
 *
 * @param typeElement the compile-time type element
 */
public record CompileTimeTypeDef(TypeElement typeElement) implements TypeDef {

  @Override
  public String getSimpleName() {
    return typeElement.getSimpleName().toString();
  }

  @Override
  public String getQualifiedName() {
    return typeElement.getQualifiedName().toString();
  }

  @Override
  public boolean isPublic() {
    return typeElement.getModifiers().contains(Modifier.PUBLIC);
  }

  @Override
  public boolean isSealed() {
    return typeElement.getModifiers().contains(Modifier.SEALED);
  }

  @Override
  public boolean isAbstract() {
    return typeElement.getModifiers().contains(Modifier.ABSTRACT);
  }

  @Override
  public List<MethodDef> getMethods() {
    return typeElement.getEnclosedElements().stream()
        .filter(e -> e instanceof ExecutableElement)
        .map(e -> (ExecutableElement) e)
        .map(CompileTimeMethodDef::new)
        .map(m -> (MethodDef) m)
        .toList();
  }

  @Override
  public List<AnnotationDef> getAnnotations() {
    return typeElement.getAnnotationMirrors().stream()
        .map(CompileTimeAnnotationDef::new)
        .map(a -> (AnnotationDef) a)
        .toList();
  }

  @Override
  public Optional<AnnotationDef> findAnnotation(String annotationName) {
    for (AnnotationMirror mirror : typeElement.getAnnotationMirrors()) {
      if (mirror.getAnnotationType().toString().equals(annotationName)) {
        return Optional.of(new CompileTimeAnnotationDef(mirror));
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<TypeRefDef> getSuperclass() {
    TypeMirror superclass = typeElement.getSuperclass();
    if (superclass != null) {
      return Optional.of(new CompileTimeTypeRefDef(superclass));
    }
    return Optional.empty();
  }

  @Override
  public boolean extendsType(String className) {
    TypeMirror superclass = typeElement.getSuperclass();
    if (superclass == null) {
      return false;
    }

    String superclassName = superclass.toString();
    // Handle generic types by checking if the superclass name starts with the expected class name
    // e.g., "akka.javasdk.eventsourcedentity.EventSourcedEntity<String, Event>" should match
    // "akka.javasdk.eventsourcedentity.EventSourcedEntity"
    if (superclassName.equals(className) || superclassName.startsWith(className + "<")) {
      return true;
    }

    // Check recursively up the hierarchy
    if (superclass instanceof DeclaredType declaredType) {
      Element superElement = declaredType.asElement();
      if (superElement instanceof TypeElement superType) {
        return new CompileTimeTypeDef(superType).extendsType(className);
      }
    }

    return false;
  }

  @Override
  public List<TypeRefDef> getPermittedSubclasses() {
    if (!isSealed()) {
      return Collections.emptyList();
    }
    List<? extends TypeMirror> permittedSubclasses = typeElement.getPermittedSubclasses();
    return permittedSubclasses.stream()
        .map(CompileTimeTypeRefDef::new)
        .map(t -> (TypeRefDef) t)
        .toList();
  }

  @Override
  public List<TypeRefDef> getSuperclassTypeArguments() {
    TypeMirror superclass = typeElement.getSuperclass();
    if (superclass instanceof DeclaredType declaredType) {
      return declaredType.getTypeArguments().stream()
          .map(CompileTimeTypeRefDef::new)
          .map(t -> (TypeRefDef) t)
          .toList();
    }
    return Collections.emptyList();
  }

  @Override
  public List<TypeDef> getNestedTypes() {
    return typeElement.getEnclosedElements().stream()
        .filter(e -> e instanceof TypeElement)
        .map(e -> (TypeElement) e)
        .map(CompileTimeTypeDef::new)
        .map(t -> (TypeDef) t)
        .toList();
  }

  @Override
  public boolean isStatic() {
    return typeElement.getModifiers().contains(Modifier.STATIC);
  }

  /** Returns the underlying TypeElement for internal use. */
  @Override
  public TypeElement typeElement() {
    return typeElement;
  }
}
