/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.http

import java.lang
import java.math.RoundingMode
import java.time.DateTimeException
import java.time.Instant
import java.util
import java.util.Optional

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters.RichOption

import akka.annotation.InternalApi
import akka.javasdk.JsonSupport
import akka.javasdk.JwtClaims
import akka.runtime.sdk.spi.{ JwtClaims => RuntimeJwtClaims }
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.`type`.TypeFactory

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class JwtClaimsImpl(jwtClaims: RuntimeJwtClaims) extends JwtClaims {

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
   * <p>Note that all values will be encoded to JSON. This means that if the value is a string, it will include the
   * quotes. E.g. "\"my-string-claim\"" for a string claim.
   *
   * @return
   *   All the claims represented as a map of string claim names to string values containing a JSON representation of
   *   its value.
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
   * <p>If the claim is not a string claim, this returns the JSON encoding of it. E.g. "42" for a numeric claim, or
   * "[\"a\",\"b\"]" for an array claim.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The string claim, if present. Returns empty if the claim is JSON null.
   */
  override def getString(name: String): Optional[String] = stringClaim(name).toJava

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
   *   the audience, if present. If the claim is an array, this returns its JSON encoding. Use `getStringList("aud")` to
   *   read an array.
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
  def getInteger(name: String): Optional[lang.Integer] =
    scalarClaim(name).flatMap(_.toIntOption).map(Int.box).toJava

  /**
   * Get the long claim with the given name.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The long claim, if present. Returns empty if the claim is not a long or can't be parsed as an long.
   */
  def getLong(name: String): Optional[lang.Long] =
    scalarClaim(name).flatMap(_.toLongOption).map(Long.box).toJava

  /**
   * Get the double claim with the given name.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The double claim, if present. Returns empty if the claim is not a double or can't be parsed as an double.
   */
  def getDouble(name: String): Optional[lang.Double] =
    scalarClaim(name).flatMap(_.toDoubleOption).map(Double.box).toJava

  /**
   * Get the boolean claim with the given name.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The boolean claim, if present. Returns empty if the claim is not a boolean or can't be parsed as a boolean.
   */
  def getBoolean(name: String): Optional[lang.Boolean] =
    scalarClaim(name).flatMap(_.toBooleanOption).map(Boolean.box).toJava

  /**
   * Get the numeric data claim with the given name.
   *
   * <p>Numeric dates are expressed as a number of seconds since epoch, as described in RFC 7519 section 2. The number
   * can have a fractional part.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The numeric date claim, if present. Returns empty if the claim is not a numeric date or can't be parsed as a
   *   numeric date.
   * @see
   *   <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-2">RFC 7519 section 2</a>
   */
  def getNumericDate(name: String): Optional[Instant] =
    scalarClaim(name).flatMap(parseDecimal).flatMap(numericDate).toJava

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
  def getObject(name: String): Optional[JsonNode] =
    jsonClaim(name).filter(_.isObject).toJava

  /**
   * Get the string list claim with the given name.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The string list claim, if present. Returns empty if the claim is not a JSON array of strings or cannot be parsed
   *   as a JSON array of strings.
   */
  def getStringList(name: String): Optional[util.List[String]] =
    listClaim(name, classOf[String]).toJava

  /**
   * Get the integer list claim with the given name.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The integer list claim, if present. Returns empty if the claim is not a JSON array of integers or cannot be
   *   parsed as a JSON array of integers.
   */
  def getIntegerList(name: String): Optional[util.List[Integer]] =
    listClaim(name, classOf[Integer]).toJava

  /**
   * Get the long list claim with the given name.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The long list claim, if present. Returns empty if the claim is not a JSON array of longs or cannot be parsed as a
   *   JSON array of longs.
   */
  def getLongList(name: String): Optional[util.List[lang.Long]] =
    listClaim(name, classOf[lang.Long]).toJava

  /**
   * Get the double list claim with the given name.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The double list claim, if present. Returns empty if the claim is not a JSON array of doubles or cannot be parsed
   *   as a JSON array of doubles.
   */
  def getDoubleList(name: String): Optional[util.List[lang.Double]] =
    listClaim(name, classOf[lang.Double]).toJava

  /**
   * Get the boolean list claim with the given name.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The boolean list claim, if present. Returns empty if the claim is not a JSON array of booleans or cannot be
   *   parsed as a JSON array of booleans.
   */
  def getBooleanList(name: String): Optional[util.List[lang.Boolean]] =
    listClaim(name, classOf[lang.Boolean]).toJava

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
    listClaim(name, classOf[java.math.BigDecimal]).flatMap { dates =>
      val instants = dates.asScala.flatMap(date => Option(date).flatMap(numericDate))
      if (instants.size == dates.size) Some(instants.asJava) else None
    }.toJava

  /**
   * Get the object list claim with the given name.
   *
   * @param name
   *   The name of the claim.
   * @return
   *   The object list claim, if present. Returns empty if the claim is not a JSON array of objects or cannot be parsed
   *   as a JSON array of objects.
   */
  def getObjectList(name: String): Optional[util.List[JsonNode]] =
    listClaim(name, classOf[JsonNode]).toJava

  private def parseJson(json: String): Option[JsonNode] =
    try Some(JsonSupport.getObjectMapper.readTree(json))
    catch {
      case _: JsonProcessingException => None
    }

  // JSON null claims are treated as absent.
  private def claimNode(name: String): Option[JsonNode] =
    jwtClaims.getRawClaim(name).flatMap(parseJson).filterNot(_.isNull)

  // Non-string claims keep the runtime's JSON text, so that numbers are not reformatted.
  private def stringClaim(name: String): Option[String] =
    jwtClaims.getRawClaim(name).flatMap { raw =>
      parseJson(raw).filterNot(_.isNull).map(node => if (node.isTextual) node.textValue else raw)
    }

  // String claims are accepted too, so that a claim such as "42" still parses as a number.
  private def scalarClaim(name: String): Option[String] =
    claimNode(name).filter(node => node.isTextual || node.isNumber || node.isBoolean).map(_.asText)

  // The text of a string claim is parsed as JSON, so that a claim such as "{\"a\":1}" still parses as an object.
  private def jsonClaim(name: String): Option[JsonNode] =
    claimNode(name).flatMap(node => if (node.isTextual) parseJson(node.textValue) else Some(node))

  private def listClaim[T](name: String, elementClass: Class[T]): Option[util.List[T]] =
    jsonClaim(name).filter(_.isArray).flatMap { node =>
      try Option(
        JsonSupport.getObjectMapper.convertValue[util.List[T]](
          node,
          TypeFactory.defaultInstance.constructCollectionType(classOf[util.List[_]], elementClass)))
      catch {
        case _: IllegalArgumentException => None
      }
    }

  private def parseDecimal(value: String): Option[java.math.BigDecimal] =
    try Some(new java.math.BigDecimal(value))
    catch {
      case _: NumberFormatException => None
    }

  private def numericDate(seconds: java.math.BigDecimal): Option[Instant] =
    try {
      val wholeSeconds = seconds.setScale(0, RoundingMode.FLOOR)
      val nanos = seconds.subtract(wholeSeconds).movePointRight(9).intValue
      Some(Instant.ofEpochSecond(wholeSeconds.longValueExact, nanos))
    } catch {
      case _: ArithmeticException | _: DateTimeException => None
    }
}
