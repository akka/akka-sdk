<!-- <nav> -->
- [Akka](../../index.html)
- [Reference](../index.html)
- [External secret descriptor](external-secret-descriptor.html)

<!-- </nav> -->

# External Secret Descriptor reference

## <a href="about:blank#route"></a> External Secret descriptor

An external secret allows an Akka service to source secrets from an external secret manager.

| Field | Type | Description |
| --- | --- | --- |
| **azure** | [AzureExternalSecret](about:blank#_azureexternalsecret) | Configuration for Azure KeyVault external secrets. |

### <a href="about:blank#_azureexternalsecret"></a> AzureExternalSecret

Azure KeyVault external secret configuration.

| Field | Type | Description |
| --- | --- | --- |
| **keyVaultName** | string *required* | The name of the KeyVault. |
| **tenantID** | string *required* | The ID of the tenant that the KeyVault is in. |
| **clientID** | string *required* | The ID of the client that was created to access the KeyVault via federated workload identity. |
| **cloudName** | string | If using a non default cloud, the name of the cloud. |
| **objects** | [] [AzureExternalSecretObject](about:blank#_azureexternalsecretobject) *required* | The prefix to match for this route. Must start with a `/`. |

### <a href="about:blank#_azureexternalsecretobject"></a> AzureExternalSecretObject

An Azure KeyVault object that should be mounted as part of the external secret.

| Field | Type | Description |
| --- | --- | --- |
| **name** | string *required* | The name of the object in the KeyVault. |
| **type** | string *required* | The type of object, either `secret`, `key` or `cert`. |
| **alias** | string | The alias for the object. This will be the filename of the object when mounted into the Akka service’s container. Defaults to the object name. |
| **version** | string | The version of the object to mount. Defaults to the latest. |
| **versionHistory** | int | If set and non zero, specifies that multiple versions of the history should be mounted. In such cases, the object name/alias will be a folder, and the top N (where N is the versionHistory) versions of the secret will be placed in that folder as files. The file name for each version will be an integer, starting with 0 for the latest version, 1 for the next most recent, and so on. |
| **encoding** | string | The encoding of the object. Valid types are `utf-8`, `hex` and `base64`. Only valid with type: `secret`. Defaults to `utf-8`. |
| **format** | string | The format of the object. Supported types are `pem` and `pfx`. Defaults to `pem`. |
| **filePermission** | int | The permission of the file being mounted. Defaults to 0644. Must be an octal value between 0000 and 0777 or a decimal value between 0 and 511. Note that YAML accepts both octal and decimal values, with octal values being specified by using a leading 0. Meanwhile JSON requires decimal values. If not specified, the volume’s default mode will be used. |

<!-- <footer> -->
<!-- <nav> -->
[Route descriptor](route-descriptor.html) [Observability descriptor](observability-descriptor.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->