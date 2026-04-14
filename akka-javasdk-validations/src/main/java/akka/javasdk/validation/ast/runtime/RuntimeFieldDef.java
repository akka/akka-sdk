/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.validation.ast.runtime;

import akka.javasdk.validation.ast.FieldDef;
import akka.javasdk.validation.ast.TypeRefDef;
import java.lang.reflect.Field;

/**
 * Runtime implementation of FieldDef backed by java.lang.reflect.Field.
 *
 * @param field the runtime field
 */
public record RuntimeFieldDef(Field field) implements FieldDef {

  @Override
  public String getName() {
    return field.getName();
  }

  @Override
  public TypeRefDef getType() {
    return new RuntimeTypeRefDef(field.getGenericType());
  }
}
