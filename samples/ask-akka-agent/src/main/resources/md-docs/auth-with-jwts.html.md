<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Setup and configuration](setup-and-configuration/index.html)
- [JSON Web Tokens (JWT)](auth-with-jwts.html)

<!-- </nav> -->

# JSON Web Tokens (JWT)

This section describes the practical aspects of using JSON Web Tokens (JWTs). If you are not sure what JWTs are, how they work or how to generate them, see [JSON Web Tokens](../reference/jwts.html) first.

Akka’s JWT support is configured by placing annotations in your endpoints at the class level or method level.

## <a href="about:blank#_authentication"></a> Authentication

Akka can validate the signature of JWT tokens provided in an Authorization header to grant access to your endpoints. The generation of tokens is not provided by Akka. In [https://jwt.io/](https://jwt.io/) you can find a simple way to generate tokens to start testing your services.

### <a href="about:blank#_bearer_token_validation"></a> Bearer token validation

If you want to validate the bearer token of a request, you need to annotate your endpoint with a `@JWT` setting with `JWT.JwtMethodMode.BEARER_TOKEN` and you can add an issuer claim. Like this:

[HelloJwtEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/endpoint-jwt/src/main/java/hellojwt/api/HelloJwtEndpoint.java)
```java
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.http.HttpEndpoint;
@HttpEndpoint("/hello")
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, bearerTokenIssuers = "my-issuer") // (1)
public class HelloJwtEndpoint extends AbstractHttpEndpoint {

}
```

| **1** | Validate the Bearer is present in the `Authorization` header and authorize only if the claim `iss` in the payload of this token is `my-issuer`. |
Requests are only allowed if they have a bearer token that can be validated by one of the configured keys for the service, all other requests will be rejected. The bearer token must be supplied with requests using the `Authorization` header:

Authorization: Bearer eyJhbGciOiJIUzI1NiIsImtpZCI6ImtleTEifQ.eyJpc3MiOiJteS1pc3N1ZXIifQ.-MLcf1-kB_1OQIZdy9_wYiFZcMOHsHOE8aJryS1tWq4 You can check in [https://jwt.io/](https://jwt.io/) that this token contains the claim in the payload `iss: my-issuer`.

|  | It is recommended that `bearerTokenIssuers` contains the issuer that you use in your JWT key configuration. See [https://doc.akka.io/security/jwts.html](https://doc.akka.io/security/jwts.html). Otherwise, any services with a trusted key can impersonate the issuer. |

### <a href="about:blank#_configuring_jwt_at_class_level_or_method_level"></a> Configuring JWT at class level or method level

The above examples show how to configure a JWT token on a class or method level. When the annotation is present on both endpoint class and a method, the configuration on the method overrides the class configuration for that method.

### <a href="about:blank#_using_more_claims"></a> Using more claims

Akka can be configured to automatically require and validate other claims than the issuer. Multiple `StaticClaim` can be declared and environment variables are supported on the `values` field. A `StaticClaim` can be defined both at class and method level. The provided claims will be used when validating against the bearer token.

```java
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN,
    bearerTokenIssuers = "my-issuer",
    staticClaims = {
        @JWT.StaticClaim(claim = "role", values = {"admin", "editor"}), // (1)
        @JWT.StaticClaim(claim = "aud", values = "${ENV}.akka.io")}) // (2)
```

| **1** | When declaring multiple values for the same claim, **all** of them will be required when validating the request. |
| **2** | The required value of the `aud` claim includes the value of environment variable `ENV` |
See <a href="../reference/cli/akka-cli/index.html">`akka service deploy -h`</a> for details on how to set environment variables when deploying a service.

|  | For specifying an issuer claim (i.e. "iss"), you should still use the `bearerTokenIssuers` and not static claims. |

#### <a href="about:blank#_configuring_claims_with_a_pattern"></a> Configuring claims with a pattern

Claims can also be defined using a pattern. This is useful when the value of the claim is not completely known in advance, but it can still be validated against a regular expression. See some examples below:

```java
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN,
    bearerTokenIssuers = "my-issuer",
    staticClaims = {
        @JWT.StaticClaim(claim = "role", pattern = "^(admin|editor)$"), // (1)
        @JWT.StaticClaim(claim = "sub", pattern = // (2)
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"),
        @JWT.StaticClaim(claim = "name", pattern = "^\\S+$") // (3)
    })
```

| **1** | Claim "role" must have one of 2 values: `admin` or `editor`. |
| **2** | Claim "sub" must be a valid UUID. |
| **3** | Claim "name" must be not empty. |
If the JWT token claim is an array of values, the token will be considered valid if at least one of the claim values matches the pattern. Otherwise, the request is rejected.

|  | A claim can be defined with a `values` or a `pattern`, but not both. |

#### <a href="about:blank#_multiple_issuers"></a> Multiple issuers

Multiple issuers may be allowed, by setting multiple `bearer_token_issuer` values:

```java
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, bearerTokenIssuers = {"my-issuer", "my-issuer2"}, staticClaims = @JWT.StaticClaim(claim = "sub", values = "my-subject"))
```
The token extracted from the bearer token must have one of the two issuers defined in the annotation.
Akka will place the claims from the validated token in the [RequestContext](_attachments/api/akka/javasdk/http/RequestContext.html), so you can access them from your service via `getJwtClaims()`. The `RequestContext` is accessed by letting the endpoint extend [AbstractHttpEndpoint](_attachments/api/akka/javasdk/http/AbstractHttpEndpoint.html) which provides the method `requestContext()`, so you can retrieve the JWT claims like this:

[HelloJwtEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/endpoint-jwt/src/main/java/hellojwt/api/HelloJwtEndpoint.java)
```java
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.AbstractHttpEndpoint;

public class HelloJwtEndpoint extends AbstractHttpEndpoint {

  @JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, bearerTokenIssuers = {"my-issuer", "my-issuer2"}, staticClaims = @JWT.StaticClaim(claim = "sub", values = "my-subject"))
  @Get("/claims")
  public String helloClaims() {
    var claims = requestContext().getJwtClaims(); // (1)
    var issuer = claims.issuer().get(); // (2)
    var sub = claims.subject().get(); // (2)
    return "issuer: " + issuer + ", subject: " + sub;
  }

}
```

| **1** | Access the claims from the request context. |
| **2** | Note that while calling `Optional#get()` is generally a bad practice, here we know the claims must be present given the `@JWT` configuration. |

## <a href="about:blank#_running_locally_with_jwts_enabled"></a> Running locally with JWTs enabled

When running locally, by default, a dev key with id `dev` is configured for use. This key uses the JWT `none` signing algorithm, which means the signature of the received JWT tokens is not validated. Therefore, when calling an endpoint with a bearer token, only the presence and values of the claims are validated.

## <a href="about:blank#_jwts_when_running_integration_tests"></a> JWTs when running integration tests

When running integration tests, JWTs will still be enforced but its signature will not be validated, similarly to what is described above for when running locally. Thus, when making calls in the context of integration testing, make sure to inject a proper token with the required claims, as shown below:

[HelloJwtIntegrationTest.java](https://github.com/akka/akka-sdk/blob/main/samples/endpoint-jwt/src/test/java/hellojwt/api/HelloJwtIntegrationTest.java)
```java
@Test
public void shouldReturnIssuerAndSubject() throws JsonProcessingException {

  String bearerToken = bearerTokenWith(
          Map.of("iss", "my-issuer", "sub", "my-subject")); // (1)

  StrictResponse<String> call = httpClient.GET("/hello/claims").addHeader("Authorization","Bearer "+ bearerToken) // (2)
          .responseBodyAs(String.class)
          .invoke();

  assertThat(call.body()).isEqualTo("issuer: my-issuer, subject: my-subject");
}

private String bearerTokenWith(Map<String, String> claims) throws JsonProcessingException {
  // setting algorithm to none
  String header = Base64.getEncoder().encodeToString("""
      {
        "alg": "none"
      }
      """.getBytes()); // (3)
  byte[] jsonClaims = JsonSupport.getObjectMapper().writeValueAsBytes(claims);
  String payload = Base64.getEncoder().encodeToString(jsonClaims);

  // no validation is done for integration tests, thus no signature required
  return header + "." + payload; // (4)
}
```

| **1** | Use a helper method to create a JWT token with 2 claims: issuer and subject. |
| **2** | Inject the bearer token as header with the key `Authorization`. |
| **3** | Use static `Base64` encoding of `{ "alg": "none" }`. |
| **4** | Note that you do not need to provide a signature, thus the token has only 2 parts, header and payload. |

<!-- <footer> -->
<!-- <nav> -->
[Access Control Lists (ACLs)](access-control.html) [Run a service locally](running-locally.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->