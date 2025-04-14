# Enable CLI command completion

Completion allows you to hit [TAB] on a partially entered `akka` command and have the shell complete the command, subcommand or flag for you.

* **bash**\
To load completion in the current bash shell run:

  ```bash
  source <(akka completion)
  ```

  Configure bash to load `akka` completions for each session by adding the following line to your `~/.bashrc` or `~/.profile` file:

  ```bash
  # add to ~/.bashrc or ~/.profile
  source <(akka completion)
  ```

  <dl><dt><strong>💡 TIP</strong></dt><dd>

  Using bash completions with `akka` requires you have bash completions enabled to begin with.
  Enable it in your `~/.bashrc` or `~/.profile` file with the following lines:

  ```bash
  if [ -f /etc/bash_completion ]; then
    source /etc/bash_completion
  fi
  ```
  For definitive details on setting up your shell with auto-completion, see the shell documentation.
  </dd></dl>
* **zsh (e.g. macOS)**\
To set up `zsh` shell completion run:

  ```zsh
  akka completion zsh > "${fpath[1]}/_akka"
  compinit
  ```

  <dl><dt><strong>📌 NOTE</strong></dt><dd>

  If shell completion is not already enabled in your environment execute the following:

  ```zsh
  echo "autoload -U compinit; compinit" >> ~/.zshrc
  ```
  </dd></dl>
* **fish**\
To set up fish shell completion run:

  ```fish
  akka completion fish > ~/.config/fish/completions/akka.fish
  source ~/.config/fish/completions/akka.fish
  ```
* **PowerShell**\
To set up shell completion for PowerShell run:

  ```powershell
  akka completion powershell | Out-String | Invoke-Expression
  ```

## Related documentation

* [reference:cli/akka-cli/index.adoc](reference:cli/akka-cli/index.adoc)
