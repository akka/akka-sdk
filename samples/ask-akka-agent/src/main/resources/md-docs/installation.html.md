<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [CLI](index.html)
- [Install the Akka CLI](installation.html)

<!-- </nav> -->

# Install the Akka CLI

The Akka CLI, `akka` enables you to interact with Akka projects. To install it, follow these steps:

Linux Download and install the latest version of `akka`:

```bash
curl -sL https://doc.akka.io/install-cli.sh | bash
```
If that fails due to permission issues, use:

```bash
curl -sL https://doc.akka.io/install-cli.sh | bash -s -- --prefix /tmp && \
    sudo mv /tmp/akka /usr/local/bin/akka
```
You can pass options to the installer script with `-s --` e.g.:

```bash
curl -sL https://doc.akka.io/install-cli.sh | bash -s -- --prefix=$HOME --version=3.0.22 --verbose
curl -sL https://doc.akka.io/install-cli.sh | bash -s -- -P $HOME -v 3.0.22 -V
```
For manual installation, download [akka_linux_amd64_3.0.22.tar.gz](https://downloads.akka.io/3.0.22/akka_linux_amd64_3.0.22.tar.gz), extract the `akka` executable and make it available on your PATH.

macOS **Recommended approach**

The recommended approach to install `akka` on macOS, is using [brew](https://brew.sh/)

```bash
brew install akka/brew/akka
```
If the `akka` CLI is already installed, and you want to upgrade `akka` to the latest version, you can run

```bash
brew update
brew upgrade akka
```
**Alternative approach**

curl -sL https://doc.akka.io/install-cli.sh | bash You can pass options to the installer script with `-s --` e.g.:

```bash
curl -sL https://doc.akka.io/install-cli.sh | bash -s -- --prefix=$HOME --version=3.0.22 --verbose
curl -sL https://doc.akka.io/install-cli.sh | bash -s -- -P $HOME -v 3.0.22 -V
```
Windows
1. Download the latest version of `akka` from [https://downloads.akka.io/latest/akka_windows_amd64.zip](https://downloads.akka.io/latest/akka_windows_amd64.zip)
2. Optionally, you can verify the integrity of the downloaded files using the [SHA256 checksums](https://downloads.akka.io/latest/checksums.txt).
3. Extract the zip file and move `akka.exe` to a location on your `%PATH%`.
Verify that the Akka CLI has been installed successfully by running the following to list all available commands:

```command
akka help
```

## <a href="about:blank#_related_documentation"></a> Related documentation

- [Using the Akka CLI](using-cli.html)
- [Enable CLI command completion](command-completion.html)
- [CLI command reference](../../reference/cli/akka-cli/index.html)

<!-- <footer> -->
<!-- <nav> -->
[CLI](index.html) [Using the Akka CLI](using-cli.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->