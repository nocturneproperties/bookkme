SHELL := /bin/bash
.SHELLFLAGS := -o pipefail -c
VAULT := inventory/group_vars/all/vault.yml
ANSIBLE_PYTHON := $(shell head -1 "$$(command -v ansible)" | sed 's/^\#!//')
TAGS_ARG := $(if $(tags),--tags $(tags))
BACKUPS := backups

.PHONY: check bootstrap infra deploy deploy-check fetch-backups restore-drill migration-rehearsal vault-import vault-update vault-edit vault-view syntax lint

check:
	ansible-playbook playbooks/site.yml --check --diff $(TAGS_ARG)

bootstrap:
	ansible-playbook playbooks/site.yml -e allow_new_stacks=true $(TAGS_ARG)

infra:
	ansible-playbook playbooks/site.yml $(TAGS_ARG)

deploy:
	@test -n "$(v)" || (echo "usage: make deploy v=<version>" && exit 1)
	ansible-playbook playbooks/deploy.yml -e version=$(v) $(if $(first),-e allow_new_stacks=true) $(if $(stop_first),-e stop_first=true)

deploy-check:
	@test -n "$(v)" || (echo "usage: make deploy-check v=<version>" && exit 1)
	ansible-playbook playbooks/deploy.yml -e version=$(v) $(if $(stop_first),-e stop_first=true) --check --diff

fetch-backups:
	ansible-playbook playbooks/fetch-backups.yml

restore-drill:
	scripts/backup-restore.sh drill $(BACKUPS) --keys-from "$$(scripts/backup-keys-source.sh)" $(if $(until),--until "$(until)")

migration-rehearsal:
	scripts/backup-restore.sh rehearse $(BACKUPS) $(if $(until),--until "$(until)")

vault-import:
	@test ! -e $(VAULT) || (echo "$(VAULT) already exists, use 'make vault-edit'" && exit 1)
	$(ANSIBLE_PYTHON) scripts/import-secrets.py | ansible-vault encrypt --output $(VAULT)

vault-update:
	ansible-vault view $(VAULT) | $(ANSIBLE_PYTHON) scripts/import-secrets.py --update | ansible-vault encrypt --output $(VAULT).new
	mv $(VAULT).new $(VAULT)

vault-edit:
	ansible-vault edit $(VAULT)

vault-view:
	ansible-vault view $(VAULT)

syntax:
	ansible-playbook playbooks/site.yml --syntax-check
	ansible-playbook playbooks/deploy.yml --syntax-check
	ansible-playbook playbooks/fetch-backups.yml --syntax-check

lint:
	ansible-lint playbooks roles
