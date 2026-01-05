# Ask Akka Agentic AI Example

This sample illustrates how to build an AI agent that performs a RAG workflow. 

## Running the app

This sample requires a Secure Repository Token, OpenAI API Key and a MongoDb Atlas URI.

### Secure Repository Token

Building requires a secure repository token, which is set up as part of [Akka CLI](https://doc.akka.io/getting-started/quick-install-cli.html)'s `akka code init` command.

If you still need to configure your system with the token there are two additional ways:

1. Use the Akka CLI's `akka code token` command and follow the instructions.
2. Set up the token manually as described [here](https://account.akka.io/token).

### OpenAI API
To get the OpenAI API key, sign up/log in to find the key at https://platform.openai.com/api-keys

### MongoDb Atlas

This sample includes a docker compose file with a pre-configured MongoDB instance. You can start it by running:

```shell
docker-compose up -d
```

Alternatively, you can create an account on MongoDb cloud. See `Deploying` section below for details on configuration. 

### Start the app

The key needs to be exported as environment variables: `OPENAI_API_KEY`. 
If you prefer to use a different LLM model, follow the instructions in `application.conf` to change it.

Then, start the application locally:

```shell
mvn compile exec:java
```

### Indexing documentation

To create the vectorized index, call: 

```shell
curl -XPOST localhost:9000/api/index/start 
```
This call will take an extract of the Akka SDK documentation and create a vectorized index in MongoDB.
The documentation files are located in `src/main/resources/md-docs/`. That said, you can also add your own documentation files to this directory.

### Query the AI

Use the Web UI to make calls.
http://localhost:9000/

Alternatively, call the API directly using curl.

```shell
curl localhost:9000/api/ask --header "Content-Type: application/json" -XPOST \
--data '{ "userId": "001", "sessionId": "foo", "question":"How many components exist in the Akka SDK?"}'
```

This will run a query and save the conversational history in a `SessionEntity` identified by 'foo'.
Results are streamed using SSE.


## Deploying

You can use the [Akka Console](https://console.akka.io) to create a project and see the status of your service.

### Mongo Atlas
The Mongo DB atlas URI you get from signing up/logging in to https://cloud.mongodb.com
Create an empty database and add a database user with a password.

The Mongo DB console should now help out by giving you a URI/connection
string to copy. Note that you need to insert the database user password into the generated URI.
You can export that as environment variable:
```shell
export MONGODB_ATLAS_URI="your generated URI goes here"
```

Before deploying the service we need to modify MongoDB configuration to allow external connections from
the Akka Automated Operations. For experimentation purposes, go to "Network Access" and allow access from anywhere.
For production use cases, you should restrict access to only trusted IP addresses.
Contact support to know which IPs to allow.

### Deploy service

1. Build container image:

```shell
mvn clean install -DskipTests
```

2. Install the `akka` CLI as documented in [Install Akka CLI](https://doc.akka.io/operations/cli/installation.html).

3. Let's setup up a secret containing both the OpenAI API key and the MongoDB Atlas Uri.

```shell
akka secret create generic ask-akka-secrets \
  --literal mongodb-uri=$MONGODB_ATLAS_URI \
  --literal openai-key=$OPENAI_API_KEY
```

Note: this assumes you have your `$OPENAI_API_KEY` and `$MONGODB_ATLAS_URI` exported as required to run the project, otherwise just pass the values directly.

4. Deploy the service using the image tag from above `mvn install`:

```shell
akka service deploy ask-akka-agent ask-akka:<tag-name> \
  --secret-env OPENAI_API_KEY=ask-akka-secrets/openai-key \
  --secret-env MONGODB_ATLAS_URI=ask-akka-secrets/mongodb-uri \
  --push
```

Note: the value of both ENV vars is set to `secret-name/key-name`, as defined in the previous command.


Refer to [Deploy and manage services](https://doc.akka.io/operations/services/deploy-service.html)
for more information.
