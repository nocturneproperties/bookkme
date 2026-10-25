SHELL := /bin/bash
.SHELLFLAGS := -o pipefail -c
VAULT := inventory/group_vars/all/vault.yml
ANSIBLE_PYTHON := $(shell head -1 "$$(command -v ansible)" | sed 's/^\#!//')
TAGS_ARG := $(if $(tags),--tags $(tags))

.PHONY: check bootstrap infra deploy deploy-check vault-import vault-update vault-edit vault-view syntax lint

check:
	ansible-playbook playbooks/site.yml --check --diff $(TAGS_ARG)

bootstrap:
	ansible-playbook playbooks/site.yml -e allow_new_stacks=true $(TAGS_ARG)

infra:
	ansible-playbook playbooks/site.yml $(TAGS_ARG)

deploy:
	@test -n "$(v)" || (echo "usage: make deploy v=<version>" && exit 1)
	ansible-playbook playbooks/deploy.yml -e version=$(v) $(if $(first),-e allow_new_stacks=true)

deploy-check:
	@test -n "$(v)" || (echo "usage: make deploy-check v=<version>" && exit 1)
	ansible-playbook playbooks/deploy.yml -e version=$(v) --check --diff

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

lint:
	ansible-lint playbooks roles
