# Using Google Cloud Pub/Sub as message broker

To configure access to your Google Cloud Pub/Sub broker for your Akka project, you need to create a Google service account with access to your Google Cloud Pub/Sub broker and provide it to Akka.

Details on doing this can be found in the [Google documentation, window="new"](https://cloud.google.com/iam/docs/creating-managing-service-accounts). We provide simplified steps below.

The service account should allow for the `roles/pubsub.editor` [role, window="new"](https://cloud.google.com/pubsub/docs/access-control#roles).

## Setting up the service account

To set up a service account and generate the key, follow these steps:

1. Navigate to [https://console.cloud.google.com/, window="new"](https://console.cloud.google.com/).
2. From the blue bar, click the dropdown menu next to **Google Cloud Platform**.
3. Click **New Project** to create a project and save the `<gcp-project-id>`, which you will need later.
4. Enter the following `gcloud` command to set up the `gcloud` environment:

   ```command window
   gcloud auth login
   gcloud projects list
   gcloud config set project <gcp-project-id>
   ```
5. Enter the following command to create the service account. The example uses the name `akka-broker`, but you can use any name.

   ```command window
   gcloud iam service-accounts create akka-broker
   ```
6. Enter the following commands to grant the GCP Pub/Sub editor role to the service account. Substitute your project ID for `<gcp-project-id>`.

   ```command window
   gcloud projects add-iam-policy-binding <gcp-project-id> \
       --member "serviceAccount:akka-broker@<gcp-project-id>.iam.gserviceaccount.com" \
       --role "roles/pubsub.editor"
   ```
7. Generate a key file for your service account:

   ```command window
   gcloud iam service-accounts keys create keyfile.json \
       --iam-account akka-broker@<gcp-project-id>.iam.gserviceaccount.com
   ```

Now you have a service account key file with which to configure Akka to use your Google Cloud Pub/Sub broker. You can add the key file using either the Akka Console or the Akka CLI.

* **Browser**

  1. Open the project in the Akka Console.
  2. Select **Integrations** from the left-hand navigation menu.
  3. Click **+** for the Google Cloud Pub/Sub integration option.
  4. Copy the contents of `keyfile.json` into the editor and, click **Apply**.

  The project is now configured to use Google Pub/Sub as the message broker.
* **CLI**

  ```command line
  akka projects config set broker \
    --broker-service google-pubsub \
    --gcp-key-file keyfile.json \
    --description "Google Pub/Sub in <gcp-project-id>"
  ```

  The project is now configured to use Google Pub/Sub as the message broker.

### Create a topic

To create a topic, you can either use the Google Cloud Console, or the Google Cloud CLI. 

* **Browser**

  1. Open the Google Cloud Console.
  2. Go to the Pub/Sub product page.
  3. Click **CREATE TOPIC** on the top of the screen.
  4. Fill in the Topic ID field and choose any other options you need.
  5. Click **CREATE TOPIC** in the modal dialog.

  You can now use the topic to connect with Akka
* **Google Cloud CLI**

  ```command line
  gcloud pubsub topics create TOPIC_ID
  ```

  You can now use the topic to connect with Akka

## Delivery characteristics

When your application consumes messages from Google Pub/Sub, it will try to deliver messages to your service in 'at-least-once' fashion while preserving order.

* the GCP 'Subscription' has the 'Message ordering' flag enabled (this is the case by default for the subscriptions created by Akka)
* the code that acts as a publisher has 'message ordering' enabled (if needed on this client SDK)
* an ordering key is [provided for each message, window="new"](https://cloud.google.com/pubsub/docs/publisher#using-ordering-keys)

When passing messages to a certain entity or using them to update a view row by specifying the id as the Cloud Event `ce-subject` attribute on the message, the same id must be used for the Google Pub/Sub ordering key to guarantee that the messages are processed in order by the entity or view.

**⚠️ WARNING**\
Correct ordering is especially important for topics that stream directly into views using the `transform_update` option: when messages for the same subject id are spread over different ordering keys (or do not have ordering keys), they may read stale data and lose updates.

To achieve at-least-once delivery, messages that are not acknowledged before the [Ack deadline, window="new"](https://cloud.google.com/pubsub/docs/subscriber#subscription-workflow) will be redelivered. This means redeliveries of 'older' messages may arrive behind fresh deliveries of 'newer' messages.

When publishing messages to Google Pub/Sub from Akka, the `ce-subject` attribute, if present, is used as the ordering key for the message.

## Testing Akka eventing

See [Testing Akka eventing](projects/message-brokers.adoc#_testing).

## See also

* [`akka projects config` commands](reference:cli/akka-cli/akka_projects_config.adoc#_see_also)
