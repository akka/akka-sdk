/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.jwt;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.AbstractHttpEndpoint;
import java.time.Instant;
import java.util.List;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/jwt-claims")
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, bearerTokenIssuers = "my-issuer-123")
public class ClaimsJwtEndpoint extends AbstractHttpEndpoint {

  public record Claims(
      String expirationTime,
      String issuedAt,
      String notBefore,
      Integer count,
      Long countAsLong,
      String countAsString,
      Boolean countAsBoolean,
      Double ratio,
      String ratioAsString,
      List<Double> ratioAsList,
      Boolean admin,
      Integer issAsInteger,
      List<String> roles,
      String rolesAsString,
      String rolesAsObject,
      List<Integer> levels,
      List<String> dates,
      String address) {}

  @Get("/")
  public Claims claims() {
    var claims = requestContext().getJwtClaims();
    return new Claims(
        claims.expirationTime().map(Instant::toString).orElse(null),
        claims.issuedAt().map(Instant::toString).orElse(null),
        claims.notBefore().map(Instant::toString).orElse(null),
        claims.getInteger("count").orElse(null),
        claims.getLong("count").orElse(null),
        claims.getString("count").orElse(null),
        claims.getBoolean("count").orElse(null),
        claims.getDouble("ratio").orElse(null),
        claims.getString("ratio").orElse(null),
        claims.getDoubleList("ratio").orElse(null),
        claims.getBoolean("admin").orElse(null),
        claims.getInteger("iss").orElse(null),
        claims.getStringList("roles").orElse(null),
        claims.getString("roles").orElse(null),
        claims.getObject("roles").map(Object::toString).orElse(null),
        claims.getIntegerList("levels").orElse(null),
        claims
            .getNumericDateList("dates")
            .map(dates -> dates.stream().map(Instant::toString).toList())
            .orElse(null),
        claims.getObject("address").map(Object::toString).orElse(null));
  }
}
