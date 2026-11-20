/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import akka.javasdk.validation.ast.AnnotationDef;
import akka.javasdk.validation.ast.TypeDef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Validation of A2A endpoints and of the {@code @A2aSkill} annotations on agents. */
public class A2aValidations {

  static final String A2A_ENDPOINT_ANNOTATION = "akka.javasdk.annotations.a2a.A2aEndpoint";
  private static final String A2A_SKILL_ANNOTATION = "akka.javasdk.annotations.a2a.A2aSkill";
  private static final String A2A_SKILLS_ANNOTATION = "akka.javasdk.annotations.a2a.A2aSkills";
  private static final String ABSTRACT_A2A_ENDPOINT = "akka.javasdk.a2a.AbstractA2aEndpoint";
  private static final String AGENT = "akka.javasdk.agent.Agent";

  private static final Pattern SKILL_ID = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

  /**
   * Validates a class annotated with {@code @A2aEndpoint}.
   *
   * @param typeDef the endpoint class
   * @return a Validation result indicating success or failure
   */
  public static Validation validateEndpoint(TypeDef typeDef) {
    List<String> errors = new ArrayList<>();
    if (!typeDef.extendsType(ABSTRACT_A2A_ENDPOINT)) {
      errors.add(
          Validations.errorMessage(
              typeDef, "A class annotated with @A2aEndpoint must extend AbstractA2aEndpoint."));
    }
    if (!typeDef.isPublic()) {
      errors.add(Validations.errorMessage(typeDef, "An A2A endpoint must be public."));
    }
    typeDef
        .findAnnotation(A2A_ENDPOINT_ANNOTATION)
        .ifPresent(
            endpoint -> {
              if (endpoint.getStringValue("name").orElse("").isBlank()) {
                errors.add(
                    Validations.errorMessage(typeDef, "@A2aEndpoint name must not be empty."));
              }
              if (endpoint.getStringValue("version").orElse("").isBlank()) {
                errors.add(
                    Validations.errorMessage(typeDef, "@A2aEndpoint version must not be empty."));
              }
              if (!endpoint.getStringValue("path").orElse("/a2a").startsWith("/")) {
                errors.add(
                    Validations.errorMessage(typeDef, "@A2aEndpoint path must start with '/'."));
              }
              var publicUrl = endpoint.getStringValue("publicUrl").orElse("");
              if (!publicUrl.isEmpty()
                  && !publicUrl.startsWith("https://")
                  && !publicUrl.startsWith("http://")) {
                errors.add(
                    Validations.errorMessage(
                        typeDef, "@A2aEndpoint publicUrl must start with https:// or http://."));
              }
            });
    return Validation.of(errors);
  }

  /**
   * Validates the {@code @A2aSkill} annotations of a component: they are only allowed on agents,
   * and their ids are valid and unique within the class.
   *
   * @param typeDef the component class
   * @return a Validation result indicating success or failure
   */
  public static Validation validateSkills(TypeDef typeDef) {
    var skills = skills(typeDef);
    if (skills.isEmpty()) {
      return Validation.Valid.instance();
    }
    if (!typeDef.extendsType(AGENT)) {
      return Validation.of(
          Validations.errorMessage(
              typeDef, "@A2aSkill is only allowed on classes that extend Agent."));
    }
    List<String> errors = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (AnnotationDef skill : skills) {
      var id = skill.getStringValue("id").orElse("");
      if (!SKILL_ID.matcher(id).matches()) {
        errors.add(
            Validations.errorMessage(
                typeDef,
                "@A2aSkill id ["
                    + id
                    + "] is invalid. Use 1 to 64 letters, digits, '_', '-' or '.'."));
      } else if (!seen.add(id)) {
        errors.add(
            Validations.errorMessage(typeDef, "@A2aSkill id [" + id + "] is used more than once."));
      }
    }
    return Validation.of(errors);
  }

  /**
   * Finds skill ids used by more than one class. A service publishes all its skills in one Agent
   * Card, so skill ids are unique within the service.
   *
   * @param typeDefs the component classes of the service
   * @return an error message per class that shares a skill id, keyed by qualified class name
   */
  public static Map<String, String> duplicateSkillIdErrors(Collection<? extends TypeDef> typeDefs) {
    Map<String, Set<String>> classesBySkillId = new TreeMap<>();
    for (TypeDef typeDef : typeDefs) {
      for (AnnotationDef skill : skills(typeDef)) {
        skill
            .getStringValue("id")
            .ifPresent(
                id ->
                    classesBySkillId
                        .computeIfAbsent(id, key -> new java.util.TreeSet<>())
                        .add(typeDef.getQualifiedName()));
      }
    }
    Map<String, String> errors = new LinkedHashMap<>();
    classesBySkillId.forEach(
        (id, classes) -> {
          if (classes.size() > 1) {
            for (String className : classes) {
              errors.putIfAbsent(
                  className,
                  "On '"
                      + className
                      + "': @A2aSkill id ["
                      + id
                      + "] is not unique. It is also used by ["
                      + String.join(
                          ", ", classes.stream().filter(c -> !c.equals(className)).toList())
                      + "].");
            }
          }
        });
    return errors;
  }

  private static List<AnnotationDef> skills(TypeDef typeDef) {
    List<AnnotationDef> skills = new ArrayList<>();
    typeDef.findAnnotation(A2A_SKILL_ANNOTATION).ifPresent(skills::add);
    typeDef
        .findAnnotation(A2A_SKILLS_ANNOTATION)
        .ifPresent(container -> skills.addAll(container.getAnnotationArrayValue("value")));
    return skills;
  }
}
