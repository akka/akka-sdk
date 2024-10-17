/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http

import akka.javasdk.JsonSupport
import akka.javasdk.JwtClaims
import akka.runtime.sdk.spi.{ JwtClaims => RuntimeJwtClaims }
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.`type`.TypeFactory

import java.lang
import java.time.Instant
import java.util
import java.util.Optional
import java.util.stream.Collectors
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters.RichOption

class JwtClaimsImpl(jwtClaims: RuntimeJwtClaims) extends JwtClaims {

  /**
   * Returns the names of all the claims in this request.
   *
   * @return
   *   The names of all the claims in this request.
   */
  override def allClaimNames(): lang.Iterable[String] =
    jwtClaims.getAllClaimNames.toList.asJava

  /**
   * Returns all the claims as a map of strings to strings.
   *
   * <p>If the claim is a String claim, the value will be the raw String. For all other types, it will be the value of
   * the claim encoded to JSON.
   *
   * @return
   *   All the claims represented as a map of string claim names to string values.
   */
  override def asMap(): util.Map[String, String] =
    jwtClaims.getAllClaimNames
      .flatMap { claimName =>
        jwtClaims.getRawClaim(claimName).map(claimName -> _)
      }
      .toMap
      .asJava

  /**
   * Get the string claim with the given name.
   *
   * <p>Note that if the claim with the given name is not a string claim, this will return the JSON encoding of it.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The string claim, if present.
   */
  override def getString(name: String): Optional[String] =
    jwtClaims.getRawClaim(name).toJava

  /**
   * Does this request have any claims that have been validated?
   *
   * @return
   *   true if there are claims.
   */
  def hasClaims: Boolean = allClaimNames.iterator.hasNext

  /**
   * Get the issuer, that is, the <tt>iss</tt> claim, as described in RFC 7519 section 4.1.1.
   *
   * @return
   *   the issuer, if present.
   * @see
   *   <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.1">RFC 7519 section 4.1.1</a>
   */
  def issuer: Optional[String] = getString("iss")

  /**
   * Get the subject, that is, the <tt>sub</tt> claim, as described in RFC 7519 section 4.1.2.
   *
   * @return
   *   the subject, if present.
   * @see
   *   <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.2">RFC 7519 section 4.1.2</a>
   */
  def subject: Optional[String] = getString("sub")

  /**
   * Get the audience, that is, the <tt>aud</tt> claim, as described in RFC 7519 section 4.1.3.
   *
   * @return
   *   the audience, if present.
   * @see
   *   <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.3">RFC 7519 section 4.1.3</a>
   */
  def audience: Optional[String] = getString("aud")

  /**
   * Get the expiration time, that is, the <tt>exp</tt> claim, as described in RFC 7519 section 4.1.4.
   *
   * @return
   *   the expiration time, if present. Returns empty if the value is not a numeric date.
   * @see
   *   <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.4">RFC 7519 section 4.1.4</a>
   */
  def expirationTime: Optional[Instant] = getNumericDate("exp")

  /**
   * Get the not before, that is, the <tt>nbf</tt> claim, as described in RFC 7519 section 4.1.5.
   *
   * @return
   *   the not before, if present. Returns empty if the value is not a numeric date.
   * @see
   *   <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.5">RFC 7519 section 4.1.5</a>
   */
  def notBefore: Optional[Instant] = getNumericDate("nbf")

  /**
   * Get the issued at, that is, the <tt>iat</tt> claim, as described in RFC 7519 section 4.1.6.
   *
   * @return
   *   the issued at, if present. Returns empty if the value is not a numeric date.
   * @see
   *   <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.6">RFC 7519 section 4.1.6</a>
   */
  def issuedAt: Optional[Instant] = getNumericDate("iat")

  /**
   * Get the JWT ID, that is, the <tt>jti</tt> claim, as described in RFC 7519 section 4.1.7.
   *
   * @return
   *   the JWT ID, if present.
   * @see
   *   <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.7">RFC 7519 section 4.1.7</a>
   */
  def jwtId: Optional[String] = getString("jti")

  /**
   * Get the integer claim with the given name.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The integer claim, if present. Returns empty if the claim is not an integer or can't be parsed as an integer.
   */
  def getInteger(name: String): Optional[lang.Integer] = getString(name).flatMap((value: String) => {
    try Optional.of(value.toInt)
    catch {
      case e: NumberFormatException =>
        Optional.empty
    }

  })

  /**
   * Get the long claim with the given name.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The long claim, if present. Returns empty if the claim is not a long or can't be parsed as an long.
   */
  def getLong(name: String): Optional[lang.Long] = getString(name).flatMap((value: String) => {
    try Optional.of(lang.Long.parseLong(value))
    catch {
      case e: NumberFormatException =>
        Optional.empty
    }

  })

  /**
   * Get the double claim with the given name.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The double claim, if present. Returns empty if the claim is not a double or can't be parsed as an double.
   */
  def getDouble(name: String): Optional[lang.Double] = getString(name).flatMap((value: String) => {
    try Optional.of(value.toDouble)
    catch {
      case e: NumberFormatException =>
        Optional.empty
    }

  })

