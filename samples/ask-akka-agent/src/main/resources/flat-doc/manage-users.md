# Managing organization users

Access to an organization is controlled by assigning roles to users. The available roles are: **superuser**, **project-admin**, **billing-admin**, and **member**.

| Permission | superuser | project-admin | billing-admin | member |
| --- | --- | --- | --- | --- |
| View organization users | ✅ | ✅ | ✅ | ✅     |
| Manage organization users | ✅ | ❌ | ❌ | ❌    |
| Create projects | ✅ | ✅ | ❌ | ❌ |
| View projects | ✅ | ❌ | ❌ | ❌ |
| Manage project users | ✅ | ❌ | ❌ | ❌ |
| Delete projects | ✅ | ❌ | ❌ | ❌ |
| All other project/service operations | ❌ | ❌ | ❌ | ❌ |
| View organization billing data | ❌ | ❌ | ✅ | ❌  |

**📌 NOTE**\
Project-level operations are accessed via project-specific roles. A superuser has a subset of project permissions, including the ability to assign roles (including to themselves). When a user creates a project, they are automatically granted admin access to it. (see [granting project roles](operations:projects/manage-project-access.adoc))

The **member** role allows project admins to add users to their projects without needing to invite them to the organization.

## Listing role bindings

You can list role bindings within an organization using the following command:

```command window
akka organization users list-bindings --organization <organization name>
```

Example output:
```
ROLE BINDING ID                        ROLE        USERNAME       EMAIL                      NAME
fd21044c-b973-4220-8f65-0f7d317bb23b   superuser   jane.citizen   jane.citizen@example.com   Jane Citizen
120b75b6-6b53-4ebb-b23b-2272be974966   member      john.smith     john.smith@example.com     John Smith
```

## Granting a role

You can grant a role to a user in two ways:

### 1. Invite a User by Email
Send an email invitation with the following command:

```command window
akka organizations invitations create --organization <organization name> \
  --email <email address> --role <role>
```

The user will receive an email to join the organization. Once accepted, the role binding will be created.

### 2. Add a Role Directly

If the user is already a member, you can assign roles directly:

* By e-mail:
```command window
akka organization users add-binding --organization <organization name> \
  --email <email address> --role <role>
```
* By username:
```command window
akka organizations users add-binding --organization <organization name> \
  --username <username> --role <role>
```

## Deleting a role binding

To delete a role binding, first list the users to get the role binding ID. Then, use the following command:

```command window
akka organizations users delete-binding --organization <organization name> \
  --id <role binding id>
```

## Managing invitations

View outstanding invitations:

```command window
akka organizations invitations list --organization <organization name>
```

Example output:
```
EMAIL                      ROLE
jane.citizen@example.com   member
```

Invitations expire after 7 days, but you can cancel them manually:

```command window
akka organizations invitations cancel --organization <organization name> \
  --email <email address>
```

To resend an invitation, cancel the previous one and reissue the invite.

## See also

* [projects/manage-project-access.adoc](projects/manage-project-access.adoc)
* [`akka organizations users` commands](reference:cli/akka-cli/akka_organizations_users.adoc#_see_also)
* [`akka organizations invitations` commands](reference:cli/akka-cli/akka_organizations_invitations.adoc#_see_also)
