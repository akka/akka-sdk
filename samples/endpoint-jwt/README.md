# endpoint-jwt


## Designing

To understand the Akka concepts behind this example, see [Development Process](https://doc.akka.io/concepts/development-process.html) in the documentation.

## Developing

This project demonstrates the use of JWT in a HTTP Endpoint.
To understand more, read [JSON Web Tokens (JWT)](https://doc.akka.io/java/auth-with-jwts.html) in the documentation.

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

## Exercising the service

With your Akka service running, any defined endpoints should be available at `http://localhost:9000`.

Run the command below, to test you can access your endpoint if you pass `iss`:`my-issuer` in the token. 
Note the signature of the token is not being passed. Only the header and payloads are included. 
More info in JWTs header, payload, and signature here: https://jwt.io/introduction.

```shell
curl localhost:9000/hello --header "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJteS1pc3N1ZXIifQ"
```

Run the command below, to test you can NOT access your endpoint with any other `iss`, like for example `wrong-issuer`. 
If interested, you can decode the token in https://jwt.io.

```shell
curl localhost:9000/hello -i --header "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ3cm9uZy1pc3N1ZXIifQ"
```

## Deploy to akka.io

You can use the [Akka Console](https://console.akka.io) to create a project and see the status of your service.

Build container image:

```shell
mvn clean install -DskipTests
```

Install the `akka` CLI as documented in [Install Akka CLI](https://doc.akka.io/reference/cli/index.html).

Deploy the service using the image name and tag from above `mvn install`:

```shell
akka service deploy endpoint-jwt endpoint-jwt:tag-name --push
```

Refer to [Deploy and manage services](https://doc.akka.io/operations/services/deploy-service.html)
for more information.

Now if you `expose` the service it (write the output of the route below):

```shell
akka service expose endpoint-jwt
````

You can export this route into the `HELLOJWT_ROUTE` variable to use in the rest of the examples. 

```shell
HELLOJWT_ROUTE=[your-route]
```

and call the service:

```shell
curl https://$HELLOJWT_ROUTE/hello -i --header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJteS1pc3N1ZXIifQ.LuiwJIA7rjL5RP2UzDjs-cfhU2rPjhXMEYrCFDmA5-U"
```

You'll get an HTTP 500 response with a similar UUID.

```shell
HTTP/2 500
...

Unexpected error [b19b63f1-e85e-46a7-8891-75950ffe119c]%
```

You need to set up a JWT key for your service. If not set up, all endpoint methods requiring JWTs will fail with an internal server error.

For this you need two things:
1. Create a secret
2. Link this secret to your service JWTs

Create a secret

```shell
akka secrets create symmetric my-secret \
    --secret-key-literal "so very secret"
```

Link it with your services JWTs
    
```shell
akka services jwts add [your-service-name] \
    --key-id my-key-id \
    --algorithm HS256 \
    --issuer my-issuer \
    --secret my-secret
```

To get a detailed explanation of these two commands go to https://doc.akka.io/security/jwts.html.

One way to find the correct token to is to use https://jwt.io with the following header, payload, and signature:
Header
```
{
  "alg": "HS256",
  "kid": "my-key-id"
}
```

Adding `kid` is recommended since it can be used by Akka to discern the appropriate key in case you have multiple secrets with 
the same `--issuer` and `--algorithm`. Note, that field `kid` as per RFC 7515 (JWS) is not obligatory.

Payload:
```
{
  "iss": "my-issuer"
}
```

Signature: paste there `so very secret`

Once you link your service with the secret, the service will restart with the new configuration. Once your service is back to ready, you can reach it with:

```shell
curl https://$HELLOJWT_ROUTE/hello --header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJteS1pc3N1ZXIifQ.kj113-OEvSI5sAwH7w4JG4zDls_ip3vMMFGg1kOsr1k"
```

Also you can call the other path `/hello/claims` with the token payload:    

```                                                                  
{                                                                    
  "iss": "my-issuer",
  "sub": "my-subject"
}
```

That is:

```shell
curl https://$HELLOJWT_ROUTE/hello/claims --header "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJteS1pc3N1ZXIiLCJzdWIiOiJteS1zdWJqZWN0In0.UcAYj_S6wuQWiQfkqMPsUCQyEBb0nmghgpYtBajtySM"
```
