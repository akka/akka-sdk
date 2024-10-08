# Key Value Entity Customer Registry

## Designing

To understand the Akka concepts that are the basis for this example, see [Designing services](https://docs.kalix.io/java/development-process.html) in the documentation.

## Developing

This project demonstrates the use of Key Value Entity and View components.
To understand more about these components, see [Developing services](https://docs.kalix.io/services/)
and in particular the [Java section](https://docs.kalix.io/java/)

## Building

Use Maven to build your project:

```shell
mvn compile
```

## Running Locally

To start your Akka service locally, run:

```shell
mvn compile exec:java
```


## Exercise the service

With your Akka service running, once you have defined endpoints they should be available at `http://localhost:9000`.

* Create customers with:

```shell
curl localhost:9000/customer/001/create \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{"email":"test@example.com","name":"Test Testsson", "street":"Teststreet 25", "city":"City Test"}'
```

```shell
curl localhost:9000/customer/002/create \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{"email":"test@example.com","name":"Test Testsson II", "street":"Teststreet 25", "city":"New City Test"}'
```


```shell
curl localhost:9000/customer/003/create \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{"email":"test@example.com","name":"Test Testsson III","street":"Teststreet 25", "city":"New York City Test"}'
```

* Retrieve the customers:

```shell
curl localhost:9000/customer/001 
```

```shell
curl localhost:9000/customer/002
```

```shell
curl localhost:9000/customer/003
```

* Query by name with a wrapped result:

```shell
curl localhost:9000/customer/by-name/Test%20Testsson
```

* Query by name with a response using a summary:

```shell
curl localhost:9000/customer/by-name-summary \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{"name":"Test Testsson"}'
```

* Query by cities
```shell
curl localhost:9000/customer/by-city \
  --header "Content-Type: application/json" \
  -XPOST  \
  --data '{ "cities": ["City Test", "New City Test"]}'
```

* Delete a customer:

```shell
curl -i -XDELETE localhost:9000/customer/001
```

## Deploying

To deploy your service, install the `akka` CLI as documented in
[Install Akka](https://docs.kalix.io/kalix/install-kalix.html)
and configure a Docker Registry to upload your docker image to.

You will need to update the `dockerImage` property in the `pom.xml` and refer to
[Configuring registries](https://docs.kalix.io/projects/container-registries.html)
for more information on how to make your docker image available to Akka.

Finally, you can use the [Akka Console](https://console.kalix.io)
to create a project and then deploy your service into the project either by using `mvn deploy kalix:deploy` which
will conveniently package, publish your docker image, and deploy your service to Akka, or by first packaging and
publishing the docker image through `mvn deploy` and then deploying the image
through the `akka` CLI.
