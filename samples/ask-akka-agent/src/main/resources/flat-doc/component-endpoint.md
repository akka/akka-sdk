You would normally not expose Entities, Workflows and Views directly to the outside world, but have an [Endpoint](java:http-endpoints.adoc) or [Consumer](java:consuming-producing.adoc) in front of it.

However, a built-in HTTP endpoint for the component can be enabled. The primary purpose of this component endpoint is for convenience in local development so that you can try the entity with `curl` without having to define the final `HttpEndpoint`.

### Enable access

To enable the component’s HTTP endpoint you can disable access control checks in `application.conf`:

**src/main/resources/application.conf**

```conf
akka.javasdk.dev-mode.acl.enabled = false
```

Alternatively, start with:

```shell
mvn compile exec:java -Dakka.javasdk.dev-mode.acl.enabled=false
```

Note that this `acl` configuration is only used when running locally and not in production. To enable component endpoints in production you must add `@Acl` to the component class:

```java
// Opened up for access from the public internet.
// For actual services meant for production this must be carefully considered,
// and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
```
