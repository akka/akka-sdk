<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Organizations](index.html)
- [Manage users](manage-users.html)

<!-- </nav> -->

# Managing organization users

Access to an organization is controlled by assigning roles to users. The available roles are: **superuser**, **project-admin**, **billing-admin**, and **member**.

| Permission | superuser | project-admin | billing-admin | member |
| --- | --- | --- | --- | --- |
| View organization users | ✅ | ✅ | ✅ | ✅ |
| Manage organization users | ✅ | ❌ | ❌ | ❌ |
| Create projects | ✅ | ✅ | ❌ | ❌ |
| Assign regions to projects | ✅ | ✅ | ❌ | ❌ |
| View all projects | ✅ | ❌ | ❌ | ❌ |
| Manage project users | ✅ | ❌ | ❌ | ❌ |
| Delete projects | ✅ | ❌ | ❌ | ❌ |
| All other project/service operations | ❌ | ❌ | ❌ | ❌ |
| View organization billing data | ❌ | ❌ | ✅ | ❌ |

|  | Project-level operations are accessed via project-specific roles. A superuser has a subset of project permissions, including the ability to assign roles (including to themselves). When a user creates a project, they are automatically granted admin access to it. (see [granting project roles](../projects/manage-project-access.html)) |
The **member** role allows project admins to add users to their projects without needing to invite them to the organization.

## <a href="about:blank#_listing_role_bindings"></a> Listing role bindings

You can list role bindings within an organization using the following command:

```command
akka organization users list-bindings --organization <organization name>
```
Example output:

```none
ROLE BINDING ID                        ROLE        USERNAME       EMAIL                      NAME
fd21044c-b973-4220-8f65-0f7d317bb23b   superuser   jane.citizen   jane.citizen@example.com   Jane Citizen
120b75b6-6b53-4ebb-b23b-2272be974966   member      john.smith     john.smith@example.com     John Smith
```

## <a href="about:blank#_granting_a_role"></a> Granting a role

You can grant a role to a user in two ways:

### <a href="about:blank#_1_invite_a_user_by_email"></a> 1. Invite a User by Email

Send an email invitation with the following command:

```command
akka organizations invitations create --organization <organization name> \
  --email <email address> --role <role>
```
The user will receive an email to join the organization. Once accepted, the role binding will be created.

### <a href="about:blank#_2_add_a_role_directly"></a> 2. Add a Role Directly

If the user is already a member, you can assign roles directly:

- By e-mail:
akka organization users add-binding --organization <organization name> \
  --email <email address> --role <role>
- By username:
akka organizations users add-binding --organization <organization name> \
  --username <username> --role <role>
## <a href="about:blank#_deleting_a_role_binding"></a> Deleting a role binding

To delete a role binding, first list the users to get the role binding ID. Then, use the following command:

```command
akka organizations users delete-binding --organization <organization name> \
  --id <role binding id>
```

## <a href="about:blank#_managing_invitations"></a> Managing invitations

View outstanding invitations:

```command
akka organizations invitations list --organization <organization name>
```
Example output:

```none
EMAIL                      ROLE
jane.citizen@example.com   member
```
Invitations expire after 7 days, but you can cancel them manually:

```command
akka organizations invitations cancel --organization <organization name> \
  --email <email address>
```
To resend an invitation, cancel the previous one and reissue the invite.

## <a href="about:blank#_see_also"></a> See also

- [Managing project users](../projects/manage-project-access.html)
- <a href="../../reference/cli/akka-cli/akka_organizations_users.html#_see_also">`akka organizations users` commands</a>
- <a href="../../reference/cli/akka-cli/akka_organizations_invitations.html#_see_also">`akka organizations invitations` commands</a>

<!-- <footer> -->
<!-- <nav> -->
[Organizations](index.html) [Regions](regions.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->