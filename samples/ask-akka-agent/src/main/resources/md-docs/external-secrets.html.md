<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Projects](index.html)
- [Manage external secrets](external-secrets.html)

<!-- </nav> -->

# Manage external secrets

Akka allows integrating with various external secret managers. External secrets are provided to your services through filesystem mounts, however they will never be written to disk on your service.

Authentication with external secret managers is done using workload identity. When a service starts, the identity of that service is used to authentication with and be authorized by the secret manager, using OIDC.

## <a href="about:blank#_managing_external_secrets_in_a_project"></a> Managing external secrets in a project

### <a href="about:blank#_listing_external_secrets"></a> Listing external secrets

To list the external secrets in your Akka project, you can use the Akka CLI.

CLI Use the `akka secret external list` command:

```command
akka secret external list
```

### <a href="about:blank#_removing_external_secrets"></a> Removing external secrets

To remove an external secret from your Akka project, you can use the Akka CLI.

CLI `akka secret external delete` command:

```command
akka secret external delete <secret-name>
```

## <a href="about:blank#_aws_secrets"></a> AWS Secrets

Akka services running on AWS can access external secrets from AWS Secrets Manager and AWS Systems Manager Parameter Store.

### <a href="about:blank#_setting_up"></a> Setting up

Before you set up AWS external secrets, you will need the following information:

- The account ID of your AWS account, which we will refer to in the scripts below using the environment variable `AWS_ACCOUNT_ID`.
- The region for your AWS account, which we will refer to in the scripts below using the environment variable `AWS_REGION`.
- The ID of the Akka project that you wish to access to the secrets, which we will refer to in the scripts below using the environment variable `AKKA_PROJECT_ID`. This is a UUID, and can be obtained using the `akka project get` command.
- The name of the service that you wish to access the secrets, which we will refer to in the scripts below using the environment variable `AKKA_SERVICE_NAME`.
The following script can set them:

```command
export AWS_ACCOUNT_ID=123456789012
export AWS_REGION=us-east-2
export AKKA_PROJECT_ID=bc16cf0c-909f-402d-bbb0-88ea1d582854
export AKKA_SERVICE_NAME=my-service
```
Now, you will need to determine the OIDC issuer for your region. This can be determined by running:

```command
akka projects regions workload-identity-info
```
If you only have one region, the above will give you some helpful snippets that may be used below. If you have more than one region, you can specify the region you want the info for using the `--region` flag.

Copy the issuer and place it in an environment variable called `AKKA_OIDC_ISSUER`, or if you only have a single region, you can do so using the following command:

```command
export AKKA_OIDC_ISSUER=$(akka projects regions workload-identity-info -o go-template='{{(index .Items 0).WorkloadIdentity.Aws.OidcIssuer}}')
```
If you haven’t run this for your AWS account and this region yet, you will need to create the OIDC provider in your AWS account. This can be done by running:

```command
aws iam create-open-id-connect-provider --url $AKKA_OIDC_ISSUER \
  --thumbprint-list 06b25927c42a721631c1efd9431e648fa62e1e39 \
  --client-id-list sts.amazonaws.com \
  --tags Key=akka-region,Value=akka-region-name
```
AWS often refers to an OIDC provider via the issuer with the `https://` stripped off of it, so for convenience, we will also set that here:

```command
export AKKA_OIDC_PROVIDER=$(echo $AKKA_OIDC_ISSUER | sed -e "s/^https:\/\///")
```
Now create a secret that you want the service to access:

```command
aws --region "$AWS_REGION" secretsmanager  create-secret --name my-secret \
  --secret-string '{"username":"some-user", "password":"hunter2"}'
```
Now create a policy that allows access to the secret:

```command
POLICY_ARN=$(aws --region "$AWS_REGION" --query Policy.Arn --output text iam create-policy \
  --policy-name akka-secret-access-policy --policy-document '{
    "Version": "2012-10-17",
    "Statement": [ {
        "Effect": "Allow",
        "Action": ["secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret"],
        "Resource": ["arn:*:secretsmanager:*:*:secret:my-secret-??????"]
    } ]
}')
```
Now create a role that the Akka service will assume bound to the policy:

