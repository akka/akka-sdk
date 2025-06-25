<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Organizations](index.html)
- [Billing](billing.html)

<!-- </nav> -->

# Billing

Akka provides detailed billing information to help organizations monitor and manage the operational costs associated with their projects. Users with the **billing-admin** role can view:

- **Cost breakdown** for each project in the organization.
- **Month-to-date aggregate cost**.
- **Cost forecast** based on current and projected usage across all projects.
Billing data is accessible only to users with the **billing-admin** role. For more information on assigning this role, see the [Assigning the billing-admin Role](about:blank#assigning_billing_admin) section below.

## <a href="about:blank#_billing_interface"></a> Billing Interface

For **billing-admin** users, a billing icon appears in the [Akka Console’s](https://console.akka.io/) side navigation. Clicking this icon opens the billing interface, where users can select the billing month and organization to view detailed billing data.

If a user is a billing admin for multiple organizations, they can switch between organizations in the billing UI.

![Akka Billing User Interface](../_images/billing-ui.jpg)
In the billing UI:

- **Month-to-date costs** and a **cost forecast** for the current month are displayed in the upper-right corner.
- Billing data for each project within the selected organization is broken down into the following categories:

  - **Network Data Transfer**: Charges for data transfer across all services, measured in GB.
  - **Data Operations**: Total read and write operations for all services.
  - **Data Persistence**: Total amount of data persisted during the month, measured in GB-Hours.
These are all metered at the project, region, service scope and you can see the totals across organization, project, region, or service as you choose.

For more details on pricing, refer to [Akka Pricing](https://akka.io/pricing#).

## <a href="about:blank#assigning_billing_admin"></a> Assigning the billing-admin Role

The organization superuser can assign the billing-admin role in one of two ways:

1. **Invite a User**: Use the following command to invite a user to the organization and assign the billing-admin role:

```command
akka organizations invitations create --organization <organization name> \
--email <email address> --role billing-admin
```
2. **Assign an Existing User**: If the user is already a member, the superuser can assign the billing-admin role directly:

```command
akka organization users add-binding --organization <organization name> \
  --email <email address> --role billing-admin
```
For more details on managing users and their roles, see the [Managing organization users](manage-users.html) section.

## <a href="about:blank#_see_also"></a> See also

- [Managing organization users](manage-users.html)
- [Akka Pricing](https://akka.io/pricing#)

<!-- <footer> -->
<!-- <nav> -->
[Regions](regions.html) [Projects](../projects/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->