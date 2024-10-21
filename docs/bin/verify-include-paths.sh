#!/bin/bash

# Run this script from the root of the project to verify that the paths in the include::example[] lines in .adoc files
# match the {sample-base-url}/ paths in the same file. Since we have to input the links manually
# in the .adoc files, this script helps us to ensure that the paths are always correct.


echo "Starting path verification for .adoc files..."

# Array to store error messages
errors=()

# Variables to track line states
expect_dash_line=false
expect_sample_link=false
expect_include_line=false
in_source_block=false
previous_source_link=""
previous_display_text=""
previous_line=""

# Find all .adoc files
for file in $(find . -name "*.adoc"); do
  echo "Processing file: $file"

  # Check if file is readable and not empty
  if [[ ! -s "$file" ]]; then
    echo "ERROR: File $file is either empty or unreadable."
    continue  # Skip this file and move on to the next
  fi

  line_number=0

  while IFS= read -r line; do
    ((line_number++))  # Increment line number

    # Check if we are entering a source block
    if [[ "$line" == "[source,"* ]]; then
      in_source_block=true
      continue
    fi

    # Only validate if we are inside a source block
    if $in_source_block; then
      # State 0: Check for {sample-base-url} line with a / after it
      if [[ "$line" =~ \{sample-base-url\}/(.*)\[(.*)\] ]]; then
        previous_source_link="${BASH_REMATCH[1]}"
        previous_display_text="${BASH_REMATCH[2]}"
        echo "    Found source link path: $previous_source_link"
        echo "    Found display text: $previous_display_text"

        # Expect the next line to be "----"
        expect_dash_line=true
        continue
      fi

      # State 1: Expect the "----" line
      if $expect_dash_line; then
        if [[ "$line" != "----" ]]; then
          echo "    ERROR: Expected '----' after the source link in file $file at line $line_number"
          errors+=("Expected '----' after source link in file $file at line $line_number")
        else
          echo "    Found '----' line"
          # Expect the next line to be the include::example line
          expect_include_line=true
        fi
        expect_dash_line=false
        continue
      fi

      # State 2: Expect the include::example line
      if $expect_include_line; then
        if [[ "$line" =~ include::(example|java:example)\$(.*)\[.*\] ]]; then
          include_path="${BASH_REMATCH[2]}"
          echo "    Found include::example path: $include_path"

          # Extract the file name from the include::example path
          include_filename=$(basename "$include_path")

          # Check if the previous source link and include path match
          if [[ "$previous_source_link" != "$include_path" ]]; then
            echo "    ERROR: Mismatch in file $file at line $line_number"
            errors+=("Mismatch in file $file at line $line_number: Include path '$include_path' does not match source link '$previous_source_link'")
          fi

          # Check if the display text matches the file name in the include::example path
          if [[ "$previous_display_text" != "$include_filename" ]]; then
            echo "    ERROR: Display text does not match file name in file $file at line $line_number"
            errors+=("Display text mismatch in file $file at line $line_number: Display text '$previous_display_text' does not match file name '$include_filename'")
          fi

        else
          echo "    ERROR: Expected include::example line but got something else in file $file at line $line_number"
          errors+=("Expected include::example line in file $file at line $line_number")
        fi

        # Reset the state for the next block
        expect_include_line=false
        in_source_block=false
        previous_source_link=""
        previous_display_text=""
        continue
      fi
    fi

  done < "$file"

done

# If there are errors, print them and fail the script
if [ ${#errors[@]} -gt 0 ]; then
  echo -e "\n### SUMMARY: Errors found (${#errors[@]}) during path verification:"
  for error in "${errors[@]}"; do
    echo "$error"
  done
  exit 1
else
  echo "All paths matched successfully!"
fi
