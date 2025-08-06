package com.example.application;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

// tag::result-type[]
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes(
  {
    @JsonSubTypes.Type(value = Result.Success.class, name = "S"),
    @JsonSubTypes.Type(value = Result.Error.class, name = "E"),
  }
)
public sealed interface Result<E, T> { // <1>
  record Success<E, T>(
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS) // <2>
    T value
  )
    implements Result<E, T> {
    // end::result-type[]
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
    // tag::result-type[]
  }

  record Error<E, T>(
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS) // <2>
    E value
  )
    implements Result<E, T> {
    // end::result-type[]
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
    // tag::result-type[]
  }

  // end::result-type[]

  @JsonIgnore
  E error();

  @JsonIgnore
  T success();

  @JsonIgnore
  boolean isSuccess();
  // tag::result-type[]
}
// end::result-type[]
