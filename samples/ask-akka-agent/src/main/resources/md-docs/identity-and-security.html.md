<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Integrations](index.html)
- [Identity & security](identity-and-security.html)

<!-- </nav> -->

# Identity & security

## <a href="about:blank#_akka_native_secret_management"></a> Akka-native secret management

Akka provides built-in secret management at the project level. Secrets are injected as environment variables into your services, supporting:

- Generic secrets (key-value pairs)
- Symmetric and asymmetric encryption keys
- TLS certificates and CA bundles
- Key rotation without environment variable changes
Secret values are never exposed in the CLI or Console.

See [Manage secrets](../../operations/projects/secrets.html) for configuration details.

## <a href="about:blank#_external_secret_providers"></a> External secret providers

### <a href="about:blank#_azure_keyvault"></a> Azure KeyVault

Azure KeyVault integration is built in. See [Manage external secrets](../../operations/projects/external-secrets.html) for setup instructions.

### <a href="about:blank#_other_providers"></a> Other providers

Connect to any secret provider with a Java client library:

- **AWS Secrets Manager** — via the AWS SDK for Java
- **GCP Secret Manager** — via the Google Cloud Client Libraries for Java
- **HashiCorp Vault** — via the Vault Java Driver

## <a href="about:blank#_see_also"></a> See also

- [Manage secrets](../../operations/projects/secrets.html)
- [Manage external secrets](../../operations/projects/external-secrets.html)
- [Access control](../access-control.html)
- [JWT authentication](../auth-with-jwts.html)

<!-- <footer> -->
<!-- <nav> -->
[APIs & protocols](apis-and-protocols.html) [Observability](observability.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->