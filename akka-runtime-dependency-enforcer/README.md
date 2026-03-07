# Akka Runtime Dependency Enforcer

A custom Maven Enforcer rule that detects dependency version conflicts between user applications and the Akka runtime at build time, before deployment to the platform.

## The problem

Akka applications can be deployed to a platform cloud environment or run locally. These two environments resolve dependencies differently, which can lead to subtle classpath conflicts:

**Platform deployment:** The Akka runtime and the user application run in the same JVM with a single classpath. The runtime image provides its own jar dependencies (Akka, gRPC, Guava, Jackson, etc.), and the application is packaged as an init container containing only the application jar, the SDK, and dependencies not already provided by the runtime. At startup the init container copies its jars and the runtime constructs the full classpath — with **runtime jars first**.

**Local development:** Dependencies are resolved through ordinary Maven dependency resolution, where the application's own dependency declarations take precedence ("nearest definition wins").

This means a dependency conflict — for example, the application transitively pulling in `guava:31.1-jre` while the runtime provides `guava:33.5.0-jre` — may go unnoticed locally (the app's version wins) but cause failures in the cloud platform (the runtime's older or newer version wins due to classpath ordering).

## The solution

This project is one piece of a three-part solution:

```
┌──────────────────────────────────────────────────────────────────┐
│  1. akka-runtime build (sbt)                                    │
│                                                                  │
│  runtime-core ──── all runtime dependencies declared here        │
│       │                                                          │
│  runtime-dependencies ──── depends on runtime-core               │
│       │                     resolves its full dependency tree     │
│       │                     generates properties manifest        │
│       ▼                     publishes as akka-runtime-dependencies│
│  META-INF/akka-runtime-dependencies.properties                   │
│    com.google.guava%guava=33.5.0-jre                             │
│    com.fasterxml.jackson.core%jackson-databind=2.18.3            │
│    io.grpc:grpc-api=1.72.0                                      │
│    ...                                                           │
└──────────────────────────────────────────────────────────────────┘
                              │
                              │ published artifact (tiny jar, just
                              │ the properties file, no transitive deps)
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│  2. akka-runtime-dependency-enforcer (this project)                      │
│                                                                  │
│  AkkaRuntimeDependencyRule                                       │
│    - loads the manifest from the plugin classpath                 │
│    - walks the application's resolved dependency tree             │
│    - compares versions using Maven's ComparableVersion            │
│    - reports conflicts as errors or warnings                     │
│    - configurable strictness and excludes                         │
└──────────────────────────────────────────────────────────────────┘
                              │
                              │ wired into the parent POM so every
                              │ user project gets the check automatically
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│  3. akka-javasdk-parent (pom.xml)                                │
│                                                                  │
│  <plugin>                                                        │
│    maven-enforcer-plugin                                         │
│    <dependencies>                                                │
│      akka-runtime-dependency-enforcer        ← the rule                  │
│      akka-runtime-dependencies  ← the manifest              │
│    </dependencies>                                               │
│    <rules>                                                       │
│      <akkaRuntimeDependencyCheck>                                │
│        <failOnConflict>true</failOnConflict>                     │
│      </akkaRuntimeDependencyCheck>                               │
│    </rules>                                                      │
│  </plugin>                                                       │
└──────────────────────────────────────────────────────────────────┘
```

The manifest is auto-generated from `runtime-core`'s actual resolved dependency tree, so there is no manually maintained list to keep in sync.

## Configuration

The rule is configured in the `maven-enforcer-plugin` section of the POM. The `akka-javasdk-parent` POM provides a default configuration, but users can override settings in their own POM.

### Options

| Parameter | Default | Description |
|---|---|---|
| `skip` | `false` | Completely disable the check. Also supports `-Dakka.dependency-check.skip=true` on the command line or as a POM property. Useful for projects that deploy in standalone mode (self-managed). |
| `failOnConflict` | `true` | `true` to fail the build on conflicts, `false` for warnings only |
| `versionStrictness` | `newer-only` | How strictly to compare versions (see below): `newer-only`, `major`, `minor`, `patch`, `exact` |
| `excludes` | (empty) | List of `groupId%artifactId` keys to skip |

### Version strictness

