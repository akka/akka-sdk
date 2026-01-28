/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import akka.javasdk.validation.ast.AnnotationDef;
import akka.javasdk.validation.ast.MethodDef;
import akka.javasdk.validation.ast.ParameterDef;
import akka.javasdk.validation.ast.TypeDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Contains compile-time validation logic for HTTP Endpoint components. */
public class HttpEndpointValidations {

  private static final String HTTP_ENDPOINT_ANNOTATION =
      "akka.javasdk.annotations.http.HttpEndpoint";
  private static final String GET_ANNOTATION = "akka.javasdk.annotations.http.Get";
  private static final String POST_ANNOTATION = "akka.javasdk.annotations.http.Post";
  private static final String PUT_ANNOTATION = "akka.javasdk.annotations.http.Put";
  private static final String PATCH_ANNOTATION = "akka.javasdk.annotations.http.Patch";
  private static final String DELETE_ANNOTATION = "akka.javasdk.annotations.http.Delete";

  // Pattern to match path variables like {id} or {name}
  private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("\\{([^}]+)}");

  /**
   * Validates an HTTP Endpoint component.
   *
   * @param typeDef the HTTP Endpoint class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validate(TypeDef typeDef) {
    if (!typeDef.hasAnnotation(HTTP_ENDPOINT_ANNOTATION)) {
      return Validation.Valid.instance();
    }

    // Note: public modifier validation is done by componentMustBePublic in Validations
    return validateHttpMethods(typeDef);
  }

  /**
   * Validates all HTTP methods in the endpoint class.
   *
   * @param typeDef the HTTP Endpoint class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation validateHttpMethods(TypeDef typeDef) {
    String mainPath = getMainPath(typeDef);
    List<String> errors = new ArrayList<>();

    for (MethodDef method : typeDef.getMethods()) {
      Optional<String> httpMethodPath = getHttpMethodPath(method);
      if (httpMethodPath.isPresent()) {
        String methodPath = httpMethodPath.get();
        String fullPath = combinePaths(mainPath, methodPath);

        List<String> methodErrors = validateMethodPath(typeDef, method, fullPath);
        errors.addAll(methodErrors);
      }
    }

    return Validation.of(errors);
  }

  /**
   * Gets the main path from the @HttpEndpoint annotation.
   *
   * @param typeDef the HTTP Endpoint class
   * @return the main path, normalized to always start and end with /
   */
  private static String getMainPath(TypeDef typeDef) {
    Optional<AnnotationDef> annotation = typeDef.findAnnotation(HTTP_ENDPOINT_ANNOTATION);
    if (annotation.isEmpty()) {
      return "/";
    }

    Optional<String> value = annotation.get().getStringValue("value");
    if (value.isEmpty() || value.get().isEmpty()) {
      return "/";
    }

    String path = value.get();
    // Normalize: always starts with slash
    if (!path.startsWith("/")) {
      path = "/" + path;
    }
    // Normalize: always ends with slash
    if (!path.endsWith("/")) {
      path = path + "/";
    }
    return path;
  }

  /**
   * Gets the path from an HTTP method annotation if present.
   *
   * @param method the method to check
   * @return the path if an HTTP method annotation is present, empty otherwise
   */
  private static Optional<String> getHttpMethodPath(MethodDef method) {
    // Check for each HTTP method annotation
    for (String annotationName :
        List.of(
            GET_ANNOTATION, POST_ANNOTATION, PUT_ANNOTATION, PATCH_ANNOTATION, DELETE_ANNOTATION)) {
      Optional<AnnotationDef> annotation = method.findAnnotation(annotationName);
      if (annotation.isPresent()) {
        Optional<String> value = annotation.get().getStringValue("value");
        return Optional.of(value.orElse(""));
      }
    }
    return Optional.empty();
  }

  /**
   * Combines the main path and method path.
   *
   * @param mainPath the main path from @HttpEndpoint
   * @param methodPath the path from the HTTP method annotation
   * @return the combined full path
   */
  private static String combinePaths(String mainPath, String methodPath) {
    // Method paths are relative to the prefix, remove leading slash if present
    if (methodPath.startsWith("/")) {
      methodPath = methodPath.substring(1);
    }
    return mainPath + methodPath;
  }

  /**
   * Validates the path expression against the method parameters.
   *
   * @param typeDef the HTTP Endpoint class
   * @param method the method to validate
   * @param fullPathExpression the full path expression
   * @return list of error messages, empty if valid
   */
  private static List<String> validateMethodPath(
      TypeDef typeDef, MethodDef method, String fullPathExpression) {

    List<String> errors = new ArrayList<>();
    List<ParameterDef> parameters = method.getParameters();
    List<String> parameterNames = parameters.stream().map(ParameterDef::getName).toList();

    // Extract path variables from the path expression
    List<String> pathVariables = extractPathVariables(fullPathExpression);

    // Check for wildcard path not at the end
    errors.addAll(validateWildcardPosition(fullPathExpression));

    // Validate path variables against method parameters
    int paramIndex = 0;
    for (String pathVariable : pathVariables) {
      if (paramIndex >= parameterNames.size()) {
        errors.add(
            "There are more parameters in the path expression ["
                + fullPathExpression
                + "] than there are parameters for ["
                + typeDef.getQualifiedName()
                + "."
                + method.getName()
                + "]");
        break;
      }

      String paramName = parameterNames.get(paramIndex);
      if (!pathVariable.equals(paramName)) {
        errors.add(
            "The parameter ["
                + pathVariable
                + "] in the path expression ["
                + fullPathExpression
                + "] does not match the method parameter name ["
                + paramName
                + "] for ["
                + typeDef.getQualifiedName()
                + "."
                + method.getName()
                + "]. The parameter names in the expression must match the parameters of the"
                + " method.");
      }
      paramIndex++;
    }

    // After matching all path variables, check if there are too many remaining parameters
    // (more than one - which would be the request body)
    int remainingParams = parameterNames.size() - paramIndex;
    if (remainingParams > 1) {
      List<String> unmatchedParams = parameterNames.subList(paramIndex, parameterNames.size());
      errors.add(
          "There are ["
              + remainingParams
              + "] parameters (["
              + String.join(",", unmatchedParams)
              + "]) for endpoint method ["
              + typeDef.getQualifiedName()
              + "."
              + method.getName()
              + "] not matched by the path expression. The parameter count and names should match"
              + " the expression, with one additional possible parameter in the end for the request"
              + " body.");
    }

    return errors;
  }

  /**
   * Extracts path variable names from a path expression.
   *
   * @param pathExpression the path expression
   * @return list of path variable names in order
   */
  private static List<String> extractPathVariables(String pathExpression) {
    List<String> variables = new ArrayList<>();
    Matcher matcher = PATH_VARIABLE_PATTERN.matcher(pathExpression);
    while (matcher.find()) {
      variables.add(matcher.group(1));
    }
    return variables;
  }

  /**
   * Validates that wildcard ** is only at the end of the path.
   *
   * @param pathExpression the path expression to validate
   * @return list of error messages, empty if valid
   */
  private static List<String> validateWildcardPosition(String pathExpression) {
    List<String> errors = new ArrayList<>();

    // Split by / and check if ** appears in any position that's not the last
    String[] segments = pathExpression.split("/");
    for (int i = 0; i < segments.length; i++) {
      if (segments[i].equals("**") && i < segments.length - 1) {
        errors.add(
            "Wildcard path can only be the last segment of the path [" + pathExpression + "]");
        break;
      }
    }

    return errors;
  }
}
