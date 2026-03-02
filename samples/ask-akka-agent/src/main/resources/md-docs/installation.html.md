<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [CLI](index.html)
- [Install the Akka CLI](installation.html)

<!-- </nav> -->

# Install the Akka CLI

The Akka CLI, `akka` enables you to interact with Akka projects. To install it, follow these steps:

Linux **Recommended approach**

The recommended approach to install the `akka` CLI on Linux is using the Debian package repository:

```bash
curl -1sLf \
  'https://downloads.akka.io/setup.deb.sh' \
  | sudo -E bash
sudo apt install akka
```
If the `akka` CLI is already installed, and you want to upgrade to the latest version, you can run:

```bash
sudo apt update
sudo apt install --only-upgrade akka
```
**Alternative approach**

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
curl -sL https://doc.akka.io/install-cli.sh | bash -s -- --prefix=$HOME --version=3.0.47 --verbose
curl -sL https://doc.akka.io/install-cli.sh | bash -s -- -P $HOME -v 3.0.47 -V
```
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
curl -sL https://doc.akka.io/install-cli.sh | bash -s -- --prefix=$HOME --version=3.0.47 --verbose
curl -sL https://doc.akka.io/install-cli.sh | bash -s -- -P $HOME -v 3.0.47 -V
```
Windows **Recommended approach**

The recommended approach to install the `akka` CLI on Windows is using [winget](https://learn.microsoft.com/en-us/windows/package-manager/winget/):

```powershell
winget install Akka.Cli
```
If the `akka` CLI is already installed, and you want to upgrade to the latest version, you can run:

```powershell
winget upgrade Akka.Cli
```
**Alternative approach**

1. Download the latest version of `akka` from [https://downloads.akka.io/latest/akka_windows_amd64.zip](https://downloads.akka.io/latest/akka_windows_amd64.zip)
2. Optionally, you can verify the integrity of the downloaded files using the [SHA256 checksums](https://downloads.akka.io/latest/checksums.txt).
3. Extract the zip file and move `akka.exe` to a location on your `%PATH%`.

|  | By downloading and using this software you agree to Akka’s [Privacy Policy](https://akka.io/legal/privacy) and [Software Terms of Use](https://trust.akka.io/cloud-terms-of-service). |
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