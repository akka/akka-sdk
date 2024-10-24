# akka-javasdk-archetype

This archetype can be used to generate a project suitable for the development of a Service using [Akka SDK](https://docs.akka.io).

For the latest release see [GitHub releases](https://github.com/lightbend/akka-sdk/releases).

To kickstart a project on Linux and macOS:

```shell
mvn \
  archetype:generate \
  -DarchetypeGroupId=io.akka \
  -DarchetypeArtifactId=akka-javasdk-archetype \
  -DarchetypeVersion=LATEST
```

To kickstart a project on Windows 10 or later:

```shell
mvn ^
  archetype:generate ^
  -DarchetypeGroupId=io.akka ^
  -DarchetypeArtifactId=akka-javasdk-archetype ^
  -DarchetypeVersion=LATEST
```
