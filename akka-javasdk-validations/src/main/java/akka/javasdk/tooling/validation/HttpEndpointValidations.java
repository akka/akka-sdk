/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import akka.javasdk.validation.ast.MethodDef;
import akka.javasdk.validation.ast.TypeDef;
import akka.javasdk.validation.ast.TypeRefDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HttpEndpointValidations {

  private static final String WEBSOCKET_ANNOTATION = "akka.javasdk.annotations.http.WebSocket";
  private static final String HTTP_ENDPOINT_ANNOTATION =
      "akka.javasdk.annotations.http.HttpEndpoint";
  private static final String FLOW_TYPE = "akka.stream.javadsl.Flow";
  private static final String NOT_USED_TYPE = "akka.NotUsed";

  private static final Set<String> SUPPORTED_MESSAGE_TYPES =
      Set.of("java.lang.String", "akka.util.ByteString", "akka.http.javadsl.model.ws.Message");

  /**
   * @param typeDef the HTTP endpoint class to validate
   */
  public static Validation validate(TypeDef typeDef) {
    if (!typeDef.hasAnnotation(HTTP_ENDPOINT_ANNOTATION)) {
      return Validation.Valid.instance();
    }

    return validateWebSocketMethods(typeDef);
  }

  private static Validation validateWebSocketMethods(TypeDef typeDef) {
    List<String> errors = new ArrayList<>();

    for (MethodDef method : typeDef.getPublicMethods()) {
      if (method.hasAnnotation(WEBSOCKET_ANNOTATION)) {
        errors.addAll(validateWebSocketMethod(method));
      }
    }

    return Validation.of(errors);
  }

  private static List<String> validateWebSocketMethod(MethodDef method) {
    List<String> errors = new ArrayList<>();
    TypeRefDef returnType = method.getReturnType();
    String returnTypeName = returnType.getRawQualifiedName();

    if (!returnTypeName.equals(FLOW_TYPE)) {
      errors.add(
          Validations.errorMessage(
              method,
              "WebSocket method must return akka.stream.javadsl.Flow but returns '"
                  + returnTypeName
                  + "'."));
      return errors;
    }

    if (!returnType.isGeneric()) {
      errors.add(
          Validations.errorMessage(
              method, "WebSocket method must return a parameterized Flow type."));
      return errors;
    }

    List<TypeRefDef> typeArgs = returnType.getTypeArguments();
    if (typeArgs.size() != 3) {
      // Note: can't really happen because flow above
      errors.add(
          Validations.errorMessage(
              method,
              "WebSocket method's Flow must have exactly 3 type parameters <In, Out, Mat>."));
      return errors;
    }

    TypeRefDef inType = typeArgs.get(0);
    TypeRefDef outType = typeArgs.get(1);
    TypeRefDef matType = typeArgs.get(2);

    String inTypeName = inType.getQualifiedName();
    String outTypeName = outType.getQualifiedName();
    String matTypeName = matType.getQualifiedName();

    // Check in and out types are the same
    if (!inTypeName.equals(outTypeName)) {
      errors.add(
          Validations.errorMessage(
              method,
              "WebSocket method's Flow must have the same input and output message types. "
                  + "Found input type '"
                  + inTypeName
                  + "' and output type '"
                  + outTypeName
                  + "'."));
    }

    // Check message type is supported
    if (!SUPPORTED_MESSAGE_TYPES.contains(inTypeName)) {
      errors.add(
          Validations.errorMessage(
              method,
              "WebSocket method has unsupported message type '"
                  + inTypeName
                  + "'. "
                  + "Supported types are: String (for text), akka.util.ByteString (for binary), "
                  + "or akka.http.javadsl.model.ws.Message (for low level protocol handling)."));
    }

    // Check materialized value is NotUsed
    if (!matTypeName.equals(NOT_USED_TYPE)) {
      errors.add(
          Validations.errorMessage(
              method,
              "WebSocket method's Flow must have akka.NotUsed as materialized value type "
                  + "but found '"
                  + matTypeName
                  + "'."));
    }

    return errors;
  }
}
