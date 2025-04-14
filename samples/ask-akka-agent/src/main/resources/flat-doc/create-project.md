# Create a new project

When creating a new project in Akka, you’ll need to provide a **name**, an optional **description**, and select a **region**. The region determines both the cloud provider and geographical location where your project will be hosted.

## Project names

* Use a short but meaningful name that reflects the purpose of the project.
* Keep descriptions short and clear to help collaborators understand the project’s context.

Project Naming Requirements:

* Maximum 63 characters
* Can include: lowercase letters, numbers, hyphens (`-`)
* Must not: start or end with hyphens
* Cannot include: underscores, spaces, or non-alphanumeric characters

## Selecting a region
Regions define the cloud provider and geographical location where your project will be deployed. Consider proximity to your users for lower latency and any compliance or performance requirements when selecting a region.

## How to create a new project

To create a new project, use either the Akka CLI or the [Akka Console, window="new"](https://console.akka.io):

* **CLI**

  1. If you haven’t done so yet, [install the Akka CLI](reference:cli/installation.adoc) and log into your account:

     ```command window
     akka auth login
     ```

  2. To list available regions and organizations, use the following command:

     ```command window
     akka regions list --organization=<org>
     ```
  3. Create a project by substituting your project name and placing a short project description name in quotes, followed by the `region` flag and the `organization` flag.

```command window
akka projects new <project name> "<project description>" --region=<region> --organization=<org>
```
+

For example:
+
```command window
akka projects new my-akka-project "My Akka Project" --region=gcp-us-east1 --organization=my-organization
```
+

Example output:
+
```
NAME              DESCRIPTION   ID                                     OWNER                                       REGION
my-akka-project   "My ..        xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx   id:"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"   gcp-us-east1

'my-akka-project' is now the currently active project.
```
* **UI**

  1. Log in to [Akka Console, window="new"](https://console.akka.io)
  2. Navigate to the [Projects, window="new"](https://console.akka.dev/projects) section.
  3. Click **Create a project** and fill in the required fields, including name, description, region, and organization.

     ![alt="Create a project"](console-create-project.png)
  4. Review and click **Create Project** to finalize your project.

The new project will show as a card in the **Project** section.

You may now continue and [deploy a Service](services/deploy-service.adoc) in the new Project.

## See also

* [services/deploy-service.adoc](services/deploy-service.adoc)
* [`akka projects new` commands](reference:cli/akka-cli/akka_projects_new.adoc#_see_also)
* [`akka projects get` commands](reference:cli/akka-cli/akka_projects_get.adoc#_see_also)
