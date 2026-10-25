#!/bin/sh

apk update
apk add gettext
envsubst < /etc/dnsmasq.conf.template > /etc/dnsmasq.conf

dnsmasq -k