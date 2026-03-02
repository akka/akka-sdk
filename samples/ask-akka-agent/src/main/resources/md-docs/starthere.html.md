<!-- <nav> -->
- [Akka](../index.html)
- [Getting started](starthere.html)

<!-- </nav> -->

# Getting started

You might be excited to start your journey learning and building with Akka, but there are a few things you need to get set up first. This will only take a minute.

## <a href="about:blank#_prerequisites"></a> Prerequisites

- Java 21, we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/)
- [Apache Maven](https://maven.apache.org/install.html) version 3.9 or later
- <a href="https://curl.se/download.html">`curl` command-line tool</a>
- [Akka CLI](quick-install-cli.html)
- Git

## <a href="about:blank#_creating_an_empty_project"></a> Creating an empty project

Before you start exploring all of the samples, make sure that your environment and your local tool chain all work properly.

To verify this, first create a new, *empty* project by typing `akka code init` in a terminal window and selecting the empty project template. If this is the first time you’ve run `akka code init`, then it will ask you to go through authenticating against your free *support account* in a browser (discussed in the next section). You will then choose whether you want the token for the private repository to be in your Maven `settings.xml` file or if you want to add it manually to the project’s `pom.xml`.

For the purposes of this test to make sure everything is working, select the option to have the CLI automatically put the repository URL in `settings.xml`.

Next, `cd` into the directory that was just created and type `mvn compile`. If you did not configure the secure repository token properly, then you may get a compile error with authorization failure/access denied messages. If you get Java compilation errors, check to make sure that you have the JDK installed and configured properly.

If you were able to create and build an empty project, then you’re all set and you can continue on to the samples.

## <a href="about:blank#_generating_a_secure_repository_token"></a> Generating a secure repository token

When you go to the [repository token](https://account.akka.io/token) page, you will be presented with two options:

- **Register** - create a new free support account.
- **Current customer** - select this if you have already created a free support account.
Remember that this account is an *Akka **support** account* and is not the same thing as a paid *Akka cloud* account. This account is specifically for interacting with support and downloading Akka dependency binaries from the secure Maven repository.

Once registered and authenticated, you will see that a secure token has been generated for you. This token will then be automatically detected and used whenever you use the Akka CLI to create new projects.

<!-- <footer> -->
<!-- <nav> -->
[Akka](../index.html) [Tutorials](index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->