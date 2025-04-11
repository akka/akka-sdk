# Author your first Akka service

## Introduction 

In this guide, you will:

* Set up your development environment.
* Generate a simple project from a template that follows the recommended [onion architecture](concepts:architecture-model.adoc#_architecture).
* Explore a basic HTTP Endpoint that responds with "Hello World!"
* Add path parameters, a request body and a response body to the Endpoint.
* Run your service locally.
* Explore the local console to observe your running service.

## Prerequisites

## Generate and build the project

The Maven archetype template prompts you to specify the project’s group ID, name and version interactively. Run it using the commands shown for your operating system.

If you are using IntelliJ, you can skip the command line entirely. Simply open the IDE and follow these steps:

1. Go to **File > New > Project**.
2. Select **Maven Archetype** from the list of Generators.
3. Fill out the project details:
   * **Name**: Enter the desired project name, such as `helloworld`.
   * **Location**: Specify the directory where you want the project to be created.
   * **JDK**: Select Java {java-version} or a later version.
4. Under **Catalog**, ensure "Maven Central" is selected.
5. In the **Archetype** section, click the dropdown and select `io.akka:akka-javasdk-archetype`.
6. Set the **Version** to `{akka-javasdk-version}`.
7. Click **Create**.

IntelliJ will handle the project generation and setup for you, allowing you to begin development immediately.

Follow these steps to generate and build your project:

1. From a command line, create a new Maven project from the Akka archetype in a convenient location:

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

+
.Fill in:
* groupId: `com.example`
* artifactId: `helloworld`
* version: `1.0-SNAPSHOT`
* package: `helloword`

1. Navigate to the new project directory.
2. Open it in your preferred IDE / Editor.

## Explore the HTTP Endpoint

An _Endpoint_ is a component that creates an externally accessible API. Endpoints are how you expose your services to the outside world. Endpoints can have different protocols and, initially, support HTTP.

HTTP Endpoint components make it possible to conveniently define such APIs accepting and responding in JSON, or dropping down to lower level APIs for ultimate flexibility in what types of data is accepted and returned.

1. Open the `src/main/java/com/example/api/HelloWorldEndpoint.java` file.

The _Endpoint_ is implemented with:

**HelloWorldEndpoint.java**

```java
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/hello")
public class HelloWorldEndpoint {

  @Get("/")
  public CompletionStage<String> helloWorld() {
    return completedStage("Hello World!");
  }
}
```

This Endpoint is on the path `/hello` and exposes an HTTP GET operation on `/`.

You can also see that there is an _Access Control List_ (ACL) on this Endpoint that allows all traffic from the Internet. Without this ACL the service would be unreachable, but you can be very expressive with these ACLs.

## Run locally

Start your service locally:

```command line
mvn compile exec:java
```

Once successfully started, any defined Endpoints become available at `localhost:9000` and you will see an INFO message that the Akka Runtime has started.

Your "Hello World" service is now running.

In another shell, you can now use `curl` to send requests to this Endpoint.

```command line
curl localhost:9000/hello
```

Which will reply
```
Hello World!
```

## Getting more advanced

In this section, you will modify the HTTP Endpoint to accept path parameters, a request body, and return a response body.

### Add path parameters

The path can also contain one or more parameters, which are extracted and passed to the method.

Path parameters can be of type `String`, `int`, `long`, `boolean`,  `float`, `double`, `short` or `char`, as well as their `java.lang` class counterparts.

Add path parameters to the Endpoint using the code shown below:

**HelloWorldEndpoint.java**

```java
```
1. Path parameter `name` in expression.
2. Method parameter named as the one in the expression
3. When there are multiple parameters
4. The method must accept all the same names in the same order as in the path expression.

Restart the service and curl these commands:

```command line
curl localhost:9000/hello/hello/Bob
```

```command line
curl localhost:9000/hello/hello/Bob/30
```

### Add a request body

Modify the Endpoint to accept an HTTP JSON body using the code shown below:

**HelloWorldEndpoint.java**

```java
```
1. The record will serialized and deserialized as JSON
2. A parameter of the request body type
3. When combining request body with path variables
4. The body must come last in the parameter list

Restart the service and curl this command:

```command line
curl -i -XPOST -H "Content-Type: application/json" localhost:9000/hello/hello -d '
{"age":"30", "name":"Bob"}'
```

### Add a response body

Modify the Endpoint to return a response body using the code shown below:

**HelloWorldEndpoint.java**

```java
```
1. Returning a record that gets serialized as JSON

Restart the service and curl this command:

```command line
curl localhost:9000/hello/hello-response/Bob/30
```

## Explore the local console

The Akka local console is a web-based tool that provides a convenient way to view and interact with your running service.

### Install the Akka CLI

Starting the local console requires using the Akka CLI and Docker.

### Start the local console

1. Start the local console. It will launch a Docker container:

   ```bash
   akka local console

   Pulling local console image, please wait...
   ```
2. Once the console is running, you will see a message like this:

+
```bash
- helloworld is running at: localhost:9000
-----------------------------------------------------
(use Ctrl+C to quit)
```

1. You can then access the local console in your browser at:

   http://localhost:3000
2. Navigate to your service’s Endpoint, which will be available [here, window="new"](http://localhost:3000/services/akka-javasdk-archetype/components/io.akka.api.HelloWorldEndpoint).

![hello-world-local-console](hello-world-local-console.png)

This is a simple Hello World service, so there isn’t much to see here yet. However, as you build more complex services, the console will become a more valuable tool for monitoring and debugging.

## Next steps

Now that you have a basic service running, it’s time to learn more about building real services in Akka. See the [java:shopping-cart-quickstart.adoc](java:shopping-cart-quickstart.adoc) to build a more realistic application and learn how to deploy it to [akka.io](https://console.akka.io).
