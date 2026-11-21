/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The JSON Canonicalization Scheme of RFC 8785: no insignificant whitespace, object members sorted
 * by their names as UTF-16 code units, strings escaped minimally, and numbers written the way
 * ECMAScript writes a double.
 */
final class Jcs {
  private Jcs() {}

  /** The canonical form of the tree, as UTF-8 bytes. */
  static byte[] canonicalize(JsonNode node) {
    StringBuilder out = new StringBuilder();
    write(node, out);
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  /** The canonical form of the tree, as a string. */
  static String canonicalString(JsonNode node) {
    StringBuilder out = new StringBuilder();
    write(node, out);
    return out.toString();
  }

  private static void write(JsonNode node, StringBuilder out) {
    switch (node.getNodeType()) {
      case NULL, MISSING -> out.append("null");
      case BOOLEAN -> out.append(node.booleanValue());
      case NUMBER -> out.append(number(node.doubleValue()));
      case STRING -> string(node.textValue(), out);
      case ARRAY -> {
        out.append('[');
        boolean first = true;
        for (JsonNode element : node) {
          if (!first) out.append(',');
          first = false;
          write(element, out);
        }
        out.append(']');
      }
      case OBJECT -> {
        List<Map.Entry<String, JsonNode>> members = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) members.add(it.next());
        // String.compareTo compares UTF-16 code units, which is the order RFC 8785 requires.
        members.sort(Map.Entry.comparingByKey());
        out.append('{');
        boolean first = true;
        for (Map.Entry<String, JsonNode> member : members) {
          if (!first) out.append(',');
          first = false;
          string(member.getKey(), out);
          out.append(':');
          write(member.getValue(), out);
        }
        out.append('}');
      }
      default -> throw new IllegalArgumentException("Cannot canonicalize " + node.getNodeType());
    }
  }

  private static void string(String value, StringBuilder out) {
    out.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\t' -> out.append("\\t");
        case '\n' -> out.append("\\n");
        case '\f' -> out.append("\\f");
        case '\r' -> out.append("\\r");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else if (Character.isHighSurrogate(c)) {
            if (i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) {
              out.append(c).append(value.charAt(++i));
            } else {
              throw new IllegalArgumentException("Lone surrogate in string");
            }
          } else if (Character.isLowSurrogate(c)) {
            throw new IllegalArgumentException("Lone surrogate in string");
          } else {
            out.append(c);
          }
        }
      }
    }
    out.append('"');
  }

  /** A double the way ECMAScript's Number-to-string writes it (ECMA-262 7.1.12.1). */
  static String number(double d) {
    if (Double.isNaN(d) || Double.isInfinite(d)) {
      throw new IllegalArgumentException("NaN and Infinity cannot be written in JSON");
    }
    if (d == 0) return "0";
    String sign = d < 0 ? "-" : "";
    double abs = Math.abs(d);

    // The fewest significant digits that read back as the same double; rounding the exact value to
    // that many digits picks the closest candidate.
    BigDecimal exact = new BigDecimal(abs);
    BigDecimal shortest = exact;
    for (int precision = 1; precision <= 17; precision++) {
      BigDecimal candidate = exact.round(new MathContext(precision, RoundingMode.HALF_EVEN));
      if (Double.parseDouble(candidate.toString()) == abs) {
        shortest = candidate;
        break;
      }
    }
    BigDecimal stripped = shortest.stripTrailingZeros();
    String digits = stripped.unscaledValue().toString();
    int k = digits.length();
    // The value is 0.<digits> * 10^n.
    int n = k - stripped.scale();

    StringBuilder out = new StringBuilder(sign);
    if (k <= n && n <= 21) {
      out.append(digits).append("0".repeat(n - k));
    } else if (0 < n && n <= 21) {
      out.append(digits, 0, n).append('.').append(digits, n, k);
    } else if (-6 < n && n <= 0) {
      out.append("0.").append("0".repeat(-n)).append(digits);
    } else {
      int exponent = n - 1;
      out.append(digits.charAt(0));
      if (k > 1) out.append('.').append(digits, 1, k);
      out.append('e').append(exponent < 0 ? '-' : '+').append(Math.abs(exponent));
    }
    return out.toString();
  }
}
