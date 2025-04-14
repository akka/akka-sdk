# Configure a container registry

Akka deploys services as container images. These images, which include your Akka services and any dependencies, are produced using Maven (i.e., `mvn install`) and sent to Akka for deployment. Before deployment, the container must be made accessible in a container registry.

Akka provides a built-in _Akka Container Registry (ACR)_ which is pre-configured for your convenience. Alternatively, [external container registries](operations:projects/external-container-registries.adoc) are also supported for Akka.

## Akka Container Registry

The _Akka Container Registry (ACR)_ is available to all Akka users and supported across all Akka regions, allowing for easy, integrated deployments without dependency on external registry connectivity. Authentication is built-in, so deployments, restarts, and scaling operate independently of external networks.

## Prerequisites

Ensure the following prerequisites are met before continuing:

* The current user must be logged into Akka.
* Docker must be installed and accessible for the current user.

To verify your Akka login status, run `akka auth current-login`. (If not logged in, use `akka auth login`.)

```command line
> akka auth current-login
ba6f49b0-c4e1-cccc-ffff-30053f652c42   user test@akka.io   true       CLI login from machine.localdomain(127.0.0.1)   3d21h
```

Confirm that you have Docker installed by checking the version:

```command line
> docker --version
Docker version 27.3.1, build ce1223035a
```

## Configuring ACR authentication

The _Akka Container Registry_ uses an access token generated via Akka for authentication. When you initiate a `docker push`, an intermediate credential helper retrieves this token using the Akka CLI. Configure the Docker credentials helper as follows:

```command line
> akka auth container-registry configure
This operation will update your '.docker/config.json' file. Do you want to continue?
Use the arrow keys to navigate: ↓ ↑ → ←
? >:
  ▸ No
    Yes
```

Select "Yes" to proceed.

Once configuration completes, it will display the ACR hostnames for all available regions:

```
Docker configuration file successfully updated.
Available Akka Container Registry hosts per region:
PROVIDER   CLOUD       REGION          ORGANIZATION   ORGANIZATION_ID                        AKKA CONTAINER REGISTRY
gcp        us-east1    gcp-us-east1    PUBLIC         NONE                                   acr.us-east-1.akka.io
gcp        us-east1    gcp-us-east1    acme           cde1044c-b973-4220-8f65-0f7d317bb458   acr.us-east-1.akka.io
```

The Akka Container Registry is now ready for use. 

See [deploy and manage services](operations:services/deploy-service.adoc) for further details on how to deploy a service.

## See also

* [`akka container-registry` commands](reference:cli/akka-cli/akka_container-registry.adoc#_see_also)
