/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = Result.Success.class, name = "S"),
  @JsonSubTypes.Type(value = Result.Error.class, name = "E")})
public sealed interface Result<E, T> {

  record Success<E, T>(
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    T value
  ) implements Result<E, T> {
    @Override
    public E error() {
      throw new IllegalStateException("Result is not an error");
    }

    @Override
    public T success() {
      return value;
    }

    @Override
    public boolean isSuccess() {
      return true;
    }
  }

  record Error<E, T>(
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    E value
  ) implements Result<E, T> {
    @Override
    public E error() {
      return value;
    }

    @Override
    public T success() {
      throw new IllegalStateException("Result is not success");
    }

    @Override
    public boolean isSuccess() {
      return false;
    }
  }

  @JsonIgnore
  E error();

  @JsonIgnore
  T success();

  @JsonIgnore
  boolean isSuccess();
}
