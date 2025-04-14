# Manage secrets

Akka provides secret management for each project. Secrets are for passwords, login credentials, keys, etc. You can provide secrets to your services through environment variables. When you display the service information, the content of the secrets will not display.

## Manage secrets in a project

### Adding secrets

To add secrets to your Akka project, you can use the Akka CLI.

<dl><dt><strong>📌 NOTE</strong></dt><dd>

To mark your project as the target of subsequent commands, use the following command:

```command window
akka config set project sample-project
```
</dd></dl>

When you create a secret, it contains:

* secret name
* contents (as key/value pairs)

* **CLI**

  Use the `akka secret create` command.

  ```command line
  akka secret create generic db-secret \ ①
    --literal username=admin \
    --literal pwd=my_passwd ②
  ```

  1. Secret name
  2. Contents (as key/value pairs)

     You can also set a secret from a file, using the `--from-file` argument:

     ```command line
     akka secret create generic some-key \
       --from-file key=path/to/my-key-file
     ```

### Updating secrets

* **CLI**

  Secrets can be updated using the `akka secret update` command, in the same way as the `akka secret create` command:

  ```command line
  akka secret update generic db-secret \
    --literal username=new-username \
    --literal pwd=new-password
  ```

### Listing secrets

To list the secrets in your Akka project, you can use the Akka CLI or the Akka Console. For security purposes, they only show content keys. Neither the CLI nor the Console will show content values of a secret.

* **CLI**\
Use the `akka secret list` command:

  ```command line
  akka secret list
  ```

  The results should look something like:

  ```
  NAME         TYPE      KEYS                             
  db-secret    generic   username,pwd  
  ```

* **Console**

  1. Sign in to your Akka account at: https://console.akka.io
  2. Click the project for which you want to see the secrets.
  3. Using the left pane or top navigation bar, click **Secrets** to open the Secrets page which lists the secrets.

### Display secret contents

To display secret contents for your Akka project, you can use the Akka CLI or the Akka Console. For security purposes, they only show content keys. Neither the CLI nor the Console will show content values of a secret.

* **CLI**\
Use the `akka secret get` command:

  ```command line
  akka secret get <secret-name>
  ```

  The results should look something like:

  ```
  NAME: db-secret
  KEYS:
     username
     pwd
  ```

* **Console**

  1. Sign in to your Akka account at: https://console.akka.io
  2. Click the project for which you want to see the secrets.
  3. Using the left pane or top navigation bar, click **Secrets** to open the Secrets page which lists the secrets.
  4. Click the secret you wish to review.

### Removing secrets

To remove the secret for your Akka project, you can use the Akka CLI.

* **CLI**\
`akka secret delete` command:

  ```command line
  akka secret delete <secret-name>
  ```

## Set secrets as environment variables for a service
To set secrets as environment variables for a service, you can use the Akka CLI.

* **CLI**\
`akka service deploy` command with parameter `--secret-env`:

  ```command line
  akka service deploy <service-name> <container-image>  \
      --secret-env MY_VAR1=db-secret/username,MY_VAR2=db-secret/pwd  ①
  ```

  1. The value for an environment variable that refers to a secret is of the form `<secret-name>/<secret-key>`

## Display secrets as environment variables for a service
To set secrets as environment variables for a service, you can use the Akka CLI or the Akka Console.

* **CLI**\
`akka service get`:

  ```command line
  akka service get <service-name>
  ```

  The results should look something like:

  ```
  Service: 	<service-name>
  Created: 	24s
  Description:
  Status: 	Running
  Image: 		<container-image-path>
  Env variables:
  		MY_VAR1=db-secret/username
  		MY_VAR2=db-secret/pwd

  Generation: 	1
  Store: 		<store-name>
  ```

* **Console**

  1. Sign in to your Akka account at: https://console.akka.io
  2. Click the project to which your service belongs.
  3. Click the service.
  4. In the `Properties: <service-name>` panel, you should see the environment variables.

## See also

* [`akka secrets` commands](reference:cli/akka-cli/akka_secrets.adoc#_see_also)
