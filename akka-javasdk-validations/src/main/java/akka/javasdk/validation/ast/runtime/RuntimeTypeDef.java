/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.validation.ast.runtime;

import akka.javasdk.validation.ast.AnnotationDef;
import akka.javasdk.validation.ast.FieldDef;
import akka.javasdk.validation.ast.MethodDef;
import akka.javasdk.validation.ast.TypeDef;
import akka.javasdk.validation.ast.TypeRefDef;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Runtime implementation of TypeDef backed by java.lang.Class.
 *
 * <p>Note: This implementation normalizes class names to use '.' instead of '$' for nested classes,
 * matching the compile-time representation from javax.lang.model.
 *
 * @param clazz the runtime class
 */
public record RuntimeTypeDef(Class<?> clazz) implements TypeDef {

  /**
   * Gets the canonical name for a class, handling array types and nested classes.
   *
   * <p>Uses Class.getCanonicalName() which returns proper format for arrays and nested classes.
   *
   * @param clazz the class to get the name for
   * @return the canonical class name, or the normalized name if canonical is null
   */
  private static String getCanonicalClassName(Class<?> clazz) {
    String canonical = clazz.getCanonicalName();
    if (canonical != null) {
      return canonical;
    }
    // Fallback for anonymous/local classes that don't have canonical names
    return clazz.getName().replace('$', '.');
  }

  @Override
  public String getSimpleName() {
    return clazz.getSimpleName();
  }

  @Override
  public String getQualifiedName() {
    return getCanonicalClassName(clazz);
  }

  @Override
  public boolean isPublic() {
    return Modifier.isPublic(clazz.getModifiers());
  }

  @Override
  public boolean isSealed() {
    return clazz.isSealed();
  }

  @Override
  public boolean isAbstract() {
    return Modifier.isAbstract(clazz.getModifiers());
  }

  @Override
  public List<MethodDef> getMethods() {
    List<MethodDef> methods = new java.util.ArrayList<>();
    // Methods declared in this type. Synthetic/bridge methods are skipped so the result matches
    // the source-level view of CompileTimeTypeDef (e.g. erased-signature bridges for an inherited
    // generic method would otherwise be counted as separate handlers).
    Arrays.stream(clazz.getDeclaredMethods())
        .filter(m -> !m.isSynthetic())
        .map(RuntimeMethodDef::new)
        .forEach(methods::add);

    // Methods inherited from the superclass hierarchy, matching CompileTimeTypeDef. Stops at
    // Object so e.g. an inherited @Get/@Post endpoint method is discovered the same way in both
    // modes.
    Class<?> superclass = clazz.getSuperclass();
    if (superclass != null && !superclass.equals(Object.class)) {
      methods.addAll(new RuntimeTypeDef(superclass).getMethods());
    }

    // sorted to ensure predictable output in tests
    return methods.stream().sorted().toList();
  }

  @Override
  public List<AnnotationDef> getAnnotations() {
    return Arrays.stream(clazz.getAnnotations())
        .map(RuntimeAnnotationDef::new)
        .map(a -> (AnnotationDef) a)
        .toList();
  }

  @Override
  public Optional<AnnotationDef> findAnnotation(String annotationName) {
    for (Annotation annotation : clazz.getAnnotations()) {
      if (getCanonicalClassName(annotation.annotationType()).equals(annotationName)) {
        return Optional.of(new RuntimeAnnotationDef(annotation));
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<TypeRefDef> getSuperclass() {
    Class<?> superclass = clazz.getSuperclass();
    if (superclass != null && !superclass.equals(Object.class)) {
      return Optional.of(new RuntimeTypeRefDef(superclass));
    }
    return Optional.empty();
  }

  @Override
  public boolean extendsType(String className) {
    Class<?> superclass = clazz.getSuperclass();
    if (superclass == null) {
      return false;
    }

    String superclassName = getCanonicalClassName(superclass);
    // Handle generic types - strip type parameters for comparison
    if (superclassName.equals(className)) {
      return true;
    }

    // Check recursively up the hierarchy
    return new RuntimeTypeDef(superclass).extendsType(className);
  }

  @Override
  public List<TypeRefDef> getPermittedSubclasses() {
    if (!isSealed()) {
      return Collections.emptyList();
    }
    Class<?>[] permittedSubclasses = clazz.getPermittedSubclasses();
    if (permittedSubclasses == null) {
      return Collections.emptyList();
    }
    return Arrays.stream(permittedSubclasses)
        .map(RuntimeTypeRefDef::new)
        .map(t -> (TypeRefDef) t)
        .toList();
  }

  @Override
  public List<TypeRefDef> getSuperclassTypeArguments() {
    Type genericSuperclass = clazz.getGenericSuperclass();
    if (genericSuperclass instanceof ParameterizedType paramType) {
      return Arrays.stream(paramType.getActualTypeArguments())
          .map(RuntimeTypeRefDef::new)
          .map(t -> (TypeRefDef) t)
          .toList();
    }
    return Collections.emptyList();
  }

  @Override
  public List<TypeDef> getNestedTypes() {
    List<TypeDef> nested = new java.util.ArrayList<>();
    Arrays.stream(clazz.getDeclaredClasses()).map(RuntimeTypeDef::new).forEach(nested::add);

    // Nested types inherited from the superclass hierarchy, matching CompileTimeTypeDef, so e.g. a
    // TableUpdater declared on a base View class is discovered the same way in both modes.
    Class<?> superclass = clazz.getSuperclass();
    if (superclass != null && !superclass.equals(Object.class)) {
      nested.addAll(new RuntimeTypeDef(superclass).getNestedTypes());
    }
    return nested;
  }

  @Override
  public boolean isStatic() {
    return Modifier.isStatic(clazz.getModifiers());
  }

  @Override
  public List<FieldDef> getFields() {
    return Arrays.stream(clazz.getDeclaredFields())
        .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
        .map(f -> (FieldDef) new RuntimeFieldDef(f))
        .toList();
  }
}
