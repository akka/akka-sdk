Akka
====

Akka is the *Akka Agentic AI Platform*, a platform for building and operating agentic systems with resilience and scalability. It has four solutions that share one definition of an AI system, one runtime, one ledger, and one governance model:

* [**Akka SDK**](https://doc.akka.io/sdk/index.html): the component model for building agentic systems.
* [**Akka Specify**](https://doc.akka.io/getting-started/spec-your-first-agent.html): spec-driven development tooling that builds systems from plain-language specifications and checks changes against them.
* [**Akka Verify**](https://doc.akka.io/): runtime-native evaluation, enforcement, evidence, and governance for agents built on Akka or elsewhere.
* [**Akka Optimize**](https://doc.akka.io/optimize/index.html): routes traffic to open-weight models and trains smaller models on your data.

Everything built with the platform runs on the Akka runtime. You can operate it yourself, or use Akka Automated Operations to run it on Akka's serverless cloud or in your own cloud account. Services use the same code in every option. See [How Akka runs](https://doc.akka.io/operations/how-akka-runs.html).

Akka, formerly Lightbend, has run distributed systems in production since 2007. The open-source Akka actor library for the JVM is the lineage of the platform. The platform is a separate product; this repository contains the Akka SDK.

Akka SDK
========

The Akka SDK is the component model for building agentic systems. Services built with the SDK are automatically clustered and can be deployed on any infrastructure.

Reference Documentation
-----------------------

The reference documentation for the Akka SDK is available via [doc.akka.io](https://doc.akka.io/).

License
-------

The Akka SDK is licensed under the Business Source License 1.1, please see the [Akka License FAQ](https://akka.io/bsl-license-faq).

Tests and documentation are under a separate license, see the LICENSE file in each documentation and test root directory for details.
