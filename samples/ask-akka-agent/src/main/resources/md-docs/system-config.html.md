<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [CLI](index.html)
- [System Configuration](system-config.html)

<!-- </nav> -->

# System Configuration

The Akka CLI supports system-level configuration that allows administrators to customize CLI behavior across all users on a machine. This is useful for enterprise deployments where you want to control default settings, specify internal mirrors for templates, or disable certain features.

## <a href="about:blank#_configuration_options"></a> Configuration Options

The following settings can be configured at the system level:

| Setting | Description | Default |
| --- | --- | --- |
| `code-templates-url` | URL of project templates used by `akka code init` | `https://doc.akka.io/_attachments/akka-code-init.json` |
| `context-url` | URL of documentation context used by AI coding assistants | `https://doc.akka.io/java/_attachments/akka-docs-md.zip` |
| `context-subdir` | Subdirectory name where context files are stored | `akka-context` |
To disable a feature, set its value to `none`. For example, setting `context-url` to `none` disables automatic context downloading.

## <a href="about:blank#_configuration_file_locations"></a> Configuration File Locations

The CLI looks for system configuration in platform-specific locations:

Linux
```none
/etc/akka/config.yml
```
macOS The CLI checks for configuration in this order:

1. MDM-managed plist (preferred for enterprise deployments):

```none
/Library/Managed Preferences/io.akka.cli.plist
```
2. YAML configuration file:

```none
/Library/Application Support/akka/config.yml
```
If an MDM plist is present, the YAML file is ignored.

Windows
```none
%ProgramData%\akka\config.yml
```
Typically this resolves to `C:\ProgramData\akka\config.yml`.

## <a href="about:blank#_yaml_configuration_format"></a> YAML Configuration Format

Create a YAML file at the appropriate location for your platform:

```yaml
code-templates-url: https://internal.example.com/akka-templates.json
context-url: https://internal.example.com/akka-context.zip
context-subdir: akka-context
```
To disable context downloading:

```yaml
context-url: none
```
Any settings not specified in the configuration file will use their default values.

## <a href="about:blank#_macos_mdm_configuration"></a> macOS MDM Configuration

For macOS devices managed via Mobile Device Management (MDM), you can deploy configuration using a property list (plist) file. This is the recommended approach for enterprise macOS deployments.

Create a configuration profile with the bundle identifier `io.akka.cli` containing the following keys:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>code-templates-url</key>
    <string>https://internal.example.com/akka-templates.json</string>
    <key>context-url</key>
    <string>https://internal.example.com/akka-context.zip</string>
    <key>context-subdir</key>
    <string>akka-context</string>
</dict>
</plist>
```
Deploy this as a managed preference using your MDM solution.

## <a href="about:blank#_precedence"></a> Precedence

Configuration is resolved in the following order (first match wins):

1. macOS MDM plist (macOS only)
2. System YAML configuration file
3. Built-in defaults

## <a href="about:blank#_troubleshooting"></a> Troubleshooting

If the YAML configuration file exists but contains invalid syntax, the CLI will print a warning to standard error and fall back to default values:

```none
Warning: failed to parse /etc/akka/config.yml: yaml: unmarshal errors: ...
```
Verify your YAML syntax is correct if you see this warning.

## <a href="about:blank#_related_documentation"></a> Related documentation

- [Install the Akka CLI](installation.html)
- [Using the Akka CLI](using-cli.html)

<!-- <footer> -->
<!-- <nav> -->
[Enable CLI command completion](command-completion.html) [Operator best practices](../operator-best-practices.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->