/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * The description of an agent that clients read to discover it, served at {@code
 * /.well-known/agent-card.json}.
 *
 * @param name the name of the agent
 * @param description what the agent does
 * @param supportedInterfaces the URLs and protocol bindings the agent serves, in order of
 *     preference
 * @param provider the organization that runs the agent
 * @param version the version of the agent
 * @param documentationUrl a link to documentation
 * @param capabilities the optional protocol features the agent supports
 * @param securitySchemes the available authentication schemes, by name
 * @param securityRequirements the alternative combinations of schemes that a client must satisfy
 * @param defaultInputModes the media types the agent accepts
 * @param defaultOutputModes the media types the agent produces
 * @param skills the skills of the agent
 * @param signatures signatures of this card as JSON Web Signatures
 * @param iconUrl a link to an icon
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record A2aAgentCard(
    String name,
    String description,
    List<Interface> supportedInterfaces,
    Provider provider,
    String version,
    String documentationUrl,
    Capabilities capabilities,
    Map<String, A2aSecurityScheme> securitySchemes,
    List<A2aSecurityRequirement> securityRequirements,
    List<String> defaultInputModes,
    List<String> defaultOutputModes,
    List<A2aAgentSkill> skills,
    List<Signature> signatures,
    String iconUrl) {

  public A2aAgentCard {
    supportedInterfaces = A2aValues.list(supportedInterfaces);
    securitySchemes = A2aValues.map(securitySchemes);
    securityRequirements = A2aValues.list(securityRequirements);
    defaultInputModes = A2aValues.list(defaultInputModes);
    defaultOutputModes = A2aValues.list(defaultOutputModes);
    skills = A2aValues.list(skills);
    signatures = A2aValues.list(signatures);
  }

  /**
   * A URL where the agent is served, with the protocol binding it speaks there.
   *
   * @param url the URL
   * @param protocolBinding the binding: {@code JSONRPC}, {@code GRPC} or {@code HTTP+JSON}
   * @param tenant the tenant to send with requests
   * @param protocolVersion the A2A version the interface speaks, for example {@code 1.0}
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Interface(
      String url, String protocolBinding, String tenant, String protocolVersion) {}

  /**
   * The organization that runs an agent.
   *
   * @param url the website of the organization
   * @param organization the name of the organization
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Provider(String url, String organization) {}

  /**
   * The optional protocol features that an agent supports. A feature that is not set is not
   * supported.
   *
   * @param streaming true when the agent streams updates
   * @param pushNotifications true when the agent sends push notifications
   * @param extensions the protocol extensions the agent supports
   * @param extendedAgentCard true when the agent serves an extended card to authenticated clients
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Capabilities(
      Boolean streaming,
      Boolean pushNotifications,
      List<Extension> extensions,
      Boolean extendedAgentCard) {

    public Capabilities {
      extensions = A2aValues.list(extensions);
    }
  }

  /**
   * A protocol extension that an agent supports.
   *
   * @param uri the URI that identifies the extension
   * @param description what the extension does for this agent
   * @param required true when a client must support the extension to use the agent; unset means
   *     false
   * @param params settings of the extension
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Extension(
      String uri, String description, Boolean required, Map<String, Object> params) {

    public Extension {
      params = A2aValues.optionalMap(params);
    }
  }

  /**
   * A JSON Web Signature of an agent card.
   *
   * @param protectedHeader the Base64url encoded protected header; serialized as {@code protected}
   * @param signature the Base64url encoded signature
   * @param header the unprotected header
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Signature(
      @JsonProperty("protected") String protectedHeader,
      String signature,
      Map<String, Object> header) {

    public Signature {
      header = A2aValues.optionalMap(header);
    }
  }
}
