# Prettier-Ignore Comment Removal

This directory contains a shell script to remove lines containing `prettier-ignore` comments from the documentation source files while leaving the original source code untouched.

## Overview

During development, `// prettier-ignore` comments are used to prevent Prettier from formatting specific code sections. However, these comments should not appear in the final documentation.

## Implementation

**File:** `docs/bin/remove-prettier-ignore.sh`

This is a shell script that removes any line containing `prettier-ignore` from files after they are copied to the `docs/src-managed` directory but before Antora processes them.

**Integration:** The script is automatically run as part of the `examples` target in the Makefile, which copies sample code to the managed documentation directory.

## How It Works

1. The `make examples` command copies sample code to `docs/src-managed`
2. The script runs automatically and finds all `.java` files
3. It removes any line containing `prettier-ignore` using `sed`
4. Antora then processes the cleaned files to generate the final documentation

## Integration

The script runs automatically when you build the documentation:

```bash
# Local development
make local

# Production build
make prod
```

The script runs during the `examples` phase and logs basic information about its execution.

## Testing

To verify the script is working:

1. Add some `// prettier-ignore` comments to Java files in the samples directory
2. Build the documentation: `make local`
3. Check the files in `docs/src-managed/modules/java/examples/` - lines with prettier-ignore should be removed
4. Check the generated HTML files in `target/site` - they should not contain prettier-ignore comments
5. Verify the original source files in `samples/` still contain the comments

## Manual Usage

You can also run the script manually:

```bash
# Process files in docs/src-managed
./docs/bin/remove-prettier-ignore.sh docs/src-managed

# Process a specific directory
./docs/bin/remove-prettier-ignore.sh path/to/directory
```

## Troubleshooting

If prettier-ignore comments still appear in the generated documentation:

1. Check that the script ran during the build (look for the log message)
2. Verify that the files in `docs/src-managed` have had the prettier-ignore lines removed
3. Make sure the `examples` target completed successfully before Antora runs
