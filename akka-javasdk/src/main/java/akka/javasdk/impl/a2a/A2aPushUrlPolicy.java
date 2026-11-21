/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * INTERNAL API
 *
 * <p>Which push notification URLs the endpoint calls. A client chooses the URL, so an unchecked URL
 * lets a client make the service call its own network. Outside dev mode a URL must be {@code
 * https}. A host whose addresses include a private, loopback, link-local or otherwise local address
 * is refused, unless the allowed hosts name it. The check resolves the host, and runs again before
 * each delivery, so that a name that resolves differently later is caught. A delivery connects to
 * the addresses that the check accepted, so that the name cannot resolve to another address between
 * the check and the connection.
 *
 * @param devMode true in dev mode, where {@code http} URLs are accepted
 * @param allowedHosts host names that may resolve to local addresses, in lower case
 */
@InternalApi
public record A2aPushUrlPolicy(boolean devMode, Set<String> allowedHosts) {

  public A2aPushUrlPolicy {
    allowedHosts =
        allowedHosts.stream().map(h -> h.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
  }

  /** The host resolver, replaced in tests. */
  interface Resolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
  }

  /**
   * The result of checking a push notification URL.
   *
   * @param refusal the reason the URL is refused, or null when it is accepted
   * @param addresses the addresses that the host resolved to and that were checked, which a
   *     delivery connects to. Empty for a host that the allowed hosts name, which is not resolved.
   */
  public record Check(String refusal, List<InetAddress> addresses) {
    static Check refused(String refusal) {
      return new Check(refusal, List.of());
    }
  }

  /**
   * Checks a push notification URL.
   *
   * @return the reason the URL is refused, or null when it is accepted
   */
  public String refusal(String url) {
    return check(url).refusal();
  }

  /** Checks a push notification URL, and returns the addresses that were checked. */
  public Check check(String url) {
    return check(url, InetAddress::getAllByName);
  }

  String refusal(String url, Resolver resolver) {
    return check(url, resolver).refusal();
  }

  Check check(String url, Resolver resolver) {
    if (url == null || url.isBlank()) {
      return Check.refused("The push notification configuration has no url");
    }
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      return Check.refused("The push notification url is not a valid URL");
    }
    var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    var host = uri.getHost();
    if (host == null || !(scheme.equals("https") || scheme.equals("http"))) {
      return Check.refused("The push notification url must be an absolute http or https URL");
    }
    if (!devMode && !scheme.equals("https")) {
      return Check.refused("The push notification url must be an https URL");
    }
    var bareHost = host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
    if (allowedHosts.contains(bareHost.toLowerCase(Locale.ROOT))) return new Check(null, List.of());
    InetAddress[] addresses;
    try {
      addresses = resolver.resolve(bareHost);
    } catch (UnknownHostException e) {
      return Check.refused(
          "The host of the push notification url [" + host + "] cannot be resolved");
    }
    for (InetAddress address : addresses) {
      if (isLocal(address)) {
        return Check.refused(
            "The host of the push notification url ["
                + host
                + "] resolves to a private or local address. Name it in"
                + " akka.javasdk.a2a.endpoint.push.allowed-hosts to allow it");
      }
    }
    return new Check(null, List.of(addresses));
  }

  private static boolean isLocal(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }
    var bytes = address.getAddress();
    if (bytes.length == 4) {
      int first = bytes[0] & 0xff;
      int second = bytes[1] & 0xff;
      // 100.64.0.0/10, the shared address space of carrier-grade NAT
      return (first == 100 && second >= 64 && second <= 127) || first == 0;
    }
    // fc00::/7, unique local IPv6 addresses
    return (bytes[0] & 0xfe) == 0xfc;
  }
}
