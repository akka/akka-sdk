# isolated-boot-it

An automated integration test that exercises the **real user experience on the
classloader-isolated boot layout** end to end. It is a normal Maven project that inherits
`akka-javasdk-parent`, so it goes through exactly the build path a user's service does:
`exportRuntimeClasspath` computes the runtime/shared/user partition, the embedded boot launcher
starts the runtime behind a shared-surface filter, and `prepareRuntimeLayout` stages the deployable
`user/` image layer.

It lives in `akka-sdk` (not `akka-runtime`) because it spans both: the **parent POM** wiring here and
the **boot launchers** from `akka-runtime`.

## What it proves

`IsolatedBootIT` (embedded mode, via `TestKitSupport`):

1. **The component serves traffic** under classloader isolation.
2. **A clashing third-party library resolves to the user's version.** The akka-runtime image ships
   `commons-io 2.16.1` on its *isolated implementation* classpath; this app pins **`commons-io
   2.11.0`**. User code sees `2.11.0`. Under a flat classpath the two would collide — coexisting is
   the entire point of the work.
3. **Runtime internals are invisible to user code** (`kalix.runtime.AkkaRuntimeMain` →
   `[NOT FOUND]`) while the shared SPI surface (`akka.javasdk.ServiceSetup`) is visible.
4. **A user logback include is applied across the classloader boundary.** The runtime's dev-mode
   logback config ends with `<include resource="include-dev-loggers.xml" optional="true"/>`; this
   app provides that fragment to raise `com.example.user-marker` to DEBUG. It only takes effect if
   the runtime drives logback's Joran engine across the classloader boundary (its stock setup cannot:
   it resolves `class=` via the `LoggerContext`'s own loader and `<include resource=>` via logback's
   own loader, neither of which sees the user fragment). Any custom `class=` named inside the fragment
   resolves the same way.

`ConsumerClasspathIsolationIT` (the consumer side of the testkit's non-transitive `Provided`
runtime dependency):

- the akka-runtime implementation (`kalix.runtime.AkkaRuntimeMain`) and a representative
  runtime-internal dependency (`org.h2.Driver`) are **not reachable** from a classpath that merely
  depends on the testkit;
- the testkit, the thin boot launcher (`akka.runtime.boot.EmbeddedAkkaRuntimeMain`) and the shared
  SPI surface **are** reachable — exactly what a testkit consumer should get.

`TestKitLifecycleStressIT` (the start/stop lifecycle, which `TestKitSupport` only exercises once per
class):

- runs many full `TestKit` start/stop cycles back to back and samples OS resources around a measured
  window. **File descriptors** are held to a tight bound — in practice **zero growth** — so a leaked
  server socket or a runtime that doesn't release its listeners on `stop()` is caught.
- **Threads** are likewise held to a tight, fixed bound. Embedded-isolated boot loads each runtime
  behind its own classloader, so libraries that cache process-global pools in static fields get a
  fresh copy per cycle — notably Reactor, whose `parallel`/`single` schedulers (core-count-sized
  daemon pools) are pulled in by the persistence plugin's r2dbc pool and live in Reactor's static
  cache, not the actor system. `EmbeddedAkkaRuntimeMain` disposes this runtime's Reactor schedulers
  when its `ActorSystem` terminates (alongside closing the runtime classloader), so a full cycle
  leaves no thread behind; the bound is a small fixed slack below one-thread-per-cycle, so any pool
  that creeps back trips it. A live-thread histogram is logged for diagnosis either way.

`LayoutVerificationIT` (the deploy layout `prepareRuntimeLayout` stages — what embedded mode does
not cover):

- the staged `user/` layer contains the app jar and `commons-io-2.11.0.jar`, and **not** the
  runtime's `2.16.1` or any runtime implementation jar;
- the runtime's own classpath carries `commons-io-2.16.1.jar`. Two versions, two layers.

This test deliberately does **not** build a docker image (the parent binds the image build to
`package`; it is skipped here), so it needs no Docker daemon. A docker-build variant would drop that
skip and run on a Docker-capable runner.

## How the clash library was chosen

`commons-io` is a runtime-internal dependency that sits on the **system** (implementation) segment of
the generated classpath and is **not** on the shared surface — so the runtime and the user get
separate copies. It was found by inspecting
`target/akka-runtime/test-classpaths.properties` after a build and picking an entry present in
`akka.runtime.classpath.system` but absent from `akka.runtime.classpath.shared`. If the runtime's
bundled version changes, re-derive it from `target/akka-runtime/runtime-classpaths.properties` and
update the pinned user version (and the constants in both ITs) so the two stay different.

## Running locally

```bash
cd maven-integration-tests/isolated-boot-it
mvn verify
```

The `<parent>` version must be an SDK build that carries the isolated-boot wiring. This file pins a
published isolation snapshot so the test runs as-is. If that snapshot is no longer in your
repositories, point it at the SDK version you are testing — either edit the `<parent><version>` or
run the repo's `SDK_VERSION=<ver> ./updateSdkVersions.sh java maven-integration-tests/isolated-boot-it`
(which is what CI does). The SDK in turn must pin (via `akka-runtime.version`) a runtime release that
publishes `akka-runtime-boot`, `akka-runtime-dev-maven-plugin`, and
`akka-runtime-shared-dependencies`. For a fully local toolchain, `cd ../../../akka-runtime && sbt
publishM2` first.
