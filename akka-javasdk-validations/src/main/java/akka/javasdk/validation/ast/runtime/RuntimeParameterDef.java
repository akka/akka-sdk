/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.validation.ast.runtime;

import akka.javasdk.validation.ast.ParameterDef;
import akka.javasdk.validation.ast.TypeRefDef;
import java.lang.reflect.Parameter;

/**
 * Runtime implementation of ParameterDef backed by java.lang.reflect.Parameter.
 *
 * @param parameter the runtime parameter
 */
public record RuntimeParameterDef(Parameter parameter) implements ParameterDef {

  @Override
  public TypeRefDef getType() {
    return new RuntimeTypeRefDef(parameter.getParameterizedType());
  }

  @Override
  public String getName() {
    return parameter.getName();
  }
}
