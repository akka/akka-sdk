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

* View entity event logs and values directly
* Invoke methods on services, even if not exposed to the internet or protected by ACLs
* Manage projections

**📌 NOTE**\
Organization membership is managed separately, see [organizations/manage-users.adoc](organizations/manage-users.adoc).

## Listing role bindings

To list the role bindings in a project, use the following command:
```command window
akka roles list-bindings
```

Example output:
```
ROLE BINDING ID                        ROLE        PRINCIPAL                                MFA
f3e1ad17-d7be-4432-9ab6-edd475c3aa44   admin       John Smith <john.smith@example.com>      true
311e3752-30f9-43f4-99ef-6cbb4c5f14f3   developer   Jane Citizen <jane.citizen@example.com>  true
```

<dl><dt><strong>📌 NOTE</strong></dt><dd>

The Akka CLI can keep a project as context, so you do not need to pass the `--project` flag.

```command window
akka config set project <project name>
```
</dd></dl>

## Granting a role

You can grant a project role to a user in two ways:

### 1. Invite a user to the project by e-mail
Invite a user to join the project and assign them a role by using the following command:

```command window
akka roles invitations invite-user <email address> --role <role>
```

The user will receive an email inviting them to join the project. Upon acceptance, the role binding will be created.

### 2. Add a role directly
If the user is already a member of the project, or the project is part of an organization and the user belongs to that organization, you can assign roles directly without sending an invitation.

* By e-mail:

  ```command window
  akka roles add-binding --email <email address> --role <role>
  ```
* By username:

  ```command window
  akka roles add-binding --username <username> --role <role>
  ```

## Deleting a project role bindings

To delete a role binding, first list the role bindings to obtain the **role binding ID**.

```command window
akka roles list-bindings
```

Example output:
```
ROLE BINDING ID                        ROLE        PRINCIPAL                                MFA
f3e1ad17-d7be-4432-9ab6-edd475c3aa44   admin       John Smith <john.smith@example.com>      true
311e3752-30f9-43f4-99ef-6cbb4c5f14f3   developer   Jane Citizen <jane.citizen@example.com>  true
```

Pass the **role binding ID** to the following command:

```command window
akka roles delete-binding <role binding id>
```

## Managing invitations

To view outstanding invitations, use the following command:

```command window
akka roles invitations list
```

Example output:
```
EMAIL                      ROLE
jane.citizen@example.com   admin
```

Invitations will automatically expire after 7 days. You can manually delete an invitation with the following command:

```command window
akka roles invitations delete <email address>
```

To resend an invitation, first delete the expired invitation and then issue a new one.

## See also

* [organizations/manage-users.adoc](organizations/manage-users.adoc)
* [`akka roles` commands](reference:cli/akka-cli/akka_roles.adoc#_see_also)
