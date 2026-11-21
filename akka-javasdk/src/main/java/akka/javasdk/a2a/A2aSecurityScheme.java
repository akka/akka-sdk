/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;

/**
 * A way for a client to authenticate to an agent, as declared in an {@link A2aAgentCard}: {@link
 * ApiKey}, {@link HttpAuth}, {@link OAuth2}, {@link OpenIdConnect} or {@link MutualTls}. The JSON
 * form is an object with one member named after the kind of scheme, for example {@code
 * {"openIdConnectSecurityScheme": {"openIdConnectUrl": "https://..."}}}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes({
  @JsonSubTypes.Type(value = A2aSecurityScheme.ApiKey.class, name = "apiKeySecurityScheme"),
  @JsonSubTypes.Type(value = A2aSecurityScheme.HttpAuth.class, name = "httpAuthSecurityScheme"),
  @JsonSubTypes.Type(value = A2aSecurityScheme.OAuth2.class, name = "oauth2SecurityScheme"),
  @JsonSubTypes.Type(
      value = A2aSecurityScheme.OpenIdConnect.class,
      name = "openIdConnectSecurityScheme"),
  @JsonSubTypes.Type(value = A2aSecurityScheme.MutualTls.class, name = "mtlsSecurityScheme")
})
public sealed interface A2aSecurityScheme
    permits A2aSecurityScheme.ApiKey,
        A2aSecurityScheme.HttpAuth,
        A2aSecurityScheme.OAuth2,
        A2aSecurityScheme.OpenIdConnect,
        A2aSecurityScheme.MutualTls {

  /** A description of the scheme, or null. */
  String description();

  /**
   * An API key.
   *
   * @param description a description of the scheme
   * @param location where the key goes: {@code header}, {@code query} or {@code cookie}
   * @param name the name of the header, query parameter or cookie
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  record ApiKey(String description, String location, String name) implements A2aSecurityScheme {}

  /**
   * An HTTP authentication scheme.
   *
   * @param description a description of the scheme
   * @param scheme the scheme name from the HTTP authentication scheme registry, for example {@code
   *     Bearer}
   * @param bearerFormat a hint about the format of the bearer token, for example {@code JWT}
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  record HttpAuth(String description, String scheme, String bearerFormat)
      implements A2aSecurityScheme {}

  /**
   * OAuth 2.0.
   *
   * @param description a description of the scheme
   * @param flows the supported flows
   * @param oauth2MetadataUrl the URL of the authorization server metadata (RFC 8414)
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  record OAuth2(String description, OAuthFlows flows, String oauth2MetadataUrl)
      implements A2aSecurityScheme {}

  /**
   * OpenID Connect.
   *
   * @param description a description of the scheme
   * @param openIdConnectUrl the URL of the OpenID Connect discovery document
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  record OpenIdConnect(String description, String openIdConnectUrl) implements A2aSecurityScheme {}

  /**
   * Mutual TLS.
   *
   * @param description a description of the scheme
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  record MutualTls(String description) implements A2aSecurityScheme {}

  /**
   * The OAuth 2.0 flow that an agent supports: {@link AuthorizationCodeFlow}, {@link
   * ClientCredentialsFlow}, {@link ImplicitFlow}, {@link PasswordFlow} or {@link DeviceCodeFlow}.
   * The JSON form is an object with one member named after the flow, for example {@code
   * {"clientCredentials": {...}}}.
   */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
  @JsonSubTypes({
    @JsonSubTypes.Type(value = AuthorizationCodeFlow.class, name = "authorizationCode"),
    @JsonSubTypes.Type(value = ClientCredentialsFlow.class, name = "clientCredentials"),
    @JsonSubTypes.Type(value = ImplicitFlow.class, name = "implicit"),
    @JsonSubTypes.Type(value = PasswordFlow.class, name = "password"),
    @JsonSubTypes.Type(value = DeviceCodeFlow.class, name = "deviceCode")
  })
  sealed interface OAuthFlows
      permits AuthorizationCodeFlow,
          ClientCredentialsFlow,
          ImplicitFlow,
          PasswordFlow,
          DeviceCodeFlow {

    /** The available scopes, from scope name to description. */
    Map<String, String> scopes();
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
  record AuthorizationCodeFlow(
      String authorizationUrl,
      String tokenUrl,
      String refreshUrl,
      Map<String, String> scopes,
      @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean pkceRequired)
      implements OAuthFlows {

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
  record ClientCredentialsFlow(String tokenUrl, String refreshUrl, Map<String, String> scopes)
      implements OAuthFlows {

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
  record ImplicitFlow(String authorizationUrl, String refreshUrl, Map<String, String> scopes)
      implements OAuthFlows {

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
  record PasswordFlow(String tokenUrl, String refreshUrl, Map<String, String> scopes)
      implements OAuthFlows {

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
  record DeviceCodeFlow(
      String deviceAuthorizationUrl, String tokenUrl, String refreshUrl, Map<String, String> scopes)
      implements OAuthFlows {

    public DeviceCodeFlow {
      scopes = A2aValues.map(scopes);
    }
  }
}
