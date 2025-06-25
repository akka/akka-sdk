# Ask Akka Agentic AI Example

This sample illustrates how to build an AI agent that performs a RAG workflow. 

## Running the app

This sample requires OpenAI API Key and a MongoDb Atlas URI. 

### Mongo Atlas
The Mongo DB atlas URI you get from signing up/logging in to https://cloud.mongodb.com
Create an empty database and add a database user with a password. Make sure to allow access from your local IP address
to be able to run the sample locally.

The Mongo DB console should now help out by giving you a URI/connection
string to copy. Note that you need to insert the database user password into the generated URI.

### OpenAI API
To get the OpenAI API key, sign up/log in to find the key at https://platform.openai.com/api-keys

### Start the app

The key and uri needs to be exported as environment variables:
`OPENAI_API_KEY` and `MONGODB_ATLAS_URI` respectively.

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

Before deploying the service we need to modify MongoDB configuration to allow external connections from
the Akka Automated Operations. For experimentation purposes, go to "Network Access" and allow access from anywhere.
For production use cases, you should restrict access to only trusted IP addresses.
Contact support to know which IPs to allow.

1. Build container image:

```shell
mvn clean install -DskipTests
```

2. Install the `akka` CLI as documented in [Install Akka CLI](https://doc.akka.io/reference/cli/index.html).

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
