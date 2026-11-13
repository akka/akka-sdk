/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/** Reads the routes of an endpoint class, with the effective ACL and JWT rule of each route. */
final class InventoryRoutes {

  private static final String HTTP_ENDPOINT_ANNOTATION =
      "akka.javasdk.annotations.http.HttpEndpoint";
  private static final String MCP_ENDPOINT_ANNOTATION = "akka.javasdk.annotations.mcp.McpEndpoint";
  private static final String ACL_ANNOTATION = "akka.javasdk.annotations.Acl";
  private static final String JWT_ANNOTATION = "akka.javasdk.annotations.JWT";

  private static final List<HttpVerb> HTTP_VERBS =
      List.of(
          new HttpVerb("akka.javasdk.annotations.http.Get", "GET", false),
          new HttpVerb("akka.javasdk.annotations.http.Post", "POST", false),
          new HttpVerb("akka.javasdk.annotations.http.Put", "PUT", false),
          new HttpVerb("akka.javasdk.annotations.http.Patch", "PATCH", false),
          new HttpVerb("akka.javasdk.annotations.http.Delete", "DELETE", false),
          new HttpVerb("akka.javasdk.annotations.http.WebSocket", "GET", true));

  private static final String PROTOCOL_HTTP = "http";
  private static final String PROTOCOL_GRPC = "grpc";
  private static final String PROTOCOL_MCP = "mcp";

  private static final String GRPC_SERVICE_NAME_FIELD = "name";

  private static final String DEFAULT_MCP_PATH = "/mcp";
  private static final int DEFAULT_HTTP_DENY_CODE = 403;
  private static final int DEFAULT_GRPC_DENY_CODE = 7;
  private static final int UNDECLARED_DENY_CODE = -1;

  private final Elements elements;
  private final Types types;
  private final Messager messager;

  InventoryRoutes(ProcessingEnvironment processingEnv) {
    this.elements = processingEnv.getElementUtils();
    this.types = processingEnv.getTypeUtils();
    this.messager = processingEnv.getMessager();
  }

  /**
   * Returns the routes of an endpoint class of the given kind, or an empty list for any other.
   * Reports a compile error and returns an empty list for an invalid gRPC endpoint class.
   */
  List<InventoryRoute> routesOf(TypeElement type, String kind) {
    return switch (kind) {
      case ComponentKinds.HTTP_ENDPOINT -> httpRoutes(type);
      case ComponentKinds.GRPC_ENDPOINT -> grpcRoutes(type);
      case ComponentKinds.MCP_ENDPOINT -> List.of(mcpRoute(type));
      default -> List.of();
    };
  }

  private List<InventoryRoute> httpRoutes(TypeElement type) {
    var className = elements.getBinaryName(type).toString();
    var prefix = httpPrefix(type);
    var classAcl = AnnotationValues.find(type, ACL_ANNOTATION);
    var classJwt = AnnotationValues.find(type, JWT_ANNOTATION);

    List<InventoryRoute> routes = new ArrayList<>();
    for (ExecutableElement method : ElementFilter.methodsIn(elements.getAllMembers(type))) {
      if (!method.getModifiers().contains(Modifier.PUBLIC)) {
        continue;
      }

      for (HttpVerb verb : HTTP_VERBS) {
        var verbAnnotation = AnnotationValues.find(method, verb.annotation());
        if (verbAnnotation.isPresent()) {
          var path = prefix + httpMethodPath(verbAnnotation.get());

          routes.add(
              new InventoryRoute(
                  PROTOCOL_HTTP,
                  className,
                  Optional.empty(),
                  Optional.of(method.getSimpleName().toString()),
                  Optional.of(verb.httpMethod()),
                  Optional.of(path),
                  verb.webSocket(),
                  acl(
                      AnnotationValues.find(method, ACL_ANNOTATION),
                      classAcl,
                      DEFAULT_HTTP_DENY_CODE),
                  jwt(AnnotationValues.find(method, JWT_ANNOTATION), classJwt)));
          break;
        }
      }
    }
    return routes;
  }

  private static String httpPrefix(TypeElement type) {
    var value =
        AnnotationValues.find(type, HTTP_ENDPOINT_ANNOTATION)
            .flatMap(mirror -> AnnotationValues.string(mirror, "value"));
    if (value.isEmpty()) {
      return "/";
    }

    var prefix = value.get();
    if (!prefix.startsWith("/")) {
      prefix = "/" + prefix;
    }
    if (!prefix.endsWith("/")) {
      prefix = prefix + "/";
    }
    return prefix;
  }

  private static String httpMethodPath(AnnotationMirror verbAnnotation) {
    var path = AnnotationValues.string(verbAnnotation, "value").orElse("");
    return path.startsWith("/") ? path.substring(1) : path;
  }