  /**
   * Get the boolean claim with the given name.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The boolean claim, if present. Returns empty if the claim is not a boolean or can't be parsed as a boolean.
   */
  def getBoolean(name: String): Optional[lang.Boolean] = getString(name).flatMap((value: String) => {
    def foo(value: String) = {
      if (value.equalsIgnoreCase("true")) Optional.of(lang.Boolean.TRUE)
      else if (value.equalsIgnoreCase("false")) Optional.of(lang.Boolean.FALSE)
      Optional.empty
    }

    foo(value)
  })

  /**
   * Get the numeric data claim with the given name.
   *
   * <p>Numeric dates are expressed as a number of seconds since epoch, as described in RFC 7519 section 2.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The numeric date claim, if present. Returns empty if the claim is not a numeric date or can't be parsed as a
   *   numeric date.
   * @see
   *   <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-2">RFC 7519 section 2</a>
   */
  def getNumericDate(name: String): Optional[Instant] = getLong(name).map(Instant.ofEpochSecond(_))

  /**
   * Get the object claim with the given name.
   *
   * <p>This returns the claim as a Jackson JsonNode AST.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The object claim, if present. Returns empty if the claim is not an object or can't be parsed as an object.
   */
  def getObject(name: String): Optional[JsonNode] = getString(name).flatMap((value: String) => {
    try Optional.of(JsonSupport.getObjectMapper.readTree(value))
    catch {
      case e: JsonProcessingException =>
        Optional.empty
    }

  })

  /**
   * Get the string list claim with the given name.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The string list claim, if present. Returns empty if the claim is not a JSON array of strings or cannot be parsed
   *   as a JSON array of strings.
   */
  def getStringList(name: String): Optional[util.List[String]] = getString(name).flatMap((value: String) => {
    try Optional.of(
      JsonSupport.getObjectMapper
        .readValue(value, TypeFactory.defaultInstance.constructCollectionType(classOf[util.List[_]], classOf[String])))
    catch {
      case e: JsonProcessingException =>
        Optional.empty
    }

  })

  /**
   * Get the integer list claim with the given name.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The integer list claim, if present. Returns empty if the claim is not a JSON array of integers or cannot be
   *   parsed as a JSON array of integers.
   */
  def getIntegerList(name: String): Optional[util.List[Integer]] = getString(name).flatMap((value: String) => {
    try Optional.of(
      JsonSupport.getObjectMapper
        .readValue(value, TypeFactory.defaultInstance.constructCollectionType(classOf[util.List[_]], classOf[Integer])))
    catch {
      case e: JsonProcessingException =>
        Optional.empty
    }

  })

  /**
   * Get the long list claim with the given name.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The long list claim, if present. Returns empty if the claim is not a JSON array of longs or cannot be parsed as a
   *   JSON array of longs.
   */
  def getLongList(name: String): Optional[util.List[lang.Long]] = getString(name).flatMap((value: String) => {
    try Optional.of(JsonSupport.getObjectMapper
      .readValue(value, TypeFactory.defaultInstance.constructCollectionType(classOf[util.List[_]], classOf[lang.Long])))
    catch {
      case e: JsonProcessingException =>
        Optional.empty
    }

  })

  /**
   * Get the double list claim with the given name.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The double list claim, if present. Returns empty if the claim is not a JSON array of doubles or cannot be parsed
   *   as a JSON array of doubles.
   */
  def getDoubleList(name: String): Optional[util.List[lang.Double]] = getString(name).flatMap((value: String) => {
    try Optional.of(
      JsonSupport.getObjectMapper.readValue(
        value,
        TypeFactory.defaultInstance.constructCollectionType(classOf[util.List[_]], classOf[lang.Double])))
    catch {
      case e: JsonProcessingException =>
        Optional.empty
    }

  })

  /**
   * Get the boolean list claim with the given name.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The boolean list claim, if present. Returns empty if the claim is not a JSON array of booleans or cannot be
   *   parsed as a JSON array of booleans.
   */
  def getBooleanList(name: String): Optional[util.List[lang.Boolean]] = getString(name).flatMap((value: String) => {
    try Optional.of(
      JsonSupport.getObjectMapper.readValue(
        value,
        TypeFactory.defaultInstance.constructCollectionType(classOf[util.List[_]], classOf[lang.Boolean])))
    catch {
      case e: JsonProcessingException =>
        Optional.empty
    }

  })

  /**
   * Get the numeric date list claim with the given name.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The numeric date list claim, if present. Returns empty if the claim is not a JSON array of numeric dates or
   *   cannot be parsed as a JSON array of numeric dates.
   */
  def getNumericDateList(name: String): Optional[util.List[Instant]] =
    getLongList(name).map((v: util.List[lang.Long]) =>
      v.stream.map(Instant.ofEpochSecond(_)).collect(Collectors.toList[Instant]))

  /**
   * Get the object list claim with the given name.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The object list claim, if present. Returns empty if the claim is not a JSON array of objects or cannot be parsed
   *   as a JSON array of objects.
   */
  def getObjectList(name: String): Optional[util.List[JsonNode]] = getString(name).flatMap((value: String) => {
    try Optional.of(JsonSupport.getObjectMapper
      .readValue(value, TypeFactory.defaultInstance.constructCollectionType(classOf[util.List[_]], classOf[JsonNode])))
    catch {
      case e: JsonProcessingException =>
        Optional.empty
    }

  })
}
