/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.validation.ast.runtime;

import akka.javasdk.validation.ast.TypeDef;
import akka.javasdk.validation.ast.TypeRefDef;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Runtime implementation of TypeRefDef backed by java.lang.reflect.Type.
 *
 * <p>Note: This implementation normalizes class names to use '.' instead of '$' for nested classes,
 * matching the compile-time representation from javax.lang.model.
 *
 * @param type the runtime type
 */
public record RuntimeTypeRefDef(Type type) implements TypeRefDef {

  /**
   * Convenience constructor for Class types.
   *
   * @param clazz the class to wrap
   */
  public RuntimeTypeRefDef(Class<?> clazz) {
    this((Type) clazz);
  }

  /**
   * Gets the canonical name for a class, handling array types and nested classes.
   *
   * <p>Uses Class.getCanonicalName() which returns proper format for arrays (e.g., "byte[]" instead
   * of "[B") and for nested classes uses '.' instead of '$'.
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
  public String getQualifiedName() {
    if (type instanceof Class<?> clazz) {
      return getCanonicalClassName(clazz);
    } else if (type instanceof ParameterizedType paramType) {
      Type rawType = paramType.getRawType();
      if (rawType instanceof Class<?> rawClass) {
        StringBuilder sb = new StringBuilder(getCanonicalClassName(rawClass));
        Type[] typeArgs = paramType.getActualTypeArguments();
        if (typeArgs.length > 0) {
          sb.append('<');
          for (int i = 0; i < typeArgs.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(new RuntimeTypeRefDef(typeArgs[i]).getQualifiedName());
          }
          sb.append('>');
        }
        return sb.toString();
      }
    }
    // For other Type implementations, use getTypeName and normalize
    return type.getTypeName().replace('$', '.');
  }

  @Override
  public String getRawQualifiedName() {
    if (type instanceof Class<?> clazz) {
      return getCanonicalClassName(clazz);
    } else if (type instanceof ParameterizedType paramType) {
      Type rawType = paramType.getRawType();
      if (rawType instanceof Class<?> rawClass) {
        return getCanonicalClassName(rawClass);
      }
    }
    // Fallback: strip generic parameters from the type name
    String name = type.getTypeName();
    int genericStart = name.indexOf('<');
    if (genericStart > 0) {
      return name.substring(0, genericStart).replace('$', '.');
    }
    return name.replace('$', '.');
  }

  @Override
  public boolean isGeneric() {
    return type instanceof ParameterizedType;
  }

  @Override
  public List<TypeRefDef> getTypeArguments() {
    if (type instanceof ParameterizedType paramType) {
      return Arrays.stream(paramType.getActualTypeArguments())
          .map(RuntimeTypeRefDef::new)
          .map(t -> (TypeRefDef) t)
          .toList();
    }
    return Collections.emptyList();
  }

  @Override
  public Optional<TypeDef> resolveTypeDef() {
    if (type instanceof Class<?> clazz) {
      return Optional.of(new RuntimeTypeDef(clazz));
    } else if (type instanceof ParameterizedType paramType) {
      Type rawType = paramType.getRawType();
      if (rawType instanceof Class<?> rawClass) {
        return Optional.of(new RuntimeTypeDef(rawClass));
      }
    }
    return Optional.empty();
  }
}
