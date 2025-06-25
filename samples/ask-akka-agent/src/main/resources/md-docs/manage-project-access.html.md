<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Projects](index.html)
- [Manage users](manage-project-access.html)

<!-- </nav> -->

# Managing project users

Access to projects is controlled by assigning specific roles to users. The available roles are: **admin**, **developer**, **viewer** and **backoffice**.

| Permission: | admin | developer | viewer | backoffice |
| --- | --- | --- | --- | --- |
| View project | ✅ | ✅ | ✅ | ✅ |
| Admin project | ✅ | ❌ | ❌ | ❌ |
| View services | ✅ | ✅ | ✅ | ❌ |
| Deploy services | ✅ | ✅ | ❌ | ❌ |
| Update services | ✅ | ✅ | ❌ | ❌ |
| Delete services | ✅ | ✅ | ❌ | ❌ |
| View routes | ✅ | ✅ | ✅ | ❌ |
| Manage routes | ✅ | ✅ | ❌ | ❌ |
| View secrets | ✅ | ✅ | ✅ | ❌ |
| Manage secrets | ✅ | ✅ | ❌ | ❌ |
| Backoffice functions | ✅ | ❌ | ❌ | ✅ |
**Backoffice functions** include the ability to:

- View entity event logs and values directly
- Invoke methods on services, even if not exposed to the internet or protected by ACLs
- Manage projections

|  | Organization membership is managed separately, see [Managing organization users](../organizations/manage-users.html). |

## <a href="about:blank#_listing_role_bindings"></a> Listing role bindings

To list the role bindings in a project, use the following command:

```command
akka roles list-bindings
```
Example output:

```none
ROLE BINDING ID                        ROLE        PRINCIPAL                                MFA
f3e1ad17-d7be-4432-9ab6-edd475c3aa44   admin       John Smith <john.smith@example.com>      true
311e3752-30f9-43f4-99ef-6cbb4c5f14f3   developer   Jane Citizen <jane.citizen@example.com>  true
```

|  | The Akka CLI can keep a project as context, so you do not need to pass the `--project` flag.

```command
akka config set project <project name>
``` |

## <a href="about:blank#_granting_a_role"></a> Granting a role

You can grant a project role to a user in two ways:

### <a href="about:blank#_1_invite_a_user_to_the_project_by_e_mail"></a> 1. Invite a user to the project by e-mail

Invite a user to join the project and assign them a role by using the following command:

```command
akka roles invitations invite-user <email address> --role <role>
```
The user will receive an email inviting them to join the project. Upon acceptance, the role binding will be created.

### <a href="about:blank#_2_add_a_role_directly"></a> 2. Add a role directly

If the user is already a member of the project, or the project is part of an organization and the user belongs to that organization, you can assign roles directly without sending an invitation.

- By e-mail:

```command
akka roles add-binding --email <email address> --role <role>
```
- By username:

```command
akka roles add-binding --username <username> --role <role>
```

## <a href="about:blank#_deleting_a_project_role_bindings"></a> Deleting a project role bindings

To delete a role binding, first list the role bindings to obtain the **role binding ID**.

```command
akka roles list-bindings
```
Example output:

```none
ROLE BINDING ID                        ROLE        PRINCIPAL                                MFA
f3e1ad17-d7be-4432-9ab6-edd475c3aa44   admin       John Smith <john.smith@example.com>      true
311e3752-30f9-43f4-99ef-6cbb4c5f14f3   developer   Jane Citizen <jane.citizen@example.com>  true
```
Pass the **role binding ID** to the following command:

```command
akka roles delete-binding <role binding id>
```

## <a href="about:blank#_managing_invitations"></a> Managing invitations

To view outstanding invitations, use the following command:

```command
akka roles invitations list
```
Example output:

```none
EMAIL                      ROLE
jane.citizen@example.com   admin
```
Invitations will automatically expire after 7 days. You can manually delete an invitation with the following command:

```command
akka roles invitations delete <email address>
```
To resend an invitation, first delete the expired invitation and then issue a new one.

## <a href="about:blank#_see_also"></a> See also

- [Managing organization users](../organizations/manage-users.html)
- <a href="../../reference/cli/akka-cli/akka_roles.html#_see_also">`akka roles` commands</a>

<!-- <footer> -->
<!-- <nav> -->
[Create](create-project.html) [Configure a container registry](container-registries.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->