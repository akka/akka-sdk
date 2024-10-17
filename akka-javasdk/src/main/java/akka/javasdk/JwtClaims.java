/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Representation of JWT claims that have been validated and extracted from the bearer token of a
 * request.
 */
public interface JwtClaims {
  /**
   * Returns the names of all the claims in this request.
   *
   * @return The names of all the claims in this request.
   */
  Iterable<String> allClaimNames();

  /**
   * Returns all the claims as a map of strings to strings.
   *
   * <p>If the claim is a String claim, the value will be the raw String. For all other types, it
   * will be the value of the claim encoded to JSON.
   *
   * @return All the claims represented as a map of string claim names to string values.
   */
  Map<String, String> asMap();

  /**
   * Does this request have any claims that have been validated?
   *
   * @return true if there are claims.
   */
  boolean hasClaims();

  /**
   * Get the issuer, that is, the <tt>iss</tt> claim, as described in RFC 7519 section 4.1.1.
   *
   * @return the issuer, if present.
   * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.1">RFC 7519 section
   *     4.1.1</a>
   */
  Optional<String> issuer();

  /**
   * Get the subject, that is, the <tt>sub</tt> claim, as described in RFC 7519 section 4.1.2.
   *
   * @return the subject, if present.
   * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.2">RFC 7519 section
   *     4.1.2</a>
   */
  Optional<String> subject();

  /**
   * Get the audience, that is, the <tt>aud</tt> claim, as described in RFC 7519 section 4.1.3.
   *
   * @return the audience, if present.
   * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.3">RFC 7519 section
   *     4.1.3</a>
   */
  Optional<String> audience();

  /**
   * Get the expiration time, that is, the <tt>exp</tt> claim, as described in RFC 7519 section
   * 4.1.4.
   *
   * @return the expiration time, if present. Returns empty if the value is not a numeric date.
   * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.4">RFC 7519 section
   *     4.1.4</a>
   */
  Optional<Instant> expirationTime();

  /**
   * Get the not before, that is, the <tt>nbf</tt> claim, as described in RFC 7519 section 4.1.5.
   *
   * @return the not before, if present. Returns empty if the value is not a numeric date.
   * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.5">RFC 7519 section
   *     4.1.5</a>
   */
  Optional<Instant> notBefore();

  /**
   * Get the issued at, that is, the <tt>iat</tt> claim, as described in RFC 7519 section 4.1.6.
   *
   * @return the issued at, if present. Returns empty if the value is not a numeric date.
   * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.6">RFC 7519 section
   *     4.1.6</a>
   */
  Optional<Instant> issuedAt();

  /**
   * Get the JWT ID, that is, the <tt>jti</tt> claim, as described in RFC 7519 section 4.1.7.
   *
   * @return the JWT ID, if present.
   * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.7">RFC 7519 section
   *     4.1.7</a>
   */
  Optional<String> jwtId();

  /**
   * Get the string claim with the given name.
   *
   * <p>Note that if the claim with the given name is not a string claim, this will return the JSON
   * encoding of it.
   *
   * @param name The name of the claim.
   * @return The string claim, if present.
   */
  Optional<String> getString(String name);

  /**
   * Get the integer claim with the given name.
   *
   * @param name The name of the claim.
   * @return The integer claim, if present. Returns empty if the claim is not an integer or can't be
   *     parsed as an integer.
   */
  Optional<Integer> getInteger(String name);

  /**
   * Get the long claim with the given name.
   *
   * @param name The name of the claim.
   * @return The long claim, if present. Returns empty if the claim is not a long or can't be parsed
   *     as an long.
   */
  Optional<Long> getLong(String name);

  /**
   * Get the double claim with the given name.
   *
   * @param name The name of the claim.
   * @return The double claim, if present. Returns empty if the claim is not a double or can't be
   *     parsed as an double.
   */
  Optional<Double> getDouble(String name);

  /**
   * Get the boolean claim with the given name.
   *
   * @param name The name of the claim.
   * @return The boolean claim, if present. Returns empty if the claim is not a boolean or can't be
   *     parsed as a boolean.
   */
  Optional<Boolean> getBoolean(String name);

  /**
   * Get the numeric data claim with the given name.
   *
   * <p>Numeric dates are expressed as a number of seconds since epoch, as described in RFC 7519
   * section 2.
   *
   * @param name The name of the claim.
   * @return The numeric date claim, if present. Returns empty if the claim is not a numeric date or
   *     can't be parsed as a numeric date.
   * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-2">RFC 7519 section 2</a>
   */
  Optional<Instant> getNumericDate(String name);

  /**
   * Get the object claim with the given name.
   *
   * <p>This returns the claim as a Jackson JsonNode AST.
   *
   * @param name The name of the claim.
   * @return The object claim, if present. Returns empty if the claim is not an object or can't be
   *     parsed as an object.
   */
  Optional<JsonNode> getObject(String name);

  /**
   * Get the string list claim with the given name.
   *
   * @param name The name of the claim.
   * @return The string list claim, if present. Returns empty if the claim is not a JSON array of
   *     strings or cannot be parsed as a JSON array of strings.
   */
  Optional<List<String>> getStringList(String name);

  /**
   * Get the integer list claim with the given name.
   *
   * @param name The name of the claim.
   * @return The integer list claim, if present. Returns empty if the claim is not a JSON array of
   *     integers or cannot be parsed as a JSON array of integers.
   */
  Optional<List<Integer>> getIntegerList(String name);

  /**
   * Get the long list claim with the given name.
   *
   * @param name The name of the claim.
   * @return The long list claim, if present. Returns empty if the claim is not a JSON array of
   *     longs or cannot be parsed as a JSON array of longs.
   */
  Optional<List<Long>> getLongList(String name);

  /**
   * Get the double list claim with the given name.
   *
   * @param name The name of the claim.
   * @return The double list claim, if present. Returns empty if the claim is not a JSON array of
   *     doubles or cannot be parsed as a JSON array of doubles.
   */
  Optional<List<Double>> getDoubleList(String name);

  /**
   * Get the boolean list claim with the given name.
   *
   * @param name The name of the claim.
   * @return The boolean list claim, if present. Returns empty if the claim is not a JSON array of
   *     booleans or cannot be parsed as a JSON array of booleans.
   */
  Optional<List<Boolean>> getBooleanList(String name);

  /**
   * Get the numeric date list claim with the given name.
   *
   * @param name The name of the claim.
   * @return The numeric date list claim, if present. Returns empty if the claim is not a JSON array
   *     of numeric dates or cannot be parsed as a JSON array of numeric dates.
   */
  Optional<List<Instant>> getNumericDateList(String name);

  /**
   * Get the object list claim with the given name.
   *
   * @param name The name of the claim.
   * @return The object list claim, if present. Returns empty if the claim is not a JSON array of
   *     objects or cannot be parsed as a JSON array of objects.
   */
  Optional<List<JsonNode>> getObjectList(String name);
}