  private List<InventoryRoute> grpcRoutes(TypeElement type) {
    var className = elements.getBinaryName(type).toString();

    var interfaces = type.getInterfaces();
    if (interfaces.size() != 1) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "@GrpcEndpoint class ["
              + className
              + "] must implement exactly one interface, the gRPC service generated by Akka gRPC.",
          type);
      return List.of();
    }

    var serviceInterface = (TypeElement) types.asElement(interfaces.get(0));
    var service = grpcServiceName(serviceInterface);
    if (service.isEmpty()) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "The gRPC service interface ["
              + elements.getBinaryName(serviceInterface)
              + "] of @GrpcEndpoint class ["
              + className
              + "] has no 'name' String constant.",
          type);
      return List.of();
    }

    var classAcl = AnnotationValues.find(type, ACL_ANNOTATION);
    var classJwt = AnnotationValues.find(type, JWT_ANNOTATION);
    var annotatedMethods =
        ElementFilter.methodsIn(elements.getAllMembers(type)).stream()
            .filter(method -> method.getModifiers().contains(Modifier.PUBLIC))
            .filter(InventoryRoutes::hasAclOrJwt)
            .toList();

    List<InventoryRoute> routes = new ArrayList<>();
    for (ExecutableElement method :
        ElementFilter.methodsIn(serviceInterface.getEnclosedElements())) {
      if (!method.getModifiers().contains(Modifier.ABSTRACT)) {
        continue;
      }

      var methodName = method.getSimpleName();
      var annotatedMethod =
          annotatedMethods.stream()
              .filter(candidate -> candidate.getSimpleName().contentEquals(methodName))
              .findFirst();

      routes.add(
          new InventoryRoute(
              PROTOCOL_GRPC,
              className,
              service,
              Optional.of(grpcMethodName(methodName.toString())),
              Optional.empty(),
              Optional.empty(),
              false,
              acl(
                  annotatedMethod.flatMap(m -> AnnotationValues.find(m, ACL_ANNOTATION)),
                  classAcl,
                  DEFAULT_GRPC_DENY_CODE),
              jwt(
                  annotatedMethod.flatMap(m -> AnnotationValues.find(m, JWT_ANNOTATION)),
                  classJwt)));
    }
    return routes;
  }

  private static boolean hasAclOrJwt(ExecutableElement method) {
    return AnnotationValues.find(method, ACL_ANNOTATION).isPresent()
        || AnnotationValues.find(method, JWT_ANNOTATION).isPresent();
  }

  private static Optional<String> grpcServiceName(TypeElement serviceInterface) {
    return ElementFilter.fieldsIn(serviceInterface.getEnclosedElements()).stream()
        .filter(field -> field.getSimpleName().contentEquals(GRPC_SERVICE_NAME_FIELD))
        .map(VariableElement::getConstantValue)
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .findFirst();
  }

  private static String grpcMethodName(String javaMethodName) {
    return Character.toUpperCase(javaMethodName.charAt(0)) + javaMethodName.substring(1);
  }

  private InventoryRoute mcpRoute(TypeElement type) {
    var path =
        AnnotationValues.find(type, MCP_ENDPOINT_ANNOTATION)
            .flatMap(mirror -> AnnotationValues.string(mirror, "path"))
            .orElse(DEFAULT_MCP_PATH);

    return new InventoryRoute(
        PROTOCOL_MCP,
        elements.getBinaryName(type).toString(),
        Optional.empty(),
        Optional.empty(),
        Optional.of("POST"),
        Optional.of(path),
        false,
        acl(Optional.empty(), AnnotationValues.find(type, ACL_ANNOTATION), DEFAULT_HTTP_DENY_CODE),
        jwt(Optional.empty(), AnnotationValues.find(type, JWT_ANNOTATION)));
  }

  private static InventoryRoute.Acl acl(
      Optional<AnnotationMirror> methodAcl,
      Optional<AnnotationMirror> classAcl,
      int defaultDenyCode) {
    var effective = methodAcl.or(() -> classAcl);
    if (effective.isEmpty()) {
      return new InventoryRoute.Acl(true, List.of(), List.of(), defaultDenyCode);
    }

    var acl = effective.get();
    var denyCode =
        AnnotationValues.integer(acl, "denyCode")
            .filter(code -> code != UNDECLARED_DENY_CODE)
            .orElse(defaultDenyCode);
    return new InventoryRoute.Acl(false, matchers(acl, "allow"), matchers(acl, "deny"), denyCode);
  }

  private static List<InventoryRoute.Matcher> matchers(AnnotationMirror acl, String name) {
    return AnnotationValues.mirrors(acl, name).stream().map(InventoryRoutes::matcher).toList();
  }

  private static InventoryRoute.Matcher matcher(AnnotationMirror matcher) {
    var principal =
        AnnotationValues.enumConstant(matcher, "principal")
            .filter(value -> !value.equals("UNSPECIFIED"));
    if (principal.isPresent()) {
      return new InventoryRoute.Matcher("principal", principal.get(), false);
    }

    var spiffe = AnnotationValues.string(matcher, "spiffe");
    if (spiffe.isPresent()) {
      return new InventoryRoute.Matcher("spiffe", spiffe.get(), false);
    }

    var parentProject =
        AnnotationValues.enumConstant(matcher, "scope")
            .filter("PARENT_PROJECT"::equals)
            .isPresent();
    return new InventoryRoute.Matcher(
        "service", AnnotationValues.string(matcher, "service").orElse(""), parentProject);
  }

  private static Optional<InventoryRoute.Jwt> jwt(
      Optional<AnnotationMirror> methodJwt, Optional<AnnotationMirror> classJwt) {
    return methodJwt
        .or(() -> classJwt)
        .map(
            jwt ->
                new InventoryRoute.Jwt(
                    AnnotationValues.enumConstants(jwt, "validate").contains("BEARER_TOKEN"),
                    AnnotationValues.strings(jwt, "bearerTokenIssuers"),
                    AnnotationValues.mirrors(jwt, "staticClaims").stream()
                        .map(InventoryRoutes::claim)
                        .toList()));
  }

  private static InventoryRoute.Claim claim(AnnotationMirror claim) {
    return new InventoryRoute.Claim(
        AnnotationValues.string(claim, "claim").orElse(""),
        AnnotationValues.strings(claim, "values"),
        AnnotationValues.string(claim, "pattern"));
  }

  /** An HTTP verb annotation with the HTTP method it serves. */
  private record HttpVerb(String annotation, String httpMethod, boolean webSocket) {}
}
