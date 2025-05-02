

<-nav->

- [  Akka](../index.html)
- [  Developing](index.html)
- [  Author your first service](author-your-first-service.html)



</-nav->



# Author your first Akka service

## [](about:blank#_introduction) Introduction

In this guide, you will:

- Set up your development environment.
- Generate a simple project from a template that follows the recommended[  onion architecture](../concepts/architecture-model.html#_architecture)  .
- Explore a basic HTTP Endpoint that responds with "Hello World!"
- Add path parameters, a request body and a response body to the Endpoint.
- Run your service locally.
- Explore the local console to observe your running service.

## [](about:blank#_prerequisites) Prerequisites

- Java 21, we recommend[  Eclipse Adoptium](https://adoptium.net/marketplace/)
- [  Apache Maven](https://maven.apache.org/install.html)   version 3.9 or later
- <a href="https://curl.se/download.html"> `curl`   command-line tool</a>

## [](about:blank#_generate_and_build_the_project) Generate and build the project

The Maven archetype template prompts you to specify the project’s group ID, name and version interactively. Run it using the commands shown for your operating system.

If you are using IntelliJ, you can skip the command line entirely. Simply open the IDE and follow these steps:

1. Go to**  File > New > Project**  .
2. Select**  Maven Archetype**   from the list of Generators.
3. Fill out the project details:  

  - **    Name**     : Enter the desired project name, such as `helloworld`    .
  - **    Location**     : Specify the directory where you want the project to be created.
  - **    JDK**     : Select Java 21 or a later version.
4. Under**  Catalog**   , ensure "Maven Central" is selected.
5. In the**  Archetype**   section, click the dropdown and select `io.akka:akka-javasdk-archetype`  .
6. Set the**  Version**   to `3.3.0`  .
7. Click**  Create**  .

IntelliJ will handle the project generation and setup for you, allowing you to begin development immediately.

Follow these steps to generate and build your project:

1. From a command line, create a new Maven project from the Akka archetype in a convenient location:  

  Linux or macOS
```command
mvn archetype:generate \
  -DarchetypeGroupId=io.akka \
  -DarchetypeArtifactId=akka-javasdk-archetype \
  -DarchetypeVersion=3.3.0
```

  Windows 10+
```command
mvn archetype:generate ^
  -DarchetypeGroupId=io.akka ^
  -DarchetypeArtifactId=akka-javasdk-archetype ^
  -DarchetypeVersion=3.3.0
```

  Fill in:
  - groupId: `com.example`
  - artifactId: `helloworld`
  - version: `1.0-SNAPSHOT`
  - package: `helloword`
2. Navigate to the new project directory.
3. Open it in your preferred IDE / Editor.

## [](about:blank#_explore_the_http_endpoint) Explore the HTTP Endpoint

An *Endpoint* is a component that creates an externally accessible API. Endpoints are how you expose your services to the outside world. Endpoints can have different protocols and, initially, support HTTP.

HTTP Endpoint components make it possible to conveniently define such APIs accepting and responding in JSON, or dropping down to lower level APIs for ultimate flexibility in what types of data is accepted and returned.

1. Open the `src/main/java/com/example/api/HelloWorldEndpoint.java`   file.

The *Endpoint* is implemented with:

HelloWorldEndpoint.java
```java
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/hello")
public class HelloWorldEndpoint {

  @Get("/")
  public String helloWorld() {
    return "Hello World!";
  }
}
```

This Endpoint is on the path `/hello` and exposes an HTTP GET operation on `/`.

You can also see that there is an *Access Control List* (ACL) on this Endpoint that allows all traffic from the Internet. Without this ACL the service would be unreachable, but you can be very expressive with these ACLs.

## [](about:blank#_run_locally) Run locally

Start your service locally:


```command
mvn compile exec:java
```

Once successfully started, any defined Endpoints become available at `localhost:9000` and you will see an INFO message that the Akka Runtime has started.

Your "Hello World" service is now running.

In another shell, you can now use `curl` to send requests to this Endpoint.


```command
curl localhost:9000/hello
```

Which will reply


```none
Hello World!
```

## [](about:blank#_getting_more_advanced) Getting more advanced

In this section, you will modify the HTTP Endpoint to accept path parameters, a request body, and return a response body.

### [](about:blank#_add_path_parameters) Add path parameters

The path can also contain one or more parameters, which are extracted and passed to the method.

Path parameters can be of type `String`, `int`, `long`, `boolean`, `float`, `double`, `short` or `char` , as well as their `java.lang` class counterparts.

Add path parameters to the Endpoint using the code shown below:

HelloWorldEndpoint.java
```java
@Get("/hello/{name}") // (1)
  public String hello(String name) { // (2)
    return "Hello " + name;
  }

  @Get("/hello/{name}/{age}") // (3)
  public String hello(String name, int age) { // (4)
    return "Hello " + name + "! You are " + age + " years old";
  }
```

| **  1** | Path parameter `name`   in expression. |
| **  2** | Method parameter named as the one in the expression |
| **  3** | When there are multiple parameters |
| **  4** | The method must accept all the same names in the same order as in the path expression. |

Restart the service and curl these commands:


```command
curl localhost:9000/hello/hello/Bob
```


```command
curl localhost:9000/hello/hello/Bob/30
```

### [](about:blank#_add_a_request_body) Add a request body

Modify the Endpoint to accept an HTTP JSON body using the code shown below:

HelloWorldEndpoint.java
```java
public record GreetingRequest(String name, int age) {} // (1)

  @Post("/hello")
  public String hello(GreetingRequest greetingRequest) { // (2)
    return "Hello " + greetingRequest.name + "! " +
        "You are " + greetingRequest.age + " years old";
  }

  @Post("/hello/{number}") // (3)
  public String hello(int number, GreetingRequest greetingRequest) { // (4)
    return number + " Hello " + greetingRequest.name + "! " +
        "You are " + greetingRequest.age + " years old";
  }
```

| **  1** | The record will serialized and deserialized as JSON |
| **  2** | A parameter of the request body type |
| **  3** | When combining request body with path variables |
| **  4** | The body must come last in the parameter list |

Restart the service and curl this command:


```command
curl -i -XPOST -H "Content-Type: application/json" localhost:9000/hello/hello -d '
{"age":"30", "name":"Bob"}'
```

### [](about:blank#_add_a_response_body) Add a response body

Modify the Endpoint to return a response body using the code shown below:

HelloWorldEndpoint.java
```java
public record MyResponse(String name, int age) {}

  @Get("/hello-response/{name}/{age}")
  public MyResponse helloJson(String name, int age) {
    return new MyResponse(name, age); // (1)
  }
```

| **  1** | Returning a record that gets serialized as JSON |

Restart the service and curl this command:


```command
curl localhost:9000/hello/hello-response/Bob/30
```

## [](about:blank#_explore_the_local_console) Explore the local console

The Akka local console is a web-based tool that provides a convenient way to view and interact with your running service.

### [](about:blank#_install_the_akka_cli) Install the Akka CLI

Starting the local console requires using the Akka CLI and Docker.

Install the Akka CLI:

|  | In case there is any trouble with installing the CLI when following these instructions, please check[  Install the Akka CLI](../operations/cli/installation.html)  . |

Linux Download and install the latest version of `akka`:


```bash
curl -sL https://doc.akka.io/install-cli.sh | bash
```

macOS The recommended approach to install `akka` on macOS, is using [brew](https://brew.sh/)


```bash
brew install akka/brew/akka
```

Windows
1. Download the latest version of `akka`   from[  https://downloads.akka.io/latest/akka_windows_amd64.zip](https://downloads.akka.io/latest/akka_windows_amd64.zip)
2. Extract the zip file and move `akka.exe`   to a location on your `%PATH%`  .

Verify that the Akka CLI has been installed successfully by running the following to list all available commands:


```command
akka help
```

### [](about:blank#_start_the_local_console) Start the local console

1. Start the local console. It will launch a Docker container:  


```bash
akka local console

Pulling local console image, please wait...
```
2. Once the console is running, you will see a message like this:  


```bash
- helloworld is running at: localhost:9000
-----------------------------------------------------
(use Ctrl+C to quit)
```
3. You can then access the local console in your browser at:  

[  http://localhost:3000](http://localhost:3000/)
4. Navigate to your service’s Endpoint, which will be available[  here](http://localhost:3000/services/akka-javasdk-archetype/components/io.akka.api.HelloWorldEndpoint)  .

![hello world local console](_images/hello-world-local-console.png)

This is a simple Hello World service, so there isn’t much to see here yet. However, as you build more complex services, the console will become a more valuable tool for monitoring and debugging.

## [](about:blank#_next_steps) Next steps

Now that you have a basic service running, it’s time to learn more about building real services in Akka. See the [Quickstart](shopping-cart/quickstart.html) to build a more realistic application and learn how to deploy it to [akka.io](https://console.akka.io/).



<-footer->


<-nav->
[Developing](index.html) [Components](components/index.html)

</-nav->


</-footer->


<-aside->


</-aside->
