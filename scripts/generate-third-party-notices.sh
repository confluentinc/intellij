#!/bin/bash

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

main() {
  # Verify that fossa CLI is available (should be natively installed in CI agent)
  command -v fossa || { echo "fossa CLI not found in PATH"; exit 1; }

  fossa --version
  java -version

  # Full access token created using rsanjay@confluent.io's FOSSA account (on Jul 17, 2024).
  # Rotate every 80-180 days.
  # https://docs.fossa.com/docs/rotating-fossa-api-key#full-access-token
  export FOSSA_API_KEY=$(vault kv get -field api_key v1/ci/kv/fossa_full_access)

  # Use --debug to capture detailed logs for troubleshooting
  # Note: v3.13.0+ outputs to fossa.debug.zip, older versions to fossa.debug.json
  fossa analyze --debug --exclude-path build --only-target gradle

  # Retry on command failure OR invalid output (FOSSA can return empty with exit 0)
  retry "fossa report attribution --debug --format text > THIRD_PARTY_NOTICES.txt" 3 || exit 1
}

main
