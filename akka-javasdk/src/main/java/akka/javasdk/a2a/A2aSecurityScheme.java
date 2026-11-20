/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * A way for a client to authenticate to an agent, as declared in an {@link A2aAgentCard}. Exactly
 * one member is set. The JSON form is an object with that one member, for example {@code
 * {"openIdConnectSecurityScheme": {"openIdConnectUrl": "https://..."}}}.
 *
 * @param apiKeySecurityScheme an API key in a header, query parameter or cookie
 * @param httpAuthSecurityScheme an HTTP authentication scheme, for example {@code Bearer}
 * @param oauth2SecurityScheme OAuth 2.0
 * @param openIdConnectSecurityScheme OpenID Connect
 * @param mtlsSecurityScheme mutual TLS
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record A2aSecurityScheme(
    ApiKey apiKeySecurityScheme,
    HttpAuth httpAuthSecurityScheme,
    OAuth2 oauth2SecurityScheme,
    OpenIdConnect openIdConnectSecurityScheme,
    MutualTls mtlsSecurityScheme) {

  public A2aSecurityScheme {
    A2aValues.exactlyOne(
        "A2aSecurityScheme",
        apiKeySecurityScheme,
        httpAuthSecurityScheme,
        oauth2SecurityScheme,
        openIdConnectSecurityScheme,
        mtlsSecurityScheme);
  }

  public static A2aSecurityScheme of(ApiKey scheme) {
    return new A2aSecurityScheme(scheme, null, null, null, null);
  }

  public static A2aSecurityScheme of(HttpAuth scheme) {
    return new A2aSecurityScheme(null, scheme, null, null, null);
  }

  public static A2aSecurityScheme of(OAuth2 scheme) {
    return new A2aSecurityScheme(null, null, scheme, null, null);
  }

  public static A2aSecurityScheme of(OpenIdConnect scheme) {
    return new A2aSecurityScheme(null, null, null, scheme, null);
  }

  public static A2aSecurityScheme of(MutualTls scheme) {
    return new A2aSecurityScheme(null, null, null, null, scheme);
  }

  /**
   * An API key.
   *
   * @param description a description of the scheme
   * @param location where the key goes: {@code header}, {@code query} or {@code cookie}
   * @param name the name of the header, query parameter or cookie
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record ApiKey(String description, String location, String name) {}

  /**
   * An HTTP authentication scheme.
   *
   * @param description a description of the scheme
   * @param scheme the scheme name from the HTTP authentication scheme registry, for example {@code
   *     Bearer}
   * @param bearerFormat a hint about the format of the bearer token, for example {@code JWT}
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record HttpAuth(String description, String scheme, String bearerFormat) {}

  /**
   * OAuth 2.0.
   *
   * @param description a description of the scheme
   * @param flows the supported flows
   * @param oauth2MetadataUrl the URL of the authorization server metadata (RFC 8414)
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record OAuth2(String description, OAuthFlows flows, String oauth2MetadataUrl) {}

  /**
   * OpenID Connect.
   *
   * @param description a description of the scheme
   * @param openIdConnectUrl the URL of the OpenID Connect discovery document
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record OpenIdConnect(String description, String openIdConnectUrl) {}

  /**
   * Mutual TLS.
   *
   * @param description a description of the scheme
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record MutualTls(String description) {}

  /**
   * The OAuth 2.0 flows that an agent supports. Exactly one member is set.
   *
   * @param authorizationCode the authorization code flow
   * @param clientCredentials the client credentials flow
   * @param implicit the implicit flow, which the protocol deprecates
   * @param password the resource owner password flow, which the protocol deprecates
   * @param deviceCode the device code flow
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record OAuthFlows(
      AuthorizationCodeFlow authorizationCode,
      ClientCredentialsFlow clientCredentials,
      ImplicitFlow implicit,
      PasswordFlow password,
      DeviceCodeFlow deviceCode) {

    public OAuthFlows {
      A2aValues.exactlyOne(
          "OAuthFlows", authorizationCode, clientCredentials, implicit, password, deviceCode);
    }

    public static OAuthFlows of(AuthorizationCodeFlow flow) {
      return new OAuthFlows(flow, null, null, null, null);
    }

    public static OAuthFlows of(ClientCredentialsFlow flow) {
      return new OAuthFlows(null, flow, null, null, null);
    }

    public static OAuthFlows of(DeviceCodeFlow flow) {
      return new OAuthFlows(null, null, null, null, flow);
    }
  }

  /**
   * The authorization code flow.
   *
   * @param authorizationUrl the authorization endpoint
   * @param tokenUrl the token endpoint
   * @param refreshUrl the endpoint for refreshing tokens
   * @param scopes the available scopes, from scope name to description
   * @param pkceRequired true when the flow requires PKCE
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record AuthorizationCodeFlow(
      String authorizationUrl,
      String tokenUrl,
      String refreshUrl,
      Map<String, String> scopes,
      @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean pkceRequired) {

    public AuthorizationCodeFlow {
      scopes = A2aValues.map(scopes);
    }
  }

  /**
   * The client credentials flow.
   *
   * @param tokenUrl the token endpoint
   * @param refreshUrl the endpoint for refreshing tokens
   * @param scopes the available scopes, from scope name to description
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record ClientCredentialsFlow(
      String tokenUrl, String refreshUrl, Map<String, String> scopes) {

    public ClientCredentialsFlow {
      scopes = A2aValues.map(scopes);
    }
  }

  /**
   * The implicit flow, which the protocol deprecates.
   *
   * @param authorizationUrl the authorization endpoint
   * @param refreshUrl the endpoint for refreshing tokens
   * @param scopes the available scopes, from scope name to description
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record ImplicitFlow(
      String authorizationUrl, String refreshUrl, Map<String, String> scopes) {

    public ImplicitFlow {
      scopes = A2aValues.map(scopes);
    }
  }

  /**
   * The resource owner password flow, which the protocol deprecates.
   *
   * @param tokenUrl the token endpoint
   * @param refreshUrl the endpoint for refreshing tokens
   * @param scopes the available scopes, from scope name to description
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record PasswordFlow(String tokenUrl, String refreshUrl, Map<String, String> scopes) {

    public PasswordFlow {
      scopes = A2aValues.map(scopes);
    }
  }

  /**
   * The device code flow.
   *
   * @param deviceAuthorizationUrl the device authorization endpoint
   * @param tokenUrl the token endpoint
   * @param refreshUrl the endpoint for refreshing tokens
   * @param scopes the available scopes, from scope name to description
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record DeviceCodeFlow(
      String deviceAuthorizationUrl,
      String tokenUrl,
      String refreshUrl,
      Map<String, String> scopes) {

    public DeviceCodeFlow {
      scopes = A2aValues.map(scopes);
    }
  }
}
