# Akka SDK Enforcer

A custom Maven Enforcer rule that detects dependency version conflicts between user applications and the Akka runtime at build time, before deployment to the platform.

## The problem

Akka applications can be deployed to a platform cloud environment or run locally. These two environments resolve dependencies differently, which can lead to subtle classpath conflicts:

**Platform deployment:** The Akka runtime and the user application run in the same JVM with a single classpath. The runtime image provides its own jar dependencies (Akka, gRPC, Guava, Jackson, etc.), and the application is packaged as an init container containing only the application jar, the SDK, and dependencies not already provided by the runtime. At startup the init container copies its jars and the runtime constructs the full classpath — with **runtime jars first**.

**Local development:** Dependencies are resolved through ordinary Maven dependency resolution, where the application's own dependency declarations take precedence ("nearest definition wins").

This means a dependency conflict — for example, the application transitively pulling in `guava:31.1-jre` while the runtime provides `guava:33.5.0-jre` — may go unnoticed locally (the app's version wins) but cause failures in the cloud platform (the runtime's older or newer version wins due to classpath ordering).

## The solution

Alignment is done by a BOM, and this project is the backstop that catches what a BOM cannot:

```
┌──────────────────────────────────────────────────────────────────┐
│  1. akka-runtime build (sbt)                                     │
│                                                                  │
│  runtime-core ──── all runtime dependencies declared here        │
│       │                                                          │
│       ├── runtime-dependencies ── resolves runtime-core's full   │
│       │       │                   dependency tree, renders it    │
│       │       ▼                   as a properties manifest       │
│       │   META-INF/akka-runtime-dependencies.properties          │
│       │     com.google.guava%guava=33.5.0-jre                    │
│       │     com.fasterxml.jackson.core%jackson-databind=2.18.3   │
│       │     io.grpc%grpc-api=1.72.0                              │
│       │     ...                                                  │
│       │                                                          │
│       └── runtime-bom ────────── same tree, rendered as a        │
│               │                  pom-packaged Maven BOM          │
│               ▼                                                  │
│           akka-runtime-bom (<dependencyManagement> only)         │
└──────────────────────────────────────────────────────────────────┘
                    │                         │
   published artifact (tiny jar,     published pom, imported
   just the properties file)         by akka-javasdk-parent
                    │                         │
                    ▼                         │
┌──────────────────────────────────────────────────────────────────┐
│  2. akka-javasdk-enforcer (this project)                         │
│                                                                  │
│  AkkaRuntimeDependencyRule                                       │
│    - loads the manifest from the plugin classpath                │
│    - walks the application's resolved dependency tree            │
│    - compares versions using Maven's ComparableVersion           │
│    - reports conflicts as errors or warnings                     │
│    - configurable strictness and excludes                        │
└──────────────────────────────────────────────────────────────────┘
                    │                         │
                    │ wired into the parent POM so every
                    │ user project gets both automatically
                    ▼                         ▼
┌──────────────────────────────────────────────────────────────────┐
│  3. akka-javasdk-parent (pom.xml)                                │
│                                                                  │
│  <dependencyManagement>                                          │
│    akka-runtime-bom  <scope>import</scope>  ← aligns versions    │
│  </dependencyManagement>                                         │
│                                                                  │
│  <plugin>                                                        │
│    maven-enforcer-plugin                                         │
│    <dependencies>                                                │
│      akka-javasdk-enforcer      ← the rule                       │
│      akka-runtime-dependencies  ← the manifest                   │
│    </dependencies>                                               │
│    <rules>                                                       │
│      <akkaRuntimeDependencyCheck>                                │
│        <failOnConflict>true</failOnConflict>                     │
│        <versionStrictness>patch</versionStrictness>              │
│      </akkaRuntimeDependencyCheck>                               │
│    </rules>                                                      │
│  </plugin>                                                       │
└──────────────────────────────────────────────────────────────────┘
```

Both the manifest and the BOM are auto-generated from `runtime-core`'s actual resolved dependency
tree, by the same code, so there is no manually maintained list to keep in sync and the two cannot
disagree.

The BOM is what actually fixes the conflict: because every service inherits from
`akka-javasdk-parent`, the imported `<dependencyManagement>` becomes part of the service's effective
POM and overrides nearest-wins for every runtime-provided library. The enforcer then only has one
case left to catch — a version the service declares explicitly — which is why the parent runs it at
`patch` strictness rather than the rule's own `newer-only` default.

