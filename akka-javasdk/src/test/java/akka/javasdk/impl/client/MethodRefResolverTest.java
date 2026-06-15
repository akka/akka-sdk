/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.japi.function.Function;
import akka.japi.function.Function2;
import org.junit.jupiter.api.Test;

class MethodRefResolverTest {

  static class SomeEntity {
    public String getState() {
      return "state";
    }

    public String processCommand(String cmd) {
      return cmd;
    }
  }

  @Test
  void resolveNoArgJavaMethodRef() throws Exception {
    // Java method reference: SerializedLambda.implMethodName = "getState" (no $anonfun$ wrapper)
    Function<SomeEntity, String> ref = SomeEntity::getState;
    var method = MethodRefResolver.resolveMethodRef(ref);
    assertThat(method.getName()).isEqualTo("getState");
    assertThat(method.getDeclaringClass()).isEqualTo(SomeEntity.class);
    assertThat(method.getParameterCount()).isEqualTo(0);
  }

  @Test
  void resolveOneArgJavaMethodRef() throws Exception {
    // Java method reference with the entity as first arg and a command as second arg
    Function2<SomeEntity, String, String> ref = SomeEntity::processCommand;
    var method = MethodRefResolver.resolveMethodRef(ref);
    assertThat(method.getName()).isEqualTo("processCommand");
    assertThat(method.getDeclaringClass()).isEqualTo(SomeEntity.class);
    assertThat(method.getParameterTypes()).containsExactly(String.class);
  }

  @Test
  void rejectNonSerializable() {
    // java.util.function.Function is not Serializable
    assertThatThrownBy(
            () ->
                MethodRefResolver.resolveMethodRef(
                    (java.util.function.Function<SomeEntity, String>) SomeEntity::getState))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("serializable");
  }
}
