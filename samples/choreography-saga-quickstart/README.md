# Build a User Registration Service Using a Choreography Saga in Akka

This guide demonstrates how to implement a choreography Saga in Akka to ensure unique email addresses across user entities. You'll learn how to manage cross-entity field uniqueness in a distributed system using Akka.

## Prerequisites

- An [Akka account](https://console.akka.io/register)
- Java 21 installed (recommended: [Eclipse Adoptium](https://adoptium.net/marketplace/))
- [Apache Maven](https://maven.apache.org/install.html)
- [Docker Engine](https://docs.docker.com/get-started/get-docker/)
- [`curl` command-line tool](https://curl.se/download.html)

## Concepts

### Designing

To understand the Akka concepts behind this example, see [Development Process](https://doc.akka.io/concepts/development-process.html) in the documentation.

### Developing

In the steps below, you will see how this project demonstrates the use of many different Akka components. For more information, see [Developing Services](https://doc.akka.io/java/index.html).

You may also wish to review the [Saga pattern](https://doc.akka.io/concepts/saga-patterns.html) concept.

## The Set-Based Consistency Validation problem

Before diving into the implementation, it's important to understand a common challenge in event-sourced applications called the _Set-Based Consistency Validation_ problem. This issue arises when we need to ensure that a particular field is unique across all entities in the system. In our user registration service, we need to ensure that email addresses are unique across all users.

While a user may have a unique identifier (e.g., a user ID), they also have an email address that needs to be unique across the entire system. This requirement introduces complexity in maintaining consistency across different entities.

## Step 1: Understand Entity implementation

Examine the following files to understand how Event Sourced Entities and Key Value Entities are implemented:

- `src/main/java/user/domain/UserEntity.java`
- `src/main/java/user/domain/UniqueEmailEntity.java`

## Step 2: Understand Consumer implementation

Review the following files to see how Consumers react to events and state changes in the Entities:

- `src/main/java/user/domain/UserEventsConsumer.java`
- `src/main/java/user/domain/UniqueEmailConsumer.java`

## Step 3: Configure timeout duration

1. Open `src/main/resources/application.conf`.
2. Note the configuration for email confirmation timeout:

   ```
   email.confirmation.timeout = 10s
   ```

## Step 4: Understand Endpoint implementation

Examine the following files to see how the functionality is exposed to the outside world:

- `src/main/java/user/registry/api/EmailEndpoint.java`
- `src/main/java/user/registry/api/UserEndpoint.java`

## Step 5: Run the Application

Start the service locally:

```shell
mvn compile exec:java -Demail.confirmation.timeout=10s
```

## Step 6: Test the Saga

Use the following curl commands to test different scenarios:

### Create a user

```shell
curl localhost:9000/api/users/001 \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{ "name":"John Doe","country":"Belgium", "email":"doe@acme.com" }'
```

### Check email status

```shell
curl localhost:9000/api/emails/doe@acme.com
```

### Test failure scenario

```shell
curl localhost:9000/api/users/003 \
  --header "Content-Type: application/json" \
  -XPOST \
  --data '{ "country":"Belgium", "email":"invalid@acme.com" }'
```

### Change email address

```shell
curl localhost:9000/api/users/001/email \
  --header "Content-Type: application/json" \
  -XPUT \
  --data '{ "newEmail": "john.doe@acme.com" }'
```

## Step 7: Monitor the Saga

1. Watch the console output for logs from `UniqueEmailConsumer.java` and `UserEventsConsumer.java`.
2. Use the Endpoints to check email statuses.

## Step 8: Understand failure handling

Review the code in `UniqueEmailConsumer.java` and `UserEventsConsumer.java` to see how potential failures are handled:

1. A timer is set to release the email if user creation fails.
2. The timer is cancelled if the user is successfully created.

This approach helps maintain consistency even in the face of failures.

## Troubleshooting

If you encounter issues:

- Ensure the Akka service is running on port 9000.
- Verify your curl commands are correctly formatted.
- Check that the data in your curl commands matches the intended input.

## Deploying

You can use the [Akka Console](https://console.akka.io) to create a project and see the status of your service.

Build container image:

```shell
mvn clean install -DskipTests
```

Install the `akka` CLI as documented in [Install Akka CLI](https://doc.akka.io/akka-cli/index.html).

Deploy the service using the image tag from above `mvn install`:

```shell
akka service deploy choreography-saga choreography-saga-quickstart:tag-name --push
```

Refer to [Deploy and manage services](https://doc.akka.io/operations/services/deploy-service.html)
for more information.

## Conclusion

Congratulations, you've learned how to implement a Choreography Saga in Akka for managing cross-entity field uniqueness. This pattern ensures consistency across entities and gracefully handles failure scenarios, effectively addressing the Set-Based Consistency Validation problem.

## Next Steps

1. **Extend the Saga**: Add additional steps, such as sending a confirmation email to the user after successful registration.
2. **Explore Akka's Workflow component**: Workflows offer an alternative, orchestrator-based approach to implementing Sagas.
3. **Join the community**: Visit the [Support page](https://doc.akka.io/support/index.html) to find resources where you can connect with other Akka developers and expand your knowledge.
