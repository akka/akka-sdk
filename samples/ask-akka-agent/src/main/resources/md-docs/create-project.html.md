<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Projects](index.html)
- [Create](create-project.html)

<!-- </nav> -->

# Create a new project

When creating a new project in Akka, you’ll need to provide a **name**, an optional **description**, and select a **region**. The region determines both the cloud provider and geographical location where your project will be hosted.

## <a href="about:blank#_project_names"></a> Project names

- Use a short but meaningful name that reflects the purpose of the project.
- Keep descriptions short and clear to help collaborators understand the project’s context.
Project Naming Requirements:

- Maximum 63 characters
- Can include: lowercase letters, numbers, hyphens (`-`)
- Must not: start or end with hyphens
- Cannot include: underscores, spaces, or non-alphanumeric characters

## <a href="about:blank#_selecting_a_region"></a> Selecting a region

Regions define the cloud provider and geographical location where your project will be deployed. Consider proximity to your users for lower latency and any compliance or performance requirements when selecting a region.

## <a href="about:blank#_how_to_create_a_new_project"></a> How to create a new project

To create a new project, use either the Akka CLI or the [Akka Console](https://console.akka.io/):

CLI
1. If you haven’t done so yet, [install the Akka CLI](../cli/installation.html) and log into your account:

```command
akka auth login
```
2. To list available regions and organizations, use the following command:

```command
akka regions list --organization=<org>
```
3. Create a project by substituting your project name and placing a short project description name in quotes, followed by the `region` flag and the `organization` flag.

```command
akka projects new <project name> "<project description>" --region=<region> --organization=<org>
```
For example:

```command
akka projects new my-akka-project "My Akka Project" --region=gcp-us-east1 --organization=my-organization
```
Example output:

```none
NAME              DESCRIPTION   ID                                     OWNER                                       REGION
my-akka-project   "My ..        xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx   id:"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"   gcp-us-east1

'my-akka-project' is now the currently active project.
```
UI
1. Log in to [Akka Console](https://console.akka.io/)
2. Navigate to the [Projects](https://console.akka.io/projects) section.
3. Click **Create a project** and fill in the required fields, including name, description, region, and organization.

![Create a project](../_images/console-create-project.png)
4. Review and click **Create Project** to finalize your project.

The new project will show as a card in the **Project** section.
You may now continue and [deploy a Service](../services/deploy-service.html) in the new Project.

## <a href="about:blank#_see_also"></a> See also

- [Deploy and manage services](../services/deploy-service.html)
- <a href="../../reference/cli/akka-cli/akka_projects_new.html#_see_also">`akka projects new` commands</a>
- <a href="../../reference/cli/akka-cli/akka_projects_get.html#_see_also">`akka projects get` commands</a>

<!-- <footer> -->
<!-- <nav> -->
[Projects](index.html) [Manage users](manage-project-access.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->