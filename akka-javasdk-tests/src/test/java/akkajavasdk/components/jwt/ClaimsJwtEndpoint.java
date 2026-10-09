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
      Long expirationTime,
      Long issuedAt,
      Integer count,
      Long countAsLong,
      Double ratio,
      Boolean admin,
      String countAsString,
      String rolesAsString,
      List<String> roles,
      List<Integer> levels,
      List<Long> dates,
      String address,
      Integer issAsInteger,
      Boolean countAsBoolean) {}

  @Get("/")
  public Claims claims() {
    var claims = requestContext().getJwtClaims();
    return new Claims(
        claims.expirationTime().map(Instant::getEpochSecond).orElse(null),
        claims.issuedAt().map(Instant::getEpochSecond).orElse(null),
        claims.getInteger("count").orElse(null),
        claims.getLong("count").orElse(null),
        claims.getDouble("ratio").orElse(null),
        claims.getBoolean("admin").orElse(null),
        claims.getString("count").orElse(null),
        claims.getString("roles").orElse(null),
        claims.getStringList("roles").orElse(null),
        claims.getIntegerList("levels").orElse(null),
        claims
            .getNumericDateList("dates")
            .map(dates -> dates.stream().map(Instant::getEpochSecond).toList())
            .orElse(null),
        claims.getObject("address").map(Object::toString).orElse(null),
        claims.getInteger("iss").orElse(null),
        claims.getBoolean("count").orElse(null));
  }
}