| Value | Behavior | Example: allowed | Example: conflict |
|---|---|---|---|
| `newer-only` | Only flag when the app resolves a **newer** version than the runtime provides. If the app has an older version, the runtime's newer version is backward compatible — no conflict. This is the recommended default. | `2.18.1` vs runtime `2.18.3` (app older) | `2.19.0` vs runtime `2.18.3` (app newer) |
| `major` | Flag when major versions differ, regardless of direction. Also flags qualifier differences when the major version matches. | `2.19.0` vs runtime `2.18.3` (same major) | `3.0.0` vs runtime `2.18.3` (different major) |
| `minor` | Flag when major or minor versions differ. Also flags qualifier differences when major.minor matches. | `2.18.1` vs runtime `2.18.3` (same minor) | `2.19.0` vs runtime `2.18.3` (different minor) |
| `patch` | Flag when major, minor, or patch versions differ. Qualifier-only differences are allowed (e.g., `-jre` vs `-android`). | `2.18.3-jre` vs runtime `2.18.3-android` (same base) | `2.18.4` vs runtime `2.18.3` (different patch) |
| `exact` | Flag **any** version difference whatsoever. | `2.18.3` vs runtime `2.18.3` (identical) | `2.18.3-jre` vs runtime `2.18.3-android` (qualifier differs) |

Version comparison uses Maven's built-in `ComparableVersion`, which correctly handles qualifiers (`-jre`, `-SNAPSHOT`, `-beta1`), milestone ordering, and all standard Maven version semantics. The `major`, `minor`, and `patch` modes also detect qualifier differences (e.g., `-SNAPSHOT` vs release, `-M1` vs `-M10`) when the relevant numeric components match.

### Example: user overrides in their own POM

Switch to warning-only:
```xml
<akkaRuntimeDependencyCheck>
    <failOnConflict>false</failOnConflict>
</akkaRuntimeDependencyCheck>
```

Exclude a known-safe conflict:
```xml
<akkaRuntimeDependencyCheck>
    <excludes>
        <exclude>com.google.guava%guava</exclude>
    </excludes>
</akkaRuntimeDependencyCheck>
```

Disable completely for a project that deploys in standalone mode (self-managed):
```xml
<akkaRuntimeDependencyCheck>
    <skip>true</skip>
</akkaRuntimeDependencyCheck>
```

Or via a POM property (useful for multi-module projects):
```xml
<properties>
    <akka.dependency-check.skip>true</akka.dependency-check.skip>
</properties>
```

Or from the command line for a one-off build:
```
mvn install -Dakka.dependency-check.skip=true
```

## Error output

When a conflict is detected, the build output shows exactly what mismatches and how to fix it:

```
[ERROR] Rule akkaRuntimeDependencyCheck failed:

Akka runtime dependency conflicts detected!
===========================================
The following dependencies are provided by the Akka runtime in managed
platform deployment. The runtime versions will take precedence on the
classpath, which may cause unexpected behavior or errors.

  com.google.guava%guava
    Your version:    35.0.0-jre  (newer than runtime)
    Runtime version: 33.5.0-jre

To fix, either:
  - Align your dependency versions to match the runtime versions above
  - Exclude the conflicting transitive dependency from the library
    that pulls it in
  - Add an <exclude> to the akkaRuntimeDependencyCheck rule
    configuration if the conflict is known to be safe
```

## Note on Guava `-jre` vs `-android` variants

Guava publishes two variants under the same `groupId:artifactId` (`com.google.guava:guava`), with the `-jre` or `-android` suffix as part of the **version string**, not the artifact name:

```
com.google.guava:guava:33.5.0-jre
com.google.guava:guava:33.5.0-android
```

The Akka runtime always provides the `-jre` variant. The enforcer handles this correctly without any special-casing because:

- **Version comparison works as expected.** Maven's `ComparableVersion` treats `-jre` and `-android` as qualifiers compared lexicographically. Since `android` < `jre`, version `33.5.0-android` is considered "older" than `33.5.0-jre`.

- **App has `-android`, runtime has `-jre` (same numeric version).** The enforcer sees the app version as older → no conflict in `newer-only` mode. This is correct: in cloud deployment the runtime's `-jre` wins, which is a superset of `-android` and fully compatible.

- **App has a newer numeric version (any variant).** For example, app resolves `35.0.0-android` while the runtime provides `33.5.0-jre`. The enforcer correctly flags this as a conflict since 35 > 33 regardless of the qualifier suffix.

- **Maven's "nearest definition wins" is not a factor here.** The enforcer runs after Maven has already resolved the dependency tree. It compares the single resolved version against the runtime manifest, so it doesn't matter how Maven arrived at that version.

In short, Guava flavor mismatches are not a problem for the Akka runtime (which is always a JRE environment), and version conflicts are detected correctly regardless of which variant the application pulls in.

## How the manifest is generated

The `akka-runtime` build generates the manifest automatically and includes in artifact `akka-runtime-dependencies`
