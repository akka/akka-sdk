/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.enforcer;

import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * Compares dependency versions using Maven's built-in {@link ComparableVersion}, which correctly
 * handles all Maven version semantics (qualifiers, milestones, snapshots, etc.).
 *
 * <p>Comparison strictness levels:
 *
 * <ul>
 *   <li><b>NEWER_ONLY</b> (default): only flag when the application resolves a <em>newer</em>
 *       version than the runtime provides. If the app resolves an older version, the runtime's
 *       newer version is backward compatible.
 *   <li><b>MAJOR</b>: flag when major versions differ, regardless of direction.
 *   <li><b>MINOR</b>: flag when major or minor versions differ.
 *   <li><b>PATCH</b>: flag when major, minor, or patch versions differ. Qualifier-only differences
 *       (e.g., {@code 2.18.3-jre} vs {@code 2.18.3-android}) are allowed.
 *   <li><b>EXACT</b>: flag any version difference whatsoever.
 * </ul>
 */
class VersionComparator {

  enum Strictness {
    /** Only flag when the app version is newer than the runtime version. */
    NEWER_ONLY,
    /** Flag when major versions differ. */
    MAJOR,
    /** Flag when major or minor versions differ. */
    MINOR,
    /** Flag when major, minor, or patch versions differ. Qualifier-only differences are allowed. */
    PATCH,
    /** Flag any version difference whatsoever. */
    EXACT
  }

  enum Direction {
    APP_NEWER,
    RUNTIME_NEWER,
    EQUAL
  }

  static class ConflictResult {
    final boolean isConflict;
    final Direction direction;

    private ConflictResult(boolean isConflict, Direction direction) {
      this.isConflict = isConflict;
      this.direction = direction;
    }

    static ConflictResult none() {
      return new ConflictResult(false, Direction.EQUAL);
    }

    static ConflictResult conflict(Direction direction) {
      return new ConflictResult(true, direction);
    }
  }

  static ConflictResult check(String appVersion, String runtimeVersion, Strictness strictness) {
    if (appVersion.equals(runtimeVersion)) {
      return ConflictResult.none();
    }

    ComparableVersion app = new ComparableVersion(appVersion);
    ComparableVersion runtime = new ComparableVersion(runtimeVersion);

    int cmp = app.compareTo(runtime);
    Direction direction;
    if (cmp > 0) {
      direction = Direction.APP_NEWER;
    } else if (cmp < 0) {
      direction = Direction.RUNTIME_NEWER;
    } else {
      // ComparableVersion considers them equal despite different strings
      // (e.g., "1.0" vs "1.0.0")
      return ConflictResult.none();
    }

    switch (strictness) {
      case NEWER_ONLY:
        if (direction == Direction.APP_NEWER) {
          return ConflictResult.conflict(direction);
        }
        return ConflictResult.none();

      case MAJOR:
        if (majorVersion(appVersion) != majorVersion(runtimeVersion)) {
          return ConflictResult.conflict(direction);
        }
        // Numeric major matches — check if qualifiers differ
        // (e.g., 1.0-M1 vs 1.0, or 1.0-SNAPSHOT vs 1.0)
        if (!qualifiersEqual(appVersion, runtimeVersion)) {
          return ConflictResult.conflict(direction);
        }
        return ConflictResult.none();

      case MINOR:
        if (majorVersion(appVersion) != majorVersion(runtimeVersion)
            || minorVersion(appVersion) != minorVersion(runtimeVersion)) {
          return ConflictResult.conflict(direction);
        }
        if (!qualifiersEqual(appVersion, runtimeVersion)) {
          return ConflictResult.conflict(direction);
        }
        return ConflictResult.none();

      case PATCH:
        if (majorVersion(appVersion) != majorVersion(runtimeVersion)
            || minorVersion(appVersion) != minorVersion(runtimeVersion)
            || patchVersion(appVersion) != patchVersion(runtimeVersion)) {
          return ConflictResult.conflict(direction);
        }
        // Numeric major.minor.patch matches — qualifier-only difference is allowed
        return ConflictResult.none();

      case EXACT:
        return ConflictResult.conflict(direction);

      default:
        return ConflictResult.conflict(direction);
    }
  }

  /** Extract major version number, returns -1 if unparseable. */
  private static int majorVersion(String version) {
    return nthVersionComponent(version, 0);
  }

  /** Extract minor version number, returns 0 if not present, -1 if unparseable. */
  private static int minorVersion(String version) {
    return nthVersionComponent(version, 1);
  }

  /** Extract patch version number, returns 0 if not present, -1 if unparseable. */
  private static int patchVersion(String version) {
    return nthVersionComponent(version, 2);
  }

  /**
   * Extract the nth numeric component from the version's base (before any qualifier). The base is
   * the part before the first '-', split on '.'. E.g., "2.18.3-M1" → base "2.18.3" → ["2", "18",
   * "3"].
   */
  private static int nthVersionComponent(String version, int index) {
    String base = stripQualifier(version);
    String[] parts = base.split("\\.");
    if (parts.length > index) {
      try {
        return Integer.parseInt(parts[index]);
      } catch (NumberFormatException e) {
        return -1;
      }
    }
    return 0;
  }

  /**
   * Returns the numeric base of a version string (everything before the first '-'). E.g.,
   * "2.18.3-M1" → "2.18.3", "1.0-SNAPSHOT" → "1.0", "3.5.0" → "3.5.0".
   */
  private static String stripQualifier(String version) {
    int dashIndex = version.indexOf('-');
    return dashIndex >= 0 ? version.substring(0, dashIndex) : version;
  }

  /**
   * Returns the qualifier portion of a version string (everything after the first '-'), or empty
   * string if there is no qualifier. E.g., "2.18.3-M1" → "M1", "1.0-SNAPSHOT" → "SNAPSHOT", "3.5.0"
   * → "".
   */
  private static String getQualifier(String version) {
    int dashIndex = version.indexOf('-');
    return dashIndex >= 0 ? version.substring(dashIndex + 1) : "";
  }

  /**
   * Check if two versions have equivalent qualifiers. Uses ComparableVersion for the comparison so
   * Maven qualifier semantics are respected (e.g., "1.0" and "1.0.0" are equal, "" and no qualifier
   * are equal). We already know the versions are not ComparableVersion-equal at this point (checked
   * earlier), so if the numeric base matches, the qualifiers must differ.
   */
  private static boolean qualifiersEqual(String appVersion, String runtimeVersion) {
    return getQualifier(appVersion).equalsIgnoreCase(getQualifier(runtimeVersion));
  }
}