```command
TRUST_POLICY_JSON=$(cat <<EOF
{
  "Version":"2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::${AWS_ACCOUNT_ID}:oidc-provider/$AKKA_OIDC_PROVIDER"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "${AKKA_OIDC_PROVIDER}:aud": "sts.amazonaws.com",
          "${AKKA_OIDC_PROVIDER}:sub": "system:serviceaccount:${AKKA_PROJECT_ID}:klx-$AKKA_SERVICE_NAME"
        }
      }
    }
  ]
}
EOF
)

aws iam create-role --role-name akka-service-role --assume-role-policy-document "$TRUST_POLICY_JSON" \
  --description "My Akka service role"

aws iam attach-role-policy --role-name akka-service-role \
  --policy-arn=arn:aws:iam::$AWS_ACCOUNT_ID:policy/akka-secret-access-policy
```
Note the `klx-` prefix before the service name in the OIDC provider subject.

Finally, we can tell Akka to assume this role for your service:

```command
akka service deploy $AKKA_SERVICE_NAME \
  --aws-workload-identity-role-arn arn:aws:iam::$AWS_ACCOUNT_ID:role/akka-service-role
```

### <a href="about:blank#_managing_aws_secrets_using_the_project_descriptor"></a> Managing AWS secrets using the project descriptor

The best way to manage AWS secrets is using the project descriptor. Please refer to [Project Descriptor reference](../../reference/descriptors/project-descriptor.html) for details.

### <a href="about:blank#_adding_aws_secrets_with_the_cli"></a> Adding AWS secrets with the CLI

To add AWS secrets to your Akka project, you can use the Akka CLI.

CLI Use the `akka secrets external create aws` command.

```command
akka secrets external create aws my-external-secret \ // (1)
  --object-name arn:aws:secretsmanager:$AWS_REGION:$AWS_ACCOUNT_ID:secret:my-secret-QNTAHI \ // (2)
  --object-alias some-file-name // (3)
```

| **1** | External secret name |
| **2** | The ARN of the secret |
| **3** | The name of the file to mount the secret as |
Adding multiple objects can be done by updating the secret after initial creation.

### <a href="about:blank#_updating_aws_secrets"></a> Updating AWS secrets

CLI Use the `akka secrets external update aws` command.

```command
akka secrets external update aws my-external-secret \
  --object-name arn:aws:secretsmanager::$AWS_ACCOUNT_ID:secret:some-other-secret-ENTHOI \
  --object-alias some-other-file-name
```
When updating, if the passed in object name exists, the object will be updated, otherwise a new object will be added to the secret.

## <a href="about:blank#_azure_keyvault"></a> Azure KeyVault

Akka services running on Azure can access external secrets from Azure KeyVault.

### <a href="about:blank#_setting_up_2"></a> Setting up

Before you set up Azure KeyVault, you will need the following information:

- The name of the Azure KeyVault that you wish to access, which we will refer to in the scripts below using the environment variable `KEYVAULT_NAME`.
- The ID of the Akka project that you wish to access to the secrets, which we will refer to in the scripts below using the environment variable `AKKA_PROJECT_ID`. This is a UUID, and can be obtained using the `akka project get` command.
- The name of the service that you wish to access the secrets, which we will refer to in the scripts below using the environment variable `AKKA_SERVICE_NAME`.
The following script can set them:

```command
export KEYVAULT_NAME=my-keyvault-name
export AKKA_PROJECT_ID=bc16cf0c-909f-402d-bbb0-88ea1d582854
export AKKA_SERVICE_NAME=my-service
```
Now, you will need to determine the OIDC issuer for your region. This can be determined by running:

```command
akka projects regions workload-identity-info
```
If you only have one region, the above will give you some helpful snippets that may be used below. If you have more than one region, you can specify the region you want the info for using the `--region` flag.

Copy the issuer and place it in an environment variable called `AKKA_OIDC_ISSUER`, or if you only have a single region, you can do so using the following command:

```command
export AKKA_OIDC_ISSUER=`akka projects regions workload-identity-info
-o go-template='{{(index .Items 0).WorkloadIdentity.Azure.OidcIssuer}}'`
```
Now you need to create an application to access the secrets on behalf of your service. We’ll place the name of this application in an environment variable called `APPLICATION_NAME`, and then obtain the client ID for the application and place that in an environment variable called `APPLICATION_CLIENT_ID`:

```command
export APPLICATION_NAME="my-akka-service-application"
az ad sp create-for-rbac --name "${APPLICATION_NAME}"
export APPLICATION_CLIENT_ID=$(az ad sp list --display-name ${APPLICATION_NAME} --query '[0].appId' -otsv)
```
Now we need to grant this application access to keys, secrets and certs in the KeyVault:

