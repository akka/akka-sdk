<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Organizations](index.html)
- [Regions](regions.html)

<!-- </nav> -->

# Regions

## <a href="about:blank#_overview"></a> Overview

Akka projects are deployed to specific regions, which are tied to the backend infrastructure Akka uses to support projects. Regions:

- Are linked to a particular cloud provider (e.g., GCP, AWS)
- Exist in specific geographic locations (e.g., Europe, North America)
- May have unique performance characteristics
- Can provide varying levels of isolation (e.g., dedicated plans offer isolated regions)

### <a href="about:blank#_for_organizations"></a> For Organizations

When an organization is created, it is assigned access to one or more regions. A region must be specified when creating a new project within the organization.

For example, if the organization `myorg` has access to the region `aws-us-east-`2, you would create a project in that region using the following command:

```command
akka project new myproject --organization myorg --region aws-us-east-2
```

### <a href="about:blank#_finding_available_regions"></a> Finding Available Regions

If you’re unsure which regions your organization has access to, there are two options:

1. **Error Prompt**: If you omit the `--region` flag when creating a new project, Akka will inform you of the available regions in the error message. For instance:

```command
$ akka project new myproject --organization myorg

--region is a required flag. The following regions are available: [aws-us-east-2]
```
2. **List Regions Command**: You can list the regions directly using the following command:

```command
akka regions list --organization myorg
```
Example output:

```command
NAME            ORGANIZATION
aws-us-east-2   db805ff5-4fbd-4442-ab56-6e6a9a3c200a
```

## <a href="about:blank#_requesting_new_regions"></a> Requesting new regions

By default organizations are limited to which regions they can use, particularly for trial organizations. If you would like access to other regions, you can use the **?** in the upper right of the [Akka Console](https://console.akka.io/) to request additional regions via the **Contact support** menu item.

## <a href="about:blank#_byoc_and_self_hosted_regions"></a> BYOC and self-hosted regions

Akka also supports Bring Your Own Cloud (BYOC) meaning that we can run regions in your AWS, Azure, or Google Cloud account. These are not available to trial users.

These regions work just like any other regions and are exclusive to your workloads.

To get a BYOC region setup you can [Contact Us](https://www.akka.io/contact).

To learn more about self-hosted Akka regions please To get a BYOC region setup you can [Submit a request](https://www.akka.io/contact) for more information.

## <a href="about:blank#_see_also"></a> See also

- <a href="../../reference/cli/akka-cli/akka_regions.html#_see_also">`akka regions` commands</a>
- <a href="../../reference/cli/akka-cli/akka_projects_regions.html#_see_also">`akka project regions` commands</a>

<!-- <footer> -->
<!-- <nav> -->
[Manage users](manage-users.html) [Billing](billing.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->