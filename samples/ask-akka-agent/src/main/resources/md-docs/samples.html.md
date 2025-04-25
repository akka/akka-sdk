

<-nav->

- [  Akka](../index.html)
- [  Developing](index.html)
- [  Samples](samples.html)



</-nav->



# Samples

|  | **  New to Akka? Start here:**  

[  Author your first Akka service](author-your-first-service.html)   to get a minimal "Hello World!" Akka service and run it locally. |

Samples are available that demonstrate important patterns and abstractions. These can be cloned from their respective repositories. Please refer to the `README` file in each repository for setup and usage instructions.

| Description | Source download | Level |
| --- | --- | --- |
| [  Build a shopping cart](shopping-cart/index.html) | [  Github Repository](https://github.com/akka-samples/shopping-cart-quickstart) | Beginner |
| [  AI agent that performs a RAG workflow](ask-akka/index.html) | [  Github Repository](https://github.com/akka-samples/ask-akka-agent) | Intermediate |
| A customer registry with query capabilities | [  customer-registry-quickstart.zip](../java/_attachments/customer-registry-quickstart.zip) | Intermediate |
| A funds transfer workflow between two wallets | [  Github Repository](https://github.com/akka-samples/transfer-workflow-compensation) | Intermediate |
| A user registration service implemented as a Choreography Saga | [  Github Repository](https://github.com/akka-samples/choreography-saga-quickstart) | Advanced |
| Akka Chess: a complete, resilient, automatically scalable, event-sourced chess game | [  Github Repository](https://github.com/akka-samples/akka-chess) | Advanced |

It is also possible to deploy a pre-built sample project in [the Akka console](https://console.akka.io/) , eliminating the need for local development.
## [](about:blank#_maven_archetype) Maven archetype

To create the build structure of a new service you can use the Maven archetype. From a command window, in the parent directory of the new service, run the following:

Linux or macOS
```command
mvn archetype:generate \
  -DarchetypeGroupId=io.akka \
  -DarchetypeArtifactId=akka-javasdk-archetype \
  -DarchetypeVersion=3.2.0
```

Windows 10+
```command
mvn archetype:generate ^
  -DarchetypeGroupId=io.akka ^
  -DarchetypeArtifactId=akka-javasdk-archetype ^
  -DarchetypeVersion=3.2.0
```

The [Author your first Akka service](author-your-first-service.html) starts from the Maven archetype and lets you implement a very simple service.



<-footer->


<-nav->
[Developer best practices](dev-best-practices.html) [Shopping Cart](shopping-cart/index.html)

</-nav->


</-footer->


<-aside->


</-aside->
