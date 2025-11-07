/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.validation.ast.compiletime;

import akka.javasdk.validation.ast.TypeDef;
import akka.javasdk.validation.ast.TypeRefDef;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Compile-time implementation of TypeRefDef backed by javax.lang.model.type.TypeMirror.
 *
 * @param typeMirror the compile-time type mirror
 */
public record CompileTimeTypeRefDef(TypeMirror typeMirror) implements TypeRefDef {

  @Override
  public String getQualifiedName() {
    return typeMirror.toString();
  }

  @Override
  public String getRawQualifiedName() {
    if (typeMirror instanceof DeclaredType declaredType) {
      Element element = declaredType.asElement();
      if (element instanceof TypeElement typeElement) {
        return typeElement.getQualifiedName().toString();
      }
    }
    // For non-declared types (primitives, arrays, etc.), return the string representation
    String name = typeMirror.toString();
    // Strip generic parameters if present
    int genericStart = name.indexOf('<');
    if (genericStart > 0) {
      return name.substring(0, genericStart);
    }
    return name;
  }

  @Override
  public boolean isGeneric() {
    if (typeMirror instanceof DeclaredType declaredType) {
      return !declaredType.getTypeArguments().isEmpty();
    }
    return false;
  }

  @Override
  public List<TypeRefDef> getTypeArguments() {
    if (typeMirror instanceof DeclaredType declaredType) {
      return declaredType.getTypeArguments().stream()
          .map(CompileTimeTypeRefDef::new)
          .map(t -> (TypeRefDef) t)
          .toList();
    }
    return Collections.emptyList();
  }

  @Override
  public Optional<TypeDef> resolveTypeDef() {
    if (typeMirror instanceof DeclaredType declaredType) {
      Element element = declaredType.asElement();
      if (element instanceof TypeElement typeElement) {
        return Optional.of(new CompileTimeTypeDef(typeElement));
      }
    }
    return Optional.empty();
  }

  /** Returns the underlying TypeMirror for internal use. */
  @Override
  public TypeMirror typeMirror() {
    return typeMirror;
  }
}