```command
az keyvault set-policy -n $KEYVAULT_NAME --key-permissions get --spn ${APPLICATION_CLIENT_ID}
az keyvault set-policy -n $KEYVAULT_NAME --secret-permissions get --spn ${APPLICATION_CLIENT_ID}
az keyvault set-policy -n $KEYVAULT_NAME --certificate-permissions get --spn ${APPLICATION_CLIENT_ID}
```
Now to federate the credentials, we need the application object id of the application:

```command
export APPLICATION_OBJECT_ID="$(az ad app show --id ${APPLICATION_CLIENT_ID} --query id -otsv)"
```
Now we’ll create a JSON parameters file for federating the credentials:

```command
cat <<EOF > params.json
{
  "name": "akka-service-federated-credential",
  "issuer": "${AKKA_OIDC_ISSUER}",
  "subject": "system:serviceaccount:${AKKA_PROJECT_ID}:klx-${AKKA_SERVICE_NAME}",
  "description": "Akka service federated credential",
  "audiences": [
    "api://AzureADTokenExchange"
  ]
}
EOF
```
And finally federate the credentials:

```command
az ad app federated-credential create --id "${APPLICATION_OBJECT_ID}" --parameters @params.json
```

### <a href="about:blank#_managing_azure_keyvault_secrets_using_the_project_descriptor"></a> Managing Azure KeyVault secrets using the project descriptor

The best way to manage Azure KeyVault secrets is using the project descriptor. Please refer to [Project Descriptor reference](../../reference/descriptors/project-descriptor.html) for details.

### <a href="about:blank#_adding_azure_keyvault_secrets"></a> Adding Azure KeyVault secrets

To add secrets to your Akka project, you can use the Akka CLI. You will need the following information:

- The name of the KeyVault
- The Tenant ID for the KeyVault
- The Application Client ID of the application created above.
CLI Use the `akka secret external azure create` command.

```command
akka secret external azure create my-external-secret \ // (1)
  --key-vault-name $KEYVAULT_NAME \
  --tenant-id $TENANT_ID \
  --client-id $APPLICATION_CLIENT_ID \
  --object-name some-secret \ // (2)
  --object-type secret // (3)
```

| **1** | External secret name |
| **2** | The name of the object (secret) in the key store |
| **3** | The type of the secret, either secret, key or cert |
Adding multiple objects can be done by updating the secret after initial creation.

### <a href="about:blank#_updating_azure_keyvault_secrets"></a> Updating Azure KeyVault secrets

CLI Use the `akka secret external azure update` command.

```command
akka secret external azure update my-external-secret \
  --object-name some-other-secret \
  --object-type secret
```
When updating, if the passed in object name exists, the object will be updated, otherwise a new object will be added to the secret.

## <a href="about:blank#_gcp_secret_manager"></a> GCP Secret Manager

Akka services running on GCP can access external secrets from GCP Secret Manager. Authentication uses Workload Identity Federation. Akka presents an identity token that GCP trusts via a pre-configured identity pool, so no service account keys are needed.

### <a href="about:blank#_setting_up_3"></a> Setting up

Before setting up GCP Secret Manager, you will need:

- A GCP project with billing enabled, which we will refer to below using the environment variable `GCP_PROJECT_ID`.
- The <a href="https://cloud.google.com/sdk/docs/install">Google Cloud CLI (`gcloud`)</a> installed and authenticated.
- The Secret Manager API enabled in your GCP project.
The following script can set up your environment:

```command
export GCP_PROJECT_ID=my-gcp-project
gcloud auth login
gcloud config set project $GCP_PROJECT_ID
```
Enable the Secret Manager API if you haven’t already:

```command
gcloud services enable secretmanager.googleapis.com
```

#### <a href="about:blank#_create_a_secret_in_gcp_secret_manager"></a> Create a secret in GCP Secret Manager

```command
gcloud secrets create my-secret --replication-policy="automatic"
echo -n "my-secret-value" | gcloud secrets versions add my-secret --data-file=-
```
You can verify the secret was stored correctly:

```command
gcloud secrets versions access latest --secret="my-secret"
```

#### <a href="about:blank#_grant_akka_access_to_your_gcp_secrets"></a> Grant Akka access to your GCP secrets

First, retrieve the workload identity information for your Akka project:

```command
akka projects regions workload-identity-info
```
This outputs a workload identity pool path, a **principal** (for a specific service), and a **principalSet** (for all services in the project).

To grant access to all services in your Akka project, use the `principalSet` value:

