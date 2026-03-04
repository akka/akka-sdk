<!-- <nav> -->
- [Akka](../index.html)
- [Install the Akka CLI](quick-install-cli.html)

<!-- </nav> -->

# Install the Akka CLI

|  | In case there is any trouble with installing the CLI when following these instructions, please check the [detailed CLI installation instructions](../operations/cli/installation.html). |
Linux Install the `akka` CLI using the Debian package repository:

```bash
curl -1sLf \
  'https://downloads.akka.io/setup.deb.sh' \
  | sudo -E bash
sudo apt install akka
```
macOS The recommended approach to install `akka` on macOS, is using [brew](https://brew.sh/)

```bash
brew install akka/brew/akka
```
Windows Install the `akka` CLI using [winget](https://learn.microsoft.com/en-us/windows/package-manager/winget/):

```powershell
winget install Akka.Cli
```

|  | By downloading and using this software you agree to Akka’s [Privacy Policy](https://akka.io/legal/privacy) and [Software Terms of Use](https://trust.akka.io/cloud-terms-of-service). |
Verify that the Akka CLI has been installed successfully by running the following to list all available commands:

```command
akka help
```

<!-- <footer> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->