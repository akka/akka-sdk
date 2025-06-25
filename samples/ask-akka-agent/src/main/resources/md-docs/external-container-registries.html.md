<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Projects](index.html)
- [Configure a container registry](container-registries.html)
- [Configure an external container registry](external-container-registries.html)

<!-- </nav> -->

# Configure an external container registry

To use an external container registry with Akka, you need to give Akka permissions to connect to your registry. To add credentials for your container registry to Akka, you can use the Akka CLI or the Akka Console.

|  | If the container registry you’re using does not require authentication, you don’t have to add any credentials. Akka will automatically pull the container image using the URL you use to deploy your service. |
External container registries are configured by creating an Akka secret, and then configuring your Akka project to use that secret as docker registry credentials. The secret, and project configuration, are both managed by the `akka docker` command.

There are four parameters you need to specify, depending on the registry you want to connect to:

- Server: The first part of the container image URL. For example, if your image is at `us.gcr.io/my-project/my-image`, the server is `https://us.gcr.io` (*mandatory*).
- Username: The username (*optional*).
- Email: The email address (*optional*).
- Password: The password (*mandatory*).
Use the `akka docker add-credentials` command.

```command
akka docker add-credentials --docker-server <my-server> \ // (1)
  --docker-username <my-username> \ // (2)
  --docker-email <my-email> \ // (3)
  --docker-password <my-password> // (4)
```

| **1** | Server |
| **2** | Username |
| **3** | Email |
| **4** | Password |
If you wish to specify the name of the secret that you want to use, that can be done using the `--secret-name` parameter. By default, if not specified, the name of the secret will be `docker-credentials`.

## <a href="about:blank#_updating_credentials"></a> Updating credentials

The `add-credentials` command can also be used to update existing credentials. Simply ensure that the `--secret-name` argument matches the secret name used when the credentials were added, if it was specified then.

## <a href="about:blank#_listing_credentials"></a> Listing credentials

To list all container registry credentials for your Akka project, you can use the Akka CLI or the Akka Console. For security purposes, neither the CLI nor the Console will show the password of the configured registry.

Use the `akka docker list-credentials` command:

```command
akka docker list-credentials
```
The results should look something like:

NAME                STATUS  SERVER             EMAIL             USERNAME
docker-credentials  OK      https://us.gcr.io  user@example.com  _json_key
## <a href="about:blank#_removing_credentials"></a> Removing credentials

To remove container registry credentials from your Akka project, you can use the Akka CLI or the Akka Console.

If you specified a `--secret-name` when creating the credentials, this is the name that you must pass to the command to remove. Otherwise, you should pass the default secret name of `docker-credentials`. The name of the secret appears in the `NAME` column when listing credentials.

```command
akka docker delete-credentials docker-credentials
```
Note that this will only remove the credentials from the configuration for the project, it will not delete the underlying secret. To delete the secret as well, run:

```command
akka secrets delete docker-credentials
```

## <a href="about:blank#_supported_external_registries"></a> Supported external registries

### <a href="about:blank#_private_container_registries"></a> Private container registries

To connect your Akka project to private or self-hosted container registries, the parameters you need are:

- Server: The full URL of your container registry, including the API version (like `https://mycontainerregistry.example.com/v1/`).
- Username: Your username.
- Email: Your email address.
- Password: Your password.

### <a href="about:blank#_docker_hub"></a> Docker Hub

To connect your Akka project to Docker Hub, the parameters you need are:

- Server: `https://index.docker.io/v1/`.
- Username: Your Docker Hub username.
- Email: Your Docker Hub email address.
- Password: Your Docker Hub password or Personal Access Token.
When you use the Akka Console, you don’t need to provide the Server URL.

#### <a href="about:blank#_limits_on_unauthenticated_and_free_usage"></a> Limits on unauthenticated and free usage

Docker has [rate limits](https://docs.docker.com/docker-hub/download-rate-limit/) for unauthenticated and free Docker Hub usage. For unauthenticated users, pull rates are limited based on IP address (anonymous, or unauthenticated, users have a limit of 100 container image pulls per 6 hours per IP address). Akka leverages a limited set of IP addresses to connect to Docker Hub. This means that unauthenticated image pulls might be rate limited. The limit for unauthenticated pulls is shared by all users of Akka.

### <a href="about:blank#_google_container_registry"></a> Google Container Registry

To connect your Akka project to Google Container Registry (GCR), you’ll need:

- An active Google Cloud Platform account.
- The Registry API enabled on your Google Cloud project.
- The ID that corresponds with your GCP project.

  1. Create the service account.

In the following example the service account is named `akka-docker-reader`. Run the create command in your terminal if you have the GCP shell tools installed. Or, run the command from the browser using Cloud Shell Terminal in the Google Cloud Platform (GCP) project.

```command
gcloud iam service-accounts create akka-docker-reader
```
  2. Grant the GCP storage object viewer role to the service account.

In the following example, replace `<gcp-project-id>` with the GCP project ID.

```command
gcloud projects add-iam-policy-binding <gcp-project-id> \
  --member "serviceAccount:akka-docker-reader@<gcp-project-id>.iam.gserviceaccount.com" \
  --role "roles/storage.objectViewer"
```
  3. Generate the service account `_json_key`.

```command
gcloud iam service-accounts keys create keyfile.json \
  --iam-account akka-docker-reader@<gcp-project-id>.iam.gserviceaccount.com
```
  4. Configure your Akka project to use these credentials, by passing the contents of the key file as the password.

```command
akka docker add-credentials --docker-server https://us.gcr.io \
  --docker-username _json_key \
  --docker-email anyemail@example.com \
  --docker-password "$(cat keyfile.json)"
```

|  | Find detailed configuration instructions in the [Google documentation](https://cloud.google.com/container-registry/docs/advanced-authentication#json-key). |

### <a href="about:blank#_azure_container_registry"></a> Azure Container Registry

To connect your Akka project to Azure Container Registry (ACR), the parameters you need are:

- Server: `<registry name>.azurecr.io`.
- Password: The password is based on the " *service principal*." To create a service principal (like `akka-docker-reader`) run the command below.

```command
ACR_REGISTRY_ID=$(az acr show —name akka-registry —query id —output tsv)
```

```command
SP_PASSWD=$(az ad sp create-for-rbac --name http://akka-docker-reader --scopes $ACR_REGISTRY_ID --role acrpull --query password --output tsv)
```
- Username: The username is the application ID of the "service principal." To retrieve the ID, run the command below.

```command
SP_APP_ID=$(az ad sp show —id http://akka-docker-reader —query appId —output tsv)
```
When you use the Akka Console, you only need to fill in the registry name for the Server URL.

## <a href="about:blank#_see_also"></a> See also

- <a href="../../reference/cli/akka-cli/akka_docker.html#_see_also">`akka docker` commands</a>
- <a href="../../reference/cli/akka-cli/akka_container-registry.html#_see_also">`akka container-registry` commands</a>

<!-- <footer> -->
<!-- <nav> -->
[Configure a container registry](container-registries.html) [Configure message brokers](message-brokers.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->