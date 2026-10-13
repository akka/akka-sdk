/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.validation.ast;

import java.util.List;
import java.util.Optional;

/**
 * Represents an annotation in the validation AST. This can be backed by either compile-time
 * (javax.lang.model.element.AnnotationMirror) or runtime (java.lang.annotation.Annotation)
 * representations.
 */
public interface AnnotationDef {

  /**
   * Returns the fully qualified name of the annotation type.
   *
   * <p>For example, for {@code @Component}, this returns "akka.javasdk.annotations.Component".
   *
   * @return the fully qualified annotation type name
   */
  String getAnnotationType();

  /**
   * Gets a string value from an annotation attribute.
   *
   * @param attributeName the name of the attribute
   * @return the string value, or empty if not found or not a string
   */
  Optional<String> getStringValue(String attributeName);

  /**
   * Gets a boolean value from an annotation attribute.
   *
   * @param attributeName the name of the attribute
   * @return the boolean value, or empty if not found or not a boolean
   */
  Optional<Boolean> getBooleanValue(String attributeName);

  /**
   * Gets a class type reference from an annotation attribute.
   *
   * <p>For example, for {@code @Consume.FromKeyValueEntity(value = MyEntity.class)}, calling {@code
   * getClassValue("value")} returns the type reference for {@code MyEntity}.
   *
   * @param attributeName the name of the attribute
   * @return the type reference, or empty if not found or not a class value
   */
  Optional<TypeRefDef> getClassValue(String attributeName);

  /**
   * Gets an array of nested annotations from an annotation attribute.
   *
   * <p>For example, for {@code @Acl(allow = {@Acl.Matcher(...), @Acl.Matcher(...)})}, calling
   * {@code getAnnotationArrayValue("allow")} returns one AnnotationDef per matcher.
   *
   * @param attributeName the name of the attribute
   * @return the nested annotations, or an empty list if not found or not an annotation array
   */
  List<AnnotationDef> getAnnotationArrayValue(String attributeName);
}
