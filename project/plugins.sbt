resolvers += "Akka repository".at("https://repo.akka.io/maven/github_actions")

addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.0.1")
// Note: akka-grpc must be carefully kept in sync with the version used in the runtime.
//       Whenever this is bumped, akka-javasdk-maven/akka-javasdk-parent/pom.xml must be bumped to the same version
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.5.5")
addSbtPlugin("com.github.sbt" % "sbt-java-formatter" % "0.10.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.7.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.12.0")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.12")
addSbtPlugin("net.aichler" % "sbt-jupiter-interface" % "0.11.0")
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.3")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "3.0.2")

// align guava version between sbt-akka-grpc and sbt-java-formatter
libraryDependencies += "com.google.guava" % "guava" % "33.3.1-jre"
