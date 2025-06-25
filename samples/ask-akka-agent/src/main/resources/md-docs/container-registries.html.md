<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Projects](index.html)
- [Configure a container registry](container-registries.html)

<!-- </nav> -->

# Configure a container registry

Akka deploys services as container images. These images, which include your Akka services and any dependencies, are produced using Maven (i.e., `mvn install`) and sent to Akka for deployment. Before deployment, the container must be made accessible in a container registry.

Akka provides a built-in *Akka Container Registry (ACR)* which is pre-configured for your convenience. Alternatively, [external container registries](external-container-registries.html) are also supported for Akka.

## <a href="about:blank#_akka_container_registry"></a> Akka Container Registry

The *Akka Container Registry (ACR)* is available to all Akka users and supported across all Akka regions, allowing for easy, integrated deployments without dependency on external registry connectivity. Authentication is built-in, so deployments, restarts, and scaling operate independently of external networks.

## <a href="about:blank#_prerequisites"></a> Prerequisites

Ensure the following prerequisites are met before continuing:

- The current user must be logged into Akka.
- Docker must be installed and accessible for the current user.
To verify your Akka login status, run `akka auth current-login`. (If not logged in, use `akka auth login`.)

```command
> akka auth current-login
ba6f49b0-c4e1-cccc-ffff-30053f652c42   user test@akka.io   true       CLI login from machine.localdomain(127.0.0.1)   3d21h
```
Confirm that you have Docker installed by checking the version:

```command
> docker --version
Docker version 27.3.1, build ce1223035a
```

## <a href="about:blank#_configuring_acr_authentication"></a> Configuring ACR authentication

The *Akka Container Registry* uses an access token generated via Akka for authentication. When you initiate a `docker push`, an intermediate credential helper retrieves this token using the Akka CLI. Configure the Docker credentials helper as follows:

```command
> akka auth container-registry configure
This operation will update your '.docker/config.json' file. Do you want to continue?
Use the arrow keys to navigate: ↓ ↑ → ←
? >:
  ▸ No
    Yes
```
Select "Yes" to proceed.

Once configuration completes, it will display the ACR hostnames for all available regions:

```none
Docker configuration file successfully updated.
Available Akka Container Registry hosts per region:
PROVIDER   CLOUD       REGION          ORGANIZATION   ORGANIZATION_ID                        AKKA CONTAINER REGISTRY
gcp        us-east1    gcp-us-east1    PUBLIC         NONE                                   acr.us-east-1.akka.io
gcp        us-east1    gcp-us-east1    acme           cde1044c-b973-4220-8f65-0f7d317bb458   acr.us-east-1.akka.io
```
The Akka Container Registry is now ready for use.

See [deploy and manage services](../services/deploy-service.html) for further details on how to deploy a service.

## <a href="about:blank#_see_also"></a> See also

- <a href="../../reference/cli/akka-cli/akka_container-registry.html#_see_also">`akka container-registry` commands</a>

<!-- <footer> -->
<!-- <nav> -->
[Manage users](manage-project-access.html) [Configure an external container registry](external-container-registries.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->