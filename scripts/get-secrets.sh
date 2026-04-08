#!/bin/bash
# Retrieve application secrets from Vault
# Usage: . scripts/get-secrets.sh

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    set -euo pipefail
fi

export VAULT_ADDR="${VAULT_ADDR:-https://non-prod.vault.internal.confluent.cloud}"
VAULT_PATH="${VAULT_PATH:-stag/kv/semaphore/intellij/telemetry}"

# Check authentication (skip in CI, vault-setup already authenticated)
if [ "${CI:-false}" != "true" ]; then
    if ! VAULT_ADDR="$VAULT_ADDR" vault kv list "stag/kv/semaphore/intellij" &>/dev/null; then
        echo "Error: Not authenticated with Vault" >&2
        echo "Run: vault_login" >&2
        return 1 2>/dev/null || exit 1
    fi
fi

# Retrieve and export secrets
for secret in sentry_auth_token sentry_dsn segment_write_key; do
    if ! value=$(VAULT_ADDR="$VAULT_ADDR" vault kv get -field="$secret" "$VAULT_PATH" 2>&1); then
        echo "Error: Failed to retrieve $secret from Vault" >&2
        echo "Details: $value" >&2
        return 1 2>/dev/null || exit 1
    fi
    # Check if value is empty
    if [ -z "$value" ]; then
        echo "Error: $secret is empty from Vault" >&2
        return 1 2>/dev/null || exit 1
    fi
    # Convert to uppercase for environment variable name
    env_var=$(echo "$secret" | tr '[:lower:]' '[:upper:]')
    export "$env_var"="$value"
done

echo "[INFO] Application secrets retrieved from Vault"
