<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Projects](index.html)
- [Manage secrets](secrets.html)

<!-- </nav> -->

# Manage secrets

Akka provides secret management for each project. Secrets are for passwords, login credentials, keys, etc. You can provide secrets to your services through environment variables. When you display the service information, the content of the secrets will not display.

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
CLI Use the `akka secret create` command.

```command
akka secret create generic db-secret \ // (1)
  --literal username=admin \
  --literal pwd=my_passwd // (2)
```

| **1** | Secret name |
| **2** | Contents (as key/value pairs) You can also set a secret from a file, using the `--from-file` argument:

```command
akka secret create generic some-key \
  --from-file key=path/to/my-key-file
``` |

### <a href="about:blank#_updating_secrets"></a> Updating secrets

CLI Secrets can be updated using the `akka secret update` command, in the same way as the `akka secret create` command:

```command
akka secret update generic db-secret \
  --literal username=new-username \
  --literal pwd=new-password
```

### <a href="about:blank#_listing_secrets"></a> Listing secrets

To list the secrets in your Akka project, you can use the Akka CLI or the Akka Console. For security purposes, they only show content keys. Neither the CLI nor the Console will show content values of a secret.

CLI Use the `akka secret list` command:

```command
akka secret list
```
The results should look something like:

NAME         TYPE      KEYS
db-secret    generic   username,pwd Console
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
   pwd Console
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

## <a href="about:blank#_set_secrets_as_environment_variables_for_a_service"></a> Set secrets as environment variables for a service

To set secrets as environment variables for a service, you can use the Akka CLI.

CLI `akka service deploy` command with parameter `--secret-env`:

```command
akka service deploy <service-name> <container-image>  \
    --secret-env MY_VAR1=db-secret/username,MY_VAR2=db-secret/pwd  // (1)
```

| **1** | The value for an environment variable that refers to a secret is of the form `<secret-name>/<secret-key>` |

## <a href="about:blank#_display_secrets_as_environment_variables_for_a_service"></a> Display secrets as environment variables for a service

To set secrets as environment variables for a service, you can use the Akka CLI or the Akka Console.

CLI `akka service get`:

```command
akka service get <service-name>
```
The results should look something like:

Service: 	<service-name>
Created: 	24s
Description:
Status: 	Running
Image: 		<container-image-path>
Env variables:
		MY_VAR1=db-secret/username
		MY_VAR2=db-secret/pwd

Generation: 	1
Store: 		<store-name> Console
1. Sign in to your Akka account at: [https://console.akka.io](https://console.akka.io/)
2. Click the project to which your service belongs.
3. Click the service.
4. In the `Properties: <service-name>` panel, you should see the environment variables.

## <a href="about:blank#_see_also"></a> See also

- <a href="../../reference/cli/akka-cli/akka_secrets.html#_see_also">`akka secrets` commands</a>

<!-- <footer> -->
<!-- <nav> -->
[Aiven for Kafka](broker-aiven.html) [Services](../services/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->