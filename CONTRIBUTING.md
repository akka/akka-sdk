# Contributing to Akka Java SDKs

FIXME contribution guidelines like in other LB projects


# Project tips

##  Build scripts

1. See `.sh` scripts in the root directory.

2. Set the maven plugin version to the version sbt generated:

`publishLocally.sh` or

    ```shell
    cd akka-javasdk-maven
    mvn versions:set -DnewVersion="0.7...-SNAPSHOT"
    mvn install
    git checkout .
    ```

3. Pass that version to the sample projects when building:

`updateSamplesVersions.sh` or

    ```shell
    cd samples/java-protobuf-valueentity-shopping-cart
    mvn -Dkalix-sdk.version="0.7...-SNAPSHOT" compile
    ```

Be careful not to accidentally check in the `maven` `pom.xml` files with changed version.
