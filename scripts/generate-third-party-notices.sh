#!/bin/bash

# Minimum expected lines in THIRD_PARTY_NOTICES.txt
# Current file has ~56,000 lines; set floor well below normal to catch FOSSA failures
# while allowing for legitimate reductions in dependencies.
MIN_LINES=10000

retry() {
  command=$1
  num_retries=$2

  for i in $(seq 1 "$num_retries"); do
    echo "Attempt $i: $command"
    eval $command && return
    sleep 5

    if [ "$i" -eq "$num_retries" ]; then
      echo "Failed after $num_retries attempts: $command"
      return 1
    fi
  done
}

validate_output() {
  local file="THIRD_PARTY_NOTICES.txt"

  # Check file exists
  if [ ! -f "$file" ]; then
    echo "ERROR: $file was not created"
    return 1
  fi

  # Check minimum line count
  local line_count
  line_count=$(wc -l < "$file" | tr -d ' ')

  if [ "$line_count" -lt "$MIN_LINES" ]; then
    echo "ERROR: Generated file has only $line_count lines (minimum: $MIN_LINES)"
    echo "FOSSA report likely failed - backend may not have finished processing."
    echo "This can happen when FOSSA backend is overloaded or has issues."
    echo "Check fossa.debug.json or fossa.debug.zip for detailed logs."
    rm -f "$file"  # Remove bad file to prevent PR creation
    return 1
  fi

  # Verify expected content structure
  if ! grep -q "Third-Party Software" "$file"; then
    echo "ERROR: Generated file missing expected 'Third-Party Software' header"
    rm -f "$file"
    return 1
  fi

  echo "SUCCESS: Generated $file with $line_count lines"
  return 0
}

main() {
  # Verify that fossa CLI is available (should be natively installed in CI agent)
  command -v fossa || { echo "fossa CLI not found in PATH"; exit 1; }

  fossa --version

  # Log Java environment (FOSSA/Gradle requires Java 17+)
  # Java should be configured via `sem-version java 21` in CI
  echo "JAVA_HOME: ${JAVA_HOME:-<not set>}"
  java -version

  # Full access token created using rsanjay@confluent.io's FOSSA account (on Jul 17, 2024).
  # Rotate every 80-180 days.
  # https://docs.fossa.com/docs/rotating-fossa-api-key#full-access-token
  export FOSSA_API_KEY=$(vault kv get -field api_key v1/ci/kv/fossa_full_access)

  # Use --debug to capture detailed logs for troubleshooting
  # Note: v3.13.0+ outputs to fossa.debug.zip, older versions to fossa.debug.json
  fossa analyze --debug --exclude-path build --only-target gradle

  # Retry on command failure OR invalid output (FOSSA can return empty with exit 0)
  retry "fossa report attribution --debug --format text > THIRD_PARTY_NOTICES.txt && validate_output" 3 || exit 1
}

main
