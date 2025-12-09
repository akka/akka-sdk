/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Represents the result of a validation operation. Can be either Valid (no errors) or Invalid
 * (contains error messages).
 */
public sealed interface Validation permits Validation.Valid, Validation.Invalid {

  /**
   * Creates a Validation from an array of messages. Returns Valid if messages is empty, otherwise
   * Invalid with those messages.
   */
  static Validation of(String[] messages) {
    return of(Arrays.asList(messages));
  }

  /**
   * Creates a Validation from a list of messages. Returns Valid if messages is empty, otherwise
   * Invalid with those messages.
   */
  static Validation of(List<String> messages) {
    if (messages.isEmpty()) {
      return Valid.instance();
    } else {
      return new Invalid(messages);
    }
  }

  /** Creates an Invalid validation with a single message. */
  static Validation of(String message) {
    return new Invalid(List.of(message));
  }

  /** Returns true if this validation is successful (no errors). */
  boolean isValid();

  /** Returns true if this validation has errors. */
  default boolean isInvalid() {
    return !isValid();
  }

  /**
   * Combines this validation with another. If both are valid, returns Valid. If either is invalid,
   * returns Invalid with combined messages.
   */
  Validation combine(Validation other);

  Validation or(Validation other);

  /** Represents a successful validation with no errors. */
  final class Valid implements Validation {

    private static final Valid INSTANCE = new Valid();

    private Valid() {
      // Private constructor to enforce a singleton pattern
    }

    /** Returns the singleton instance of Valid. */
    public static Valid instance() {
      return INSTANCE;
    }

    @Override
    public boolean isValid() {
      return true;
    }

    @Override
    public Validation combine(Validation other) {
      // Valid combined with anything returning that other validation
      return other;
    }

    @Override
    public Validation or(Validation other) {
      return combine(other);
    }

    @Override
    public String toString() {
      return "Valid";
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Valid;
    }

    @Override
    public int hashCode() {
      return Valid.class.hashCode();
    }
  }

  /**
   * Represents a failed validation containing one or more error messages.
   *
   * @param messages the list of error messages
   */
  record Invalid(List<String> messages) implements Validation {

    /** Creates an Invalid validation with the given error messages. */
    public Invalid(List<String> messages) {
      if (messages == null || messages.isEmpty()) {
        throw new IllegalArgumentException("Invalid must have at least one message");
      }
      this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
    }

    /** Creates an Invalid validation with a single error message. */
    public Invalid(String message) {
      this(List.of(message));
    }

    /** Returns the list of error messages. */
    @Override
    public List<String> messages() {
      return messages;
    }

    @Override
    public boolean isValid() {
      return false;
    }

    @Override
    public Validation combine(Validation other) {
      if (other instanceof Valid) {
        return this;
      } else if (other instanceof Invalid(List<String> otherMessages)) {
        List<String> combinedMessages = new ArrayList<>(this.messages);
        combinedMessages.addAll(otherMessages);
        return new Invalid(combinedMessages);
      }
      return this;
    }

    @Override
    public Validation or(Validation other) {
      return this;
    }

    /** Throws a RuntimeException with all error messages joined by ", ". */
    public void throwFailureSummary() {
      throw new RuntimeException(String.join(", ", messages));
    }

    @Override
    public String toString() {
      return "Invalid(" + String.join(", ", messages) + ")";
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Invalid(List<String> otherMessages))) return false;
      return messages.equals(otherMessages);
    }
  }
}
