/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class A2aPushUrlPolicyTest {

  // Names resolve to fixed addresses, so the test needs no DNS
  private static final Map<String, String> HOSTS =
      Map.of(
          "public.example.com", "93.184.216.34",
          "internal.example.com", "10.1.2.3",
          "metadata.example.com", "169.254.169.254",
          "rebound.example.com", "127.0.0.1",
          "cgnat.example.com", "100.64.1.1");

  private static final A2aPushUrlPolicy.Resolver resolver =
      host -> {
        var address = HOSTS.getOrDefault(host, host);
        if (!Character.isDigit(address.charAt(0)) && !address.contains(":")) {
          throw new UnknownHostException(host);
        }
        return new InetAddress[] {InetAddress.getByName(address)};
      };

  private static String refusal(A2aPushUrlPolicy policy, String url) {
    return policy.refusal(url, resolver);
  }

  private final A2aPushUrlPolicy production = new A2aPushUrlPolicy(false, Set.of());
  private final A2aPushUrlPolicy devMode = new A2aPushUrlPolicy(true, Set.of());

  @Test
  void acceptsAnHttpsUrlOnAPublicAddress() {
    assertNull(refusal(production, "https://public.example.com/hook"));
  }

  @Test
  void refusesHttpOutsideDevMode() {
    assertTrue(refusal(production, "http://public.example.com/hook").contains("https"));
    assertNull(refusal(devMode, "http://public.example.com/hook"));
  }

  @Test
  void refusesPrivateLoopbackLinkLocalAndSharedAddressesAfterResolving() {
    for (String host :
        new String[] {
          "internal.example.com",
          "metadata.example.com",
          "rebound.example.com",
          "cgnat.example.com",
          "127.0.0.1",
          "[::1]",
          "[fd00::1]",
          "0.0.0.0"
        }) {
      assertNotNull(refusal(production, "https://" + host + "/hook"), host);
      assertNotNull(refusal(devMode, "http://" + host + "/hook"), host);
    }
  }

  @Test
  void refusesBenchmarkingAddresses() {
    for (String host : new String[] {"198.18.0.1", "198.19.255.254"}) {
      assertNotNull(refusal(production, "https://" + host + "/hook"), host);
    }
    assertNull(refusal(production, "https://198.20.0.1/hook"));
    assertNull(refusal(production, "https://198.17.255.255/hook"));
  }

  @Test
  void refusesNat64AddressesOfPrivateIpv4Addresses() {
    for (String host :
        new String[] {
          "[64:ff9b::a01:203]", // 10.1.2.3
          "[64:ff9b::7f00:1]", // 127.0.0.1
          "[64:ff9b::a9fe:a9fe]", // 169.254.169.254
          "[64:ff9b::c0a8:101]", // 192.168.1.1
          "[64:ff9b::c612:1]" // 198.18.0.1
        }) {
      assertNotNull(refusal(production, "https://" + host + "/hook"), host);
    }
    // 93.184.216.34, a public address
    assertNull(refusal(production, "https://[64:ff9b::5db8:d822]/hook"));
  }

  @Test
  void acceptsALocalHostThatTheAllowListNames() {
    var policy = new A2aPushUrlPolicy(true, Set.of("Rebound.example.com", "127.0.0.1"));
    assertNull(refusal(policy, "http://rebound.example.com:8080/hook"));
    assertNull(refusal(policy, "http://127.0.0.1:9000/hook"));
    assertNotNull(refusal(policy, "http://internal.example.com/hook"));
  }

  @Test
  void refusesUrlsThatAreNotAbsoluteHttpUrls() {
    assertNotNull(refusal(devMode, "ftp://public.example.com/hook"));
    assertNotNull(refusal(devMode, "/relative/hook"));
    assertNotNull(refusal(devMode, ""));
    assertNotNull(refusal(devMode, "https://unknown.invalid/hook"));
  }

  @Test
  void returnsTheAddressesItChecked() throws Exception {
    var check = production.check("https://public.example.com/hook", resolver);
    assertNull(check.refusal());
    assertEquals(List.of(InetAddress.getByName("93.184.216.34")), check.addresses());
    var allowed = new A2aPushUrlPolicy(true, Set.of("rebound.example.com"));
    assertEquals(List.of(), allowed.check("http://rebound.example.com/hook", resolver).addresses());
    assertEquals(
        List.of(), production.check("https://internal.example.com/hook", resolver).addresses());
  }
}
