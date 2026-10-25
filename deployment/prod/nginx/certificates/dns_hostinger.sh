#!/usr/bin/env sh
# shellcheck disable=SC2034
dns_cf_info='Hostinger
Site: hostinger.com
Docs: github.com/acmesh-official/acme.sh/wiki/dnsapi#dns_hostinger
Options:
 HOSTINGER_Key API Key
'

HOSTINGER_Api="https://developers.hostinger.com/api/dns/v1"

########  Public functions #####################
#Usage: add  _acme-challenge.www.domain.com   "XKrxpRBosdIKFzxW_CT3KLZNf6q0HG9i01zxXp5CPBs"
dns_hostinger_add() {
  fulldomain=$1
  txtvalue=$2

  HOSTINGER_Key="${HOSTINGER_Key:-$(_readaccountconf_mutable HOSTINGER_Key)}"

  if [ -z "$HOSTINGER_Key" ]; then
    HOSTINGER_Key=""
    _err "You don't specify hostinger api key yet."
    _err "Please create your api key and try again."
    return 1
  fi

  _saveaccountconf_mutable HOSTINGER_Key "$HOSTINGER_Key"

  _debug "Detecting the root zone"

  if ! _get_root "$fulldomain"; then
    _err "Invalid domain"
    return 1
  fi

  _debug _sub_domain "$_sub_domain"
  _debug _domain "$_domain"

  _info "Adding record"

  if ! _hostinger_rest POST "zones/$_domain/validate" "{\"overwrite\":false,\"zone\":[{\"name\":\"$_sub_domain\",\"records\":[{\"content\":\"$txtvalue\"}],\"ttl\":300,\"type\":\"TXT\"}]}"; then
    _err "Failed to validate zones"
    return 1
  elif ! _contains "$response" "Request accepted"; then
    _err "Zone validation error"
    _debug "$response"
    return 1
  else
    _info "Validation success"
    _debug "$response"
  fi

  if _hostinger_rest PUT "zones/$_domain" "{\"overwrite\":false,\"zone\":[{\"name\":\"$_sub_domain\",\"records\":[{\"content\":\"$txtvalue\"}],\"ttl\":300,\"type\":\"TXT\"}]}"; then
    if _contains "$response" "Request accepted"; then
      _info "Added, OK"
      return 0
    elif _contains "$response" "errors"; then
      _err "Add txt record error."
      return 1
    else
      _info "Unexpected error"
      _debug "$response"
      return 1
    fi
  fi
  _err "Add txt record error."
  return 1
}

#fulldomain txtvalue
dns_hostinger_rm() {
  fulldomain=$1
  txtvalue=$2

  HOSTINGER_Key="${HOSTINGER_Key:-$(_readaccountconf_mutable HOSTINGER_Key)}"

  _debug "First detect the root zone"
  if ! _get_root "$fulldomain"; then
    _err "invalid domain"
    return 1
  fi

  _debug _sub_domain "$_sub_domain"
  _debug _domain "$_domain"

  _debug "Getting records"
  _hostinger_rest GET "zones/$_domain"

  if ! _contains "$response" "$txtvalue"; then
    _info "Don't need to remove."
  else
    if ! _hostinger_rest DELETE "zones/$_domain" "{\"filters\":[{\"name\":\"$_sub_domain\",\"type\":\"TXT\"}]}"; then
      _err "Delete record error."
      return 1
    fi
  fi
  return 0
}

####################  Private functions below ##################################
#_acme-challenge.www.domain.com
#returns
# _sub_domain=_acme-challenge.www
# _domain=domain.com
_get_root() {
  domain=$1
  i=1
  p=1

  while true; do
    h=$(printf "%s" "$domain" | cut -d . -f "$i"-100)
    _debug h "$h"
    if [ -z "$h" ]; then
      return 1
    fi

    if ! _hostinger_rest GET "zones/$h"; then
      return 1
    fi

    if _contains "$response" "\"content\":\"$h.\",\"is_disabled\":false"; then
      _sub_domain=$(printf "%s" "$domain" | cut -d . -f 1-"$p")
      _domain=$h
      return 0
    fi
    p=$i
    i=$(_math "$i" + 1)
  done
  return 1
}

_hostinger_rest() {
  method=$1
  path="$2"
  data="$3"
  _debug "Method: $method Path: $path Data: $data"

  key_trimmed=$(echo "$HOSTINGER_Key" | tr -d '"')

  authorization="Bearer $key_trimmed"

  export _H1="Content-Type: application/json; charset=UTF-8"
  export _H2="Authorization: $authorization"

  if [ "$method" != "GET" ]; then
    _debug data "$data"
    response="$(_post "$data" "$HOSTINGER_Api/$path" "" "$method")"
  else
    response="$(_get "$HOSTINGER_Api/$path")"
  fi

  if [ "$?" != "0" ]; then
    _err "error $path"
    return 1
  fi
  _debug2 response "$response"
  return 0
}