<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Projects](index.html)
- [Manage secrets](secrets.html)

<!-- </nav> -->

# Manage secrets

Akka provides secret management for each project. Secrets are for passwords, login credentials, API keys, etc. You can provide secrets to your services through environment variables. When you display the service information, the content of the secrets will not display.

## <a href="about:blank#_understanding_akka_secrets_structure"></a> Understanding Akka secrets structure

|  | Each Akka secret has a two-level structure:

  1. **Secret name** — The container for one or more key-value pairs
  2. **Key(s)** — One or more named values within the secret
When you reference a secret in an environment variable, you use the format: `SECRET_NAME/KEY_NAME`

This allows you to group related credentials together (e.g., a database secret with `username` and `password` keys) or use a simple single-key pattern for individual values (e.g., an API key). |

## <a href="about:blank#_quick_start_example"></a> Quick start example

Here’s a common pattern for storing and using a single API key:

```command
# 1. Create a secret with a single key-value pair
akka secret create generic openai-key --literal value=sk-abc123...

# 2. Deploy your service with the secret as an environment variable
akka service deploy my-service my-image:latest \
  --secret-env OPENAI_API_KEY=openai-key/value
```
In your service code, you can now access `OPENAI_API_KEY` as a regular environment variable. The mapping works like this:

Environment Variable Name: OPENAI_API_KEY
           ↓
Secret Name: openai-key
           ↓
Key Name: value
           ↓
Actual Value: sk-abc123... (the API key)
## <a href="about:blank#_manage_secrets_in_a_project"></a> Manage secrets in a project

### <a href="about:blank#_adding_secrets"></a> Adding secrets

To add secrets to your Akka project, you can use the Akka CLI.

|  | To mark your project as the target of subsequent commands, use the following command:

```command
akka config set project sample-project
``` |
When you create a secret, it contains:

- secret name
- contents (as key/value pairs)

#### <a href="about:blank#_example_1_single_value_secret_api_key"></a> Example 1: Single-value secret (API key)

The most common pattern is to create a secret with a single key-value pair:

```command
akka secret create generic openai-key \ // (1)
  --literal value=sk-abc123... // (2)
```

| **1** | Secret name: `openai-key` |
| **2** | Single key-value pair where the key is `value` and the value is your API key |
This pattern works well for API keys, tokens, or any single credential.

#### <a href="about:blank#_example_2_multi_key_secret_database_credentials"></a> Example 2: Multi-key secret (database credentials)

You can also group related credentials together in a single secret:

```command
akka secret create generic db-secret \ // (1)
  --literal username=admin \
  --literal password=my_passwd \
  --literal host=db.example.com // (2)
```

| **1** | Secret name: `db-secret` |
| **2** | Multiple key-value pairs for related database credentials |
You can also set a secret from a file, using the `--from-file` argument:

```command
akka secret create generic tls-cert \
  --from-file cert=path/to/certificate.pem
```

### <a href="about:blank#_updating_secrets"></a> Updating secrets

CLI Secrets can be updated using the `akka secret update` command, in the same way as the `akka secret create` command:

```command
akka secret update generic db-secret \
  --literal username=new-username \
  --literal password=new-password
```

### <a href="about:blank#_listing_secrets"></a> Listing secrets

To list the secrets in your Akka project, you can use the Akka CLI or the Akka Console. For security purposes, they only show content keys. Neither the CLI nor the Console will show content values of a secret.

CLI Use the `akka secret list` command:

```command
akka secret list
```
The results should look something like:

