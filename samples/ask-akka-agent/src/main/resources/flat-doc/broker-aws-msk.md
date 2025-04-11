# Using AWS MSK as Kafka service

Akka connects to [AWS MSK, window="new"](https://aws.amazon.com/msk/) clusters via TLS, authenticating using SASL (Simple Authentication and Security Layer) SCRAM.

Prerequisites not covered in detail by this guide:

1. The MSK instance must be provisioned, serverless MSK does not support SASL.
2. The MSK cluster must be set up with [TLS for client broker connections and SASL/SCRAM for authentication, window="new"](https://docs.aws.amazon.com/msk/latest/developerguide/msk-password.html)
with a user and password to use for authenticating your Akka service
   1. The user and password is stored in a secret
   2. The secret must be encrypted with a specific key, MSK cannot use the default MKS encryption key
3. The provisioned cluster must be set up for [public access, window="new"](https://docs.aws.amazon.com/msk/latest/developerguide/public-access.html)
   1. Creating relevant ACLs for the user to access the topics in your MSK cluster
   2. Disabling `allow.everyone.if.no.acl.found` in the MSK cluster config
4. Creating topics used by your Akka service

## Steps to connect to an AWS Kafka broker

Take the following steps to configure access to your AWS Kafka broker for your Akka project.

1. Ensure you are on the correct Akka project

   ```command window
   akka config get-project
   ```
2. Store the password for your user in an Akka secret:

   ```command window
   akka secret create generic aws-msk-secret --literal pwd=<sasl user password>
   ```
3. Get the bootstrap brokers for your cluster, they can be found by selecting the cluster and clicking "View client information."
There is a copy button at the top of "Public endpoint" that will copy a correctly formatted string with the bootstrap brokers. [See AWS docs for other ways to inspect the bootstrap brokers, window="new"](https://docs.aws.amazon.com/msk/latest/developerguide/msk-get-bootstrap-brokers.html).
4. Use `akka projects config` to set the broker details. Set the MSK SASL username you have prepared and the bootstrap servers.

   ```command window
   akka projects config set broker  \
     --broker-service kafka \
     --broker-auth scram-sha-512 \
     --broker-user <sasl username> \
     --broker-password-secret aws-msk-secret/pwd \
     --broker-bootstrap-servers <bootstrap brokers> \
   ```

The `broker-password-secret` refer to the name of the Akka secret created earlier rather than the actual password string.

An optional description can be added with the parameter `--description` to provide additional notes about the broker.

The broker config can be inspected using:
```command window
akka projects config get broker
```

### Custom key pair

If you are using a custom key pair for TLS connections to your MSK cluster, instead of the default AWS provided key pair, you will need to define a secret with the CA certificate:

```command window
akka secret create tls-ca kafka-ca-cert --cert ./ca.pem
```

And then pass the name of that secret for `--broker-ca-cert-secret` when setting the broker up:

```command window
akka projects config set broker  \
  --broker-service kafka \
  --broker-auth scram-sha-512 \
  --broker-user <sasl username> \
  --broker-password-secret aws-msk-secret/pwd \
  --broker-ca-cert-secret kafka-ca-cert
  --broker-bootstrap-servers <bootstrap brokers> \
```

## See also

* [`akka projects config` commands](reference:cli/akka-cli/akka_projects_config.adoc#_see_also)