## What a user can and cannot change

The runtime decides the versions of the libraries it provides. In platform deployment it supplies
them and its jars come first on the classpath, and the service image does not even package the ~50
artifacts listed in the parent POM's exclusion list. Declaring a different version of one of those
changes local compilation and tests, and changes nothing in production — the jar it produces is
discarded at packaging time.

So a runtime-provided version cannot be overridden from the service. What the pieces above do is
make the build say so early: the BOM makes local resolution match production, and the enforcer fails
the build when an explicit declaration has pulled something out of alignment.

The one genuine exit is standalone (self-managed) deployment, where the runtime does not supply the
classpath. Those projects turn the check off with `-Dakka.dependency-check.skip=true` or the
equivalent POM setting below.

## Configuration

The rule is configured in the `maven-enforcer-plugin` section of the POM. The `akka-javasdk-parent` POM provides a default configuration, but users can override settings in their own POM.

### Options

| Parameter | Default | Description |
|---|---|---|
| `skip` | `false` | Completely disable the check. Also supports `-Dakka.dependency-check.skip=true` on the command line or as a POM property. Useful for projects that deploy in standalone mode (self-managed). |
| `failOnConflict` | `true` | `true` to fail the build on conflicts, `false` for warnings only |
| `versionStrictness` | `newer-only` (the parent POM sets `patch`) | How strictly to compare versions (see below): `newer-only`, `major`, `minor`, `patch`, `exact` |
| `excludes` | (empty) | List of `groupId%artifactId` keys to skip |

### Version strictness

| Value | Behavior | Example: allowed | Example: conflict |
|---|---|---|---|
| `newer-only` | Only flag when the app resolves a **newer** version than the runtime provides. Note that this stays silent on a downgrade, including the security-patch downgrades the runtime pins against. | `2.18.1` vs runtime `2.18.3` (app older) | `2.19.0` vs runtime `2.18.3` (app newer) |
| `major` | Flag when major versions differ, regardless of direction. Also flags qualifier differences when the major version matches. | `2.19.0` vs runtime `2.18.3` (same major) | `3.0.0` vs runtime `2.18.3` (different major) |
| `minor` | Flag when major or minor versions differ. Also flags qualifier differences when major.minor matches. | `2.18.1` vs runtime `2.18.3` (same minor) | `2.19.0` vs runtime `2.18.3` (different minor) |
| `patch` | Flag when major, minor, or patch versions differ. Qualifier-only differences are allowed (e.g., `-jre` vs `-android`). This is what `akka-javasdk-parent` configures, since the BOM already aligns everything the runtime provides. | `2.18.3-jre` vs runtime `2.18.3-android` (same base) | `2.18.4` vs runtime `2.18.3` (different patch) |
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
The following dependencies are provided by the Akka runtime in platform
deployment. The runtime versions will take precedence on the classpath,
which may cause unexpected behavior or errors.

  com.google.guava%guava
    Your version:    35.0.0-jre  (newer than runtime)
    Runtime version: 33.5.0-jre

The Akka runtime supplies these libraries, and its jars come first on the
classpath, so a different version here has no effect once deployed.

To fix, either:
  - Remove the explicit version declaration, so that the akka-runtime-bom
    imported by akka-javasdk-parent decides it
  - Align the declared version with the runtime version above
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

## How the manifest and the BOM are generated

The `akka-runtime` build renders both from `runtime-core`'s resolved dependency classpath, through a
single shared helper (`project/RuntimeDependencies.scala`), and publishes them as
`akka-runtime-dependencies` (a jar carrying only the properties file) and `akka-runtime-bom` (a pom,
no jar).

Two details are worth knowing when reading the generated output:

- Artifact ids carry their Scala cross-version suffix, so they match the coordinates a Maven build
  resolves (`io.akka:akka-sdk-spi_2.13`, not `akka-sdk-spi`).
- The BOM manages classified artifacts with an entry of their own, because Maven matches managed
  dependencies on classifier too. Without those, netty's native jars would keep resolving by
  nearest-wins while their companion classes jars followed the BOM.

The `akka-runtime-dev` module — `runtime-core` plus H2 and r2dbc-h2 — is what a service runs against
locally. Those two extra artifacts are dev-mode only, are not part of what the platform provides, and
are deliberately absent from both the manifest and the BOM.
