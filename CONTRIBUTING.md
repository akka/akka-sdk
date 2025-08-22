# Contributing to the Akka SDK

FIXME contribution guidelines like in other LB projects


# Project tips

## Build Token

To build locally, you need to fetch a token at https://account.akka.io/token that you have to place into `~/.sbt/1.0/akka-commercial.sbt` file like this:
```
ThisBuild / resolvers += "lightbend-akka".at("your token resolver here")
```

##  Build scripts

1. The SDK can be built with `sbt publishM2`

2. Samples can be updated to a locally built SDK with `updateSamplesVersions.sh samples/*` 
