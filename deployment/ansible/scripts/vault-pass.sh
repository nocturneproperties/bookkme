#!/bin/sh

if [ -n "$ANSIBLE_VAULT_PASSWORD" ]; then
  printf '%s\n' "$ANSIBLE_VAULT_PASSWORD"
  exit 0
fi

if command -v security >/dev/null 2>&1 && security find-generic-password -s bookk-ansible-vault -w 2>/dev/null; then
  exit 0
fi

if [ -f "$HOME/.bookk-vault-pass" ]; then
  cat "$HOME/.bookk-vault-pass"
  exit 0
fi

echo "No vault password: set ANSIBLE_VAULT_PASSWORD, add a 'bookk-ansible-vault' keychain item, or create ~/.bookk-vault-pass" >&2
exit 1
