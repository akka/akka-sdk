# Vale documentation linter

## What is Vale?

Vale is an open-source, command-line linter for writing. It improves the quality and consistency of technical documentation through enforcement of style guides. It is configurable, allowing custom rules to be defined and integrated with existing style guides.

## Why use Vale?

- Helps maintain a consistent writing style.
- Catches common writing errors.
- Catches issues early, saving time in the review process.
- Allows enforcement of Akka-specific terminology.
- Integrates with CI/CD pipeline, ensuring consistent enforcement of standards.

## Configuration

- `docs/.vale.ini`: The main configuration file.
- `docs/styles`: Directory containing custom style rules and vocabularies.
- `docs/styles/Vocab/Akka/accept.txt`: Custom list of words acceptable for Akka documentation.

## Adding or modifying rules

- Edit the `docs/.vale.ini` file to change settings or add new styles.
- Modify files in the `docs/styles` directory to adjust specific rules.
- Update `docs/styles/Vocab/Akka/accept.txt` to add project-specific terms.

## CI/CD

A GitHub Action is used to run Vale in the CI/CD pipeline:

- Workflow defined in `.github/workflows/documentation.yml`.
- Workflow runs on pull requests that modify files in the `docs/` directory.
- `errata-ai/vale-action` used to execute Vale checks.
- Failures are reported directly in pull requests, with detailed errors for easy fixing.

## How to use locally (macOS)

1. Install Vale:

   ```bash
   brew install vale
   ```

2. Install Asciidoctor (required for processing .adoc files):

   ```bash
   gem install asciidoctor
   ```

3. Run Vale in the docs directory:

   ```bash
   (cd docs && vale src)
   ```

### Advanced local usage for writing improvement

When writing new sections of documentation, it may be useful to run Vale to see linting alerts at `warning` or `suggestion` level. These commands will provide feedback, helping you improve your writing before committing changes.

```bash
(cd docs && vale --minAlertLevel warning src)
```

or

```bash
(cd docs && vale --minAlertLevel suggestion src)
```

## Best practices

- Run Vale locally before committing changes to catch issues early.
- Review Vale suggestions carefully:not all may be applicable in every context.
- Propose updates to Vale configuration if you notice recurring false positives.

## Troubleshooting

- Ensure you have the latest version installed.
- Check that all dependencies (like Asciidoctor) are correctly installed.
- Verify that your working directory is correct when running Vale.
- Review the `.vale.ini` file for any recent changes that might affect behavior.

## Additional resources

- [Vale documentation](https://docs.errata.ai/vale/about)
- [Vale styles](https://github.com/errata-ai/styles)
- [Vale GitHub action](https://github.com/errata-ai/vale-action)