```command
gcloud secrets add-iam-policy-binding my-secret \
    --project=$GCP_PROJECT_ID \
    --role="roles/secretmanager.secretAccessor" \
    --member="principalSet://iam.googleapis.com/projects/GCP_PROJECT_NUMBER/locations/global/workloadIdentityPools/POOL_ID/namespace/NAMESPACE_ID" // (1)
```

| **1** | Replace the `--member` value with the `principalSet` from the `akka projects regions workload-identity-info` output |
To grant access to a specific Akka service only, use the `principal` value instead:

```command
gcloud secrets add-iam-policy-binding my-secret \
    --project=$GCP_PROJECT_ID \
    --role="roles/secretmanager.secretAccessor" \
    --member="principal://iam.googleapis.com/projects/GCP_PROJECT_NUMBER/locations/global/workloadIdentityPools/POOL_ID/subject/ns/NAMESPACE_ID/sa/kalix-SERVICE_NAME" // (1)
```

| **1** | Copy the exact `principal` value from the `akka projects regions workload-identity-info` output rather than constructing it manually. Note the `kalix-` prefix before the Akka service name. Akka uses this prefix internally when registering workload identities |
Repeat the IAM binding for each secret that your service needs to access.

### <a href="about:blank#_managing_gcp_secrets_using_the_project_descriptor"></a> Managing GCP secrets using the project descriptor

The best way to manage GCP Secret Manager secrets is using the project descriptor. Please refer to [Project Descriptor reference](../../reference/descriptors/project-descriptor.html) for details.

### <a href="about:blank#_adding_gcp_secret_manager_secrets"></a> Adding GCP Secret Manager secrets

To add a GCP external secret to your Akka project, you can use the Akka CLI. You will need:

- The GCP project ID
- The name of the secret in GCP Secret Manager
CLI Use the `akka secrets external create gcp` command.

```command
akka secrets external create gcp my-external-secret \ // (1)
  --project-id $GCP_PROJECT_ID \ // (2)
  --object-name my-secret \ // (3)
  --object-path my-secret // (4)
```

| **1** | External secret name in Akka |
| **2** | The GCP project ID containing the secret |
| **3** | The name of the secret in GCP Secret Manager |
| **4** | The path for the mounted file |
Adding multiple objects can be done by updating the secret after initial creation.

### <a href="about:blank#_updating_gcp_secret_manager_secrets"></a> Updating GCP Secret Manager secrets

CLI Use the `akka secrets external update gcp` command.

```command
akka secrets external update gcp my-external-secret \
  --object-name another-secret \
  --object-path another-secret
```
When updating, if the passed in object name exists, the object will be updated, otherwise a new object will be added to the secret.

## <a href="about:blank#_mount_secrets_to_the_filesystem_of_your_service"></a> Mount secrets to the filesystem of your service

External secrets are provided to your service through filesystem mounts. Unlike regular Akka secrets, external secrets cannot be injected as environment variables.

|  | External secrets are never stored in Kubernetes Secrets or etcd, and are never read by the Kubernetes API server or the kubelet. Instead, a process on the node running on behalf of the pod projects the secret value directly into the pod’s filesystem. This is why external secrets can only be mounted as files. Environment variable injection would require the kubelet to read the secret value, which would defeat the purpose. Only the service itself ever accesses the secret. |
To mount an external secret, declare a `volumeMount` in your [service descriptor](../../reference/descriptors/service-descriptor.html):

```yaml
resource: Service
resourceVersion: v1
metadata:
  name: my-service
spec:
  image: my-container-registry/my-image:latest
  volumeMounts:
  - mountPath: /secrets/my-secret // (1)
    externalSecret:
      provider: my-external-secret // (2)
```

| **1** | The path where the secret will be available inside the container |
| **2** | The name of the external secret created with `akka secret external create` |
The mount path is a directory. The file within it is named after the object’s `path` (for GCP) or `name` / `alias` (for Azure), as configured when creating the external secret. For example, if you created an external secret with `--object-path my-secret` and mounted it at `/secrets/my-secret`, the secret value is readable at `/secrets/my-secret/my-secret`:

```java
String secretValue = Files.readString(Path.of("/secrets/my-secret/my-secret")).trim();
```
Deploy the service descriptor using `akka project apply`:

```command
akka project apply --file project.yaml
```

|  | While `akka service deploy` is a convenience command for getting started, production deployments should use service descriptors checked into version control and deployed with `akka project apply`. Descriptors give you full control over volume mounts, environment variables, and other service configuration. |

<!-- <footer> -->
<!-- <nav> -->
[Manage secrets](secrets.html) [Services](../services/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->