NAME          TYPE      KEYS
db-secret     generic   username,password,host
openai-key    generic   value Console
1. Sign in to your Akka account at: [https://console.akka.io](https://console.akka.io/)
2. Click the project for which you want to see the secrets.
3. Using the left pane or top navigation bar, click **Secrets** to open the Secrets page which lists the secrets.

### <a href="about:blank#_display_secret_contents"></a> Display secret contents

To display secret contents for your Akka project, you can use the Akka CLI or the Akka Console. For security purposes, they only show content keys. Neither the CLI nor the Console will show content values of a secret.

CLI Use the `akka secret get` command:

```command
akka secret get <secret-name>
```
The results should look something like:

NAME: db-secret
KEYS:
   username
   password
   host Console
1. Sign in to your Akka account at: [https://console.akka.io](https://console.akka.io/)
2. Click the project for which you want to see the secrets.
3. Using the left pane or top navigation bar, click **Secrets** to open the Secrets page which lists the secrets.
4. Click the secret you wish to review.

### <a href="about:blank#_removing_secrets"></a> Removing secrets

To remove the secret for your Akka project, you can use the Akka CLI.

CLI `akka secret delete` command:

```command
akka secret delete <secret-name>
```

## <a href="about:blank#_using_secrets_in_service_deployments"></a> Using secrets in service deployments

To use secrets in your service, you reference them as environment variables. The format is always: `ENV_VAR_NAME=SECRET_NAME/KEY_NAME`

### <a href="about:blank#_deploy_with_secrets_using_cli"></a> Deploy with secrets using CLI

CLI Use the `akka service deploy` command with the `--secret-env` parameter:

**Example 1: Single API key**

```command
akka service deploy my-service my-image:latest \
  --secret-env OPENAI_API_KEY=openai-key/value // (1)
```

| **1** | Maps environment variable `OPENAI_API_KEY` to the `value` key in the `openai-key` secret **Example 2: Multiple database credentials**

```command
akka service deploy my-service my-image:latest \
  --secret-env DB_USER=db-secret/username,DB_PASS=db-secret/password,DB_HOST=db-secret/host // (2)
``` |
| **2** | Maps three environment variables to three different keys within the same `db-secret` |

### <a href="about:blank#_deploy_with_secrets_using_a_deploy_file"></a> Deploy with secrets using a deploy file

You can also specify secrets in a deployment descriptor file:

```yaml
secretEnv:
  - name: OPENAI_API_KEY
    secretName: openai-key
    secretKey: value
  - name: DB_USER
    secretName: db-secret
    secretKey: username
  - name: DB_PASS
    secretName: db-secret
    secretKey: password
  - name: DB_HOST
    secretName: db-secret
    secretKey: host
```
Then deploy with:

```command
akka service apply -f deployment.yaml
```

## <a href="about:blank#_display_secrets_as_environment_variables_for_a_service"></a> Display secrets as environment variables for a service

To view how secrets are configured as environment variables for a service, you can use the Akka CLI or the Akka Console.

CLI `akka service get`:

```command
akka service get <service-name>
```
The results should look something like:

Service:     <service-name>
Created:     24s
Description:
Status:      Running
Image:       <container-image-path>
Env variables:
        OPENAI_API_KEY=openai-key/value
        DB_USER=db-secret/username
        DB_PASS=db-secret/password
        DB_HOST=db-secret/host

Generation:  1
Store:       <store-name>
|  | The output shows the reference path (`secret-name/key-name`), not the actual secret values. This is for security purposes. |
Console
1. Sign in to your Akka account at: [https://console.akka.io](https://console.akka.io/)
2. Click the project to which your service belongs.
3. Click the service.
4. In the `Properties: <service-name>` panel, you should see the environment variables.

## <a href="about:blank#_common_patterns_and_best_practices"></a> Common patterns and best practices

### <a href="about:blank#_single_value_secrets"></a> Single-value secrets

For individual credentials like API keys or tokens, use the pattern:

```command
akka secret create generic <secret-name> --literal value=<your-secret>
```
Then reference it as:

```command
--secret-env ENV_VAR_NAME=<secret-name>/value
```

### <a href="about:blank#_multi_value_secrets"></a> Multi-value secrets

For grouped credentials (e.g., database, OAuth), create a secret with multiple keys:

```command
akka secret create generic oauth-creds \
  --literal client_id=abc123 \
  --literal client_secret=xyz789 \
  --literal tenant_id=def456
```
Then reference each key separately:

```command
--secret-env OAUTH_CLIENT_ID=oauth-creds/client_id,OAUTH_CLIENT_SECRET=oauth-creds/client_secret,OAUTH_TENANT_ID=oauth-creds/tenant_id
```

### <a href="about:blank#_why_the_two_level_structure"></a> Why the two-level structure?

The `SECRET_NAME/KEY_NAME` pattern provides flexibility:

- **Single credentials**: Use a simple `secret-name/value` pattern
- **Grouped credentials**: Store related values together (e.g., all database credentials in one secret)
- **Key rotation**: Update individual keys without changing the secret name or environment variable mapping

## <a href="about:blank#_see_also"></a> See also

- <a href="../../reference/cli/akka-cli/akka_secrets.html#_see_also">`akka secrets` commands</a>

<!-- <footer> -->
<!-- <nav> -->
[Azure Event Hubs](broker-azure-eventhubs.html) [Manage external secrets](external-secrets.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->