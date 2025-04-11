# Samples

<dl><dt><strong>💡 TIP</strong></dt><dd>

**New to Akka? Start here:** 

[java:author-your-first-service.adoc](java:author-your-first-service.adoc) to get a minimal "Hello World!" Akka service and run it locally.
</dd></dl>

Samples are available that demonstrate important patterns and abstractions. These can be downloaded as zip files. Please refer to the `README` file in each zip for setup and usage instructions.

|=======================
| Description | Source download | Level
| [Build a shopping cart](shopping-cart-quickstart.adoc) | [shopping-cart-quickstart.zip](../java/_attachments/shopping-cart-quickstart.zip) |Beginner
| A customer registry with query capabilities | [customer-registry-quickstart.zip](../java/_attachments/customer-registry-quickstart.zip) |Intermediate
| A funds transfer workflow between two wallets | [workflow-quickstart.zip](../java/_attachments/workflow-quickstart.zip) |Intermediate
| A user registration service implemented as a Choreography Saga | [choreography-saga-quickstart.zip](../java/_attachments/choreography-saga-quickstart.zip) |Advanced
|=======================

It is also possible to deploy a pre-built sample project in [the Akka console, window="new"](https://console.akka.io), eliminating the need for local development.

## Maven archetype

To create the build structure of a new service you can use the Maven archetype. From a command window, in the parent directory of the new service, run the following:

* **Linux or macOS**

  ```command window
  mvn archetype:generate \
    -DarchetypeGroupId=io.akka \
    -DarchetypeArtifactId=akka-javasdk-archetype \
    -DarchetypeVersion={akka-javasdk-version}
  ```
* **Windows 10+**

  ```command window
  mvn archetype:generate ^
    -DarchetypeGroupId=io.akka ^
    -DarchetypeArtifactId=akka-javasdk-archetype ^
    -DarchetypeVersion={akka-javasdk-version}
  ```

The [java:author-your-first-service.adoc](java:author-your-first-service.adoc) starts from the Maven archetype and lets you implement a very simple service.
