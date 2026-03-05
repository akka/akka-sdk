/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.enforcer;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.project.MavenProject;

/**
 * Custom Maven Enforcer rule that detects dependency version conflicts between the application's
 * resolved dependencies and the Akka runtime-provided dependencies.
 *
 * <p>In platform deployment, the Akka runtime's dependencies are placed first on the classpath, so
 * any version mismatch means the runtime's version will be used, potentially breaking the
 * application. This rule catches such conflicts at build time.
 *
 * <p>The runtime dependency manifest is loaded from the {@code akka-runtime-dependencies} artifact,
 * which must be added as a dependency of the maven-enforcer-plugin configuration. The manifest is
 * auto-generated from runtime-core's actual resolved dependency tree.
 *
 * <p>Version comparison is configurable via {@code versionStrictness}:
 *
 * <ul>
 *   <li><b>newer-only</b> (default): only flag when the application resolves a <em>newer</em>
 *       version than the runtime provides. An older application version is safe because the
 *       runtime's newer version is backward compatible.
 *   <li><b>major</b>: flag when major versions differ
 *   <li><b>minor</b>: flag when major or minor versions differ
 *   <li><b>patch</b>: flag any version difference (exact match required)
 * </ul>
 */
@Named("akkaRuntimeDependencyCheck")
public class AkkaRuntimeDependencyRule extends AbstractEnforcerRule {

  private static final String DEFAULT_MANIFEST_RESOURCE =
      "META-INF/akka-runtime-dependencies.properties";

  /**
   * If true, version conflicts cause a build failure. If false, conflicts are reported as warnings
   * only. Default: true.
   */
  private boolean failOnConflict = true;

  /**
   * Controls how strictly versions are compared. Valid values:
   *
   * <ul>
   *   <li><b>newer-only</b> (default): only flag when the application resolves a newer version than
   *       the runtime. Safe because the runtime's newer version is backward compatible with what
   *       the app needs.
   *   <li><b>major</b>: flag when major versions differ
   *   <li><b>minor</b>: flag when major or minor versions differ
   *   <li><b>patch</b>: flag any version difference
   * </ul>
   */
  private String versionStrictness = "newer-only";

  /** Classpath resource path to the runtime dependency manifest. */
  private String manifestResource = DEFAULT_MANIFEST_RESOURCE;

  /**
   * Completely disable this rule. Can be set in the POM or via the command line with {@code
   * -Dakka.dependency-check.skip=true}. Useful for projects that deploy in standalone mode
   * (self-managed). Default: false.
   */
  private boolean skip = false;

  /** Optional list of groupId%artifactId keys to exclude from conflict checking. */
  private List<String> excludes = Collections.emptyList();

  @Inject private MavenProject project;

  @Override
  public void execute() throws EnforcerRuleException {
    if (skip
        || Boolean.parseBoolean(
            project.getProperties().getProperty("akka.dependency-check.skip", "false"))) {
      getLog().info("Akka runtime dependency check is disabled, skipping.");
      return;
    }

    VersionComparator.Strictness strictness = parseStrictness();

    Map<String, String> runtimeDeps = loadManifest();
    if (runtimeDeps.isEmpty()) {
      getLog()
          .warn("Akka runtime dependency manifest is empty or not found at: " + manifestResource);
      return;
    }

    getLog().debug("Loaded " + runtimeDeps.size() + " runtime-provided dependencies from manifest");
    getLog().debug("Version strictness: " + strictness);

    Set<String> excludeSet = new HashSet<>(excludes);
    List<Conflict> conflicts = new ArrayList<>();

    for (Artifact artifact : project.getArtifacts()) {
      String key = artifact.getGroupId() + "%" + artifact.getArtifactId();

      if (excludeSet.contains(key)) {
        continue;
      }

      String runtimeVersion = runtimeDeps.get(key);
      if (runtimeVersion != null) {
        String resolvedVersion = artifact.getVersion();
        VersionComparator.ConflictResult result =
            VersionComparator.check(resolvedVersion, runtimeVersion, strictness);

        if (result.isConflict) {
          conflicts.add(new Conflict(key, resolvedVersion, runtimeVersion, result.direction));
        }
      }
    }

    if (!conflicts.isEmpty()) {
      reportConflicts(conflicts, strictness);
    } else {
      getLog().info("No dependency conflicts with Akka runtime detected.");
    }
  }

