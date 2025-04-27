# Deploy and manage services

This guide will walk you through deploying Akka services using the Akka Console and the Akka CLI. By the end, you’ll be able to deploy, check the status, update, and remove services.

## Prerequisites

Before deploying a service, ensure you have the following:

* An [Akka account](https://console.akka.io/register)
* An [Akka project created](operations:projects/create-project.adoc)
* The [Akka CLI installed](reference:cli/installation.adoc)

## Build container image

Build a container image of the service:

```command line
mvn clean install -DskipTests
```

By default, the maven build will produce images with the following format: `container-name:tag-name` where the container name is the `artifactId` and the tag name is the `version` plus the build timestamp.

The docker build output in maven will print something similar to the following:

```command line
DOCKER> Tagging image shopping-cart:1.0-SNAPSHOT-20241028102843 successful!
```
## Deploying a service

Services can be deployed via the Akka CLI.

To deploy your service, use the following command. Replace `my-service` with your service name and update the container name and tag from the `mvn install`:

```command line
akka service deploy my-service container-name:tag-name --push
```

Your service will now begin deploying.

**💡 TIP**\
To combine deploying a service with relevant settings, Akka supports deploying with service descriptors ([see below](#using-service-descriptors)).

## Checking service status

You can verify the deployment status of your service in the Akka Console or with the Akka CLI:

* **Akka CLI**

  Verify the service status from the command line with this command:

  ```command line
  akka service list
  ```

  A service status can be one of the following:
  * **Ready**: All service instances are up-to-date and fully available.
  * **UpdateInProgress**: Service is updating.
  * **Unavailable**: No service instances are available.
  * **PartiallyReady**: Some, but not all, service instances are available.

* **Akka Console**

  1. Open the [**Akka Console**, window="new"](https://console.akka.io).
  2. Navigate to the **Project** where the Service is deployed.
  3. Look for the **Service** card of the Service, it shows the status.

     ![Service card](console-service-status.png)

     A service status can be one of the following:
     * **Ready**: All service instances are up-to-date and fully available.
     * **Update In Progress**: Service is updating.
     * **Unavailable**: No service instances are available.
     * **Partially Ready**: Some, but not all, service instances are available.

## How to update a deployed service

If you need to update your service with a new container image:

1. **Make changes** to your service and **package them** into a **new container image**, see [Build container image](#build-container-image).
2. **Deploy the updated image** by passing the new tag:

   ```command line
   akka service deploy my-service container-name:tag-name-2 --push
   ```

Akka will perform a rolling update, replacing old instances with new ones without downtime.

## Pushing to Akka Container Registry

Pushing images to the Akka Container Registry (ACR) works similarly to other Docker registries, with the added feature that Akka supports multi-region deployments. When deploying to multiple regions, each configured region requires its own ACR. The Akka CLI manages this process automatically.

To push your images to the Akka Container Registry (ACR), use the following command:

```command line
akka container-registry push container-name:tag-name
```

This command will create new tags specifically formatted for ACR, prepending the ACR **URL**, the **organization**, and the **project** names to the image before pushing it.

For example, if your project has two regions with ACRs `acr.us-east-1.akka.io` and `acr.us-east-2.akka.io`, the command will push to: 

* `acr.us-east-1.akka.io/my-organization/my-project/container-name:tag-name`
* `acr.us-east-2.akka.io/my-organization/my-project/container-name:tag-name`

After pushing to all regions, the CLI will display the primary region’s image path, which should be used for service deployment:

```command line
When deploying an Akka service, use the primary region image tag:
	acr.us-east-1.akka.io/my-organization/my-project/container-name:tag-name
```

### ACR image paths

Images in ACR follow a hierarchical structure and can be scoped to either a single project or an entire organization:

* For single-project availability, the image path must include both the organization and project names:

  `my-organization/my-project/container-name:tag-name`
* To make an image available across ***all projects*** within an organization, use only the organization name in the image path:

  `my-organization/container-name:tag-name`

In ACR, this structure reflects Akka’s organizational layout, where an organization can manage multiple projects that host images. Images stored at the organizational root can be deployed in any project within that organization.

As mentioned earlier, the Maven build will produce images with the format `container-name:tag-name` (without `organization` and `project` segments). When pushing images without the organization and project segments, the Akka CLI will populate these segments based on your current `organization` and `project`. 

If desired, you can configure Maven to build images for a specific `organization` or `organization/project`. To do this, configure the `docker.image` property in your pom.xml:

```xml
<properties>
  <docker.image>my-organization/my-project/container-name</docker.image>
</properties>
```
## Pushing to external container registry

If you are not using ACR, use `docker push` command instead.

```command line
docker push container-uri/container-name:tag-name
```

Ensure that your chosen container registry is accessible to all regions in your project.

For further details, see [external container registries](operations:projects/external-container-registries.adoc).

## Using service descriptors

Akka services can also be described and managed with **YAML service descriptors**. See [reference:descriptors/service-descriptor.adoc](reference:descriptors/service-descriptor.adoc).

You can deploy your service using a service descriptor.
For this you need at least the image, which you can get by [building the container image](#build-container-image) and then [pushing it to the container registry](#pushing-to-akka-container-registry):

```command line
akka container-registry push container-name:tag-name
```

Once pushed, you need to use the suggested image from the command’s output in your service descriptor, for example:

```yaml
name: my-service
service:
  image: acr.us-east-1.akka.io/my-organization/my-project/container-name:tag-name
  env:
  - name: SOME_VARIABLE
    value: some value
```

**📌 NOTE**\
You must add the primary region image tag from `akka container-registry push` output.

To apply this descriptor, run:

```command line
akka service apply -f my-service.yaml
```

You can also export an existing service’s descriptor for reference or editing:
```command line
akka service export my-service -f my-service.yaml
```

### Redeploying with a descriptor

After editing the service descriptor (e.g., `my-service.yaml`), redeploy it with:

```command line
akka service apply -f my-service.yaml
```

### Editing the service descriptor in place

Once you have [deployed your service](#deploying-a-service), you can also modify it by editing its service descriptor:

```command line
akka service edit my-service
```

## Removing a deployed service

To delete a service and free its resources, run the following command, replacing `my-service` with the name of the service you want to remove:

```command line
akka service delete my-service
```

The service will be deleted, and its resources will be freed.

During development, with changing domain models, it may be useful to delete a service _including its data_. To delete already stored data and the service, use the `--hard` flag. **This can not be undone.**

```command line
akka service delete my-service --hard
```

## Conclusion

You now know how to deploy, verify, update, and remove Akka services using the Akka CLI. Continue experimenting with different configurations and commands to further enhance your services.

## Related documentation

* [`akka service` CLI commands](reference:cli/akka-cli/akka_services.adoc)
* [Akka Service Descriptor](reference:descriptors/service-descriptor.adoc)