  private VersionComparator.Strictness parseStrictness() throws EnforcerRuleException {
    switch (versionStrictness.toLowerCase(Locale.ROOT)) {
      case "newer-only":
      case "newer_only":
      case "neweronly":
        return VersionComparator.Strictness.NEWER_ONLY;
      case "major":
        return VersionComparator.Strictness.MAJOR;
      case "minor":
        return VersionComparator.Strictness.MINOR;
      case "patch":
        return VersionComparator.Strictness.PATCH;
      default:
        throw new EnforcerRuleException(
            "Invalid versionStrictness: '"
                + versionStrictness
                + "'. Valid values: newer-only, major, minor, patch");
    }
  }

  private Map<String, String> loadManifest() throws EnforcerRuleException {
    Map<String, String> deps = new LinkedHashMap<>();

    InputStream is = getClass().getClassLoader().getResourceAsStream(manifestResource);
    if (is == null) {
      is = Thread.currentThread().getContextClassLoader().getResourceAsStream(manifestResource);
    }
    if (is == null) {
      throw new EnforcerRuleException(
          "Cannot find Akka runtime dependency manifest at classpath resource: "
              + manifestResource
              + "\nMake sure akka-runtime-dependencies is added as a <dependency> "
              + "of the maven-enforcer-plugin configuration.");
    }

    try (InputStream stream = is) {
      Properties props = new Properties();
      props.load(stream);
      for (String key : props.stringPropertyNames()) {
        deps.put(key.trim(), props.getProperty(key).trim());
      }
    } catch (IOException e) {
      throw new EnforcerRuleException(
          "Failed to read runtime dependency manifest: " + e.getMessage(), e);
    }

    return deps;
  }

  private void reportConflicts(List<Conflict> conflicts, VersionComparator.Strictness strictness)
      throws EnforcerRuleException {
    StringBuilder sb = new StringBuilder();
    sb.append("\n");
    sb.append("Akka runtime dependency conflicts detected!\n");
    sb.append("===========================================\n");
    sb.append("The following dependencies are provided by the Akka runtime in platform\n");
    sb.append("deployment. The runtime versions will take precedence on the classpath,\n");
    sb.append("which may cause unexpected behavior or errors.\n\n");

    for (Conflict c : conflicts) {
      sb.append("  ").append(c.key.replace('%', ':')).append("\n");
      sb.append("    Your version:    ").append(c.resolvedVersion);
      if (c.direction == VersionComparator.Direction.APP_NEWER) {
        sb.append("  (newer than runtime)");
      } else if (c.direction == VersionComparator.Direction.RUNTIME_NEWER) {
        sb.append("  (older than runtime)");
      }
      sb.append("\n");
      sb.append("    Runtime version: ").append(c.runtimeVersion).append("\n\n");
    }

    sb.append("To fix, either:\n");
    sb.append("  - Align your dependency versions to match the runtime versions above\n");
    sb.append("  - Exclude the conflicting transitive dependency from the library\n");
    sb.append("    that pulls it in\n");
    sb.append("  - Add an <exclude> to the akkaRuntimeDependencyCheck rule\n");
    sb.append("    configuration if the conflict is known to be safe\n");

    if (failOnConflict) {
      throw new EnforcerRuleException(sb.toString());
    } else {
      getLog().warn(sb.toString());
    }
  }

  @Override
  public String toString() {
    return String.format(
        "AkkaRuntimeDependencyRule[failOnConflict=%s, strictness=%s]",
        failOnConflict, versionStrictness);
  }

  private static class Conflict {
    final String key;
    final String resolvedVersion;
    final String runtimeVersion;
    final VersionComparator.Direction direction;

    Conflict(
        String key,
        String resolvedVersion,
        String runtimeVersion,
        VersionComparator.Direction direction) {
      this.key = key;
      this.resolvedVersion = resolvedVersion;
      this.runtimeVersion = runtimeVersion;
      this.direction = direction;
    }
  }
}
