#!/usr/bin/env python3
import argparse
import hashlib
import ipaddress
import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.request

CONFIG_PATH = "/etc/bookk/geoblock.json"
RULES_PATH = "/var/lib/bookk-geoblock/geoblock.nft"
TABLE = "bookk_geoblock"
STATS_URL = "https://ftp.ripe.net/pub/stats/ripencc/delegated-ripencc-latest"
REFUSED_EXIT_CODE = 3


def fetch(url):
    with urllib.request.urlopen(url, timeout=60) as response:
        return response.read()


def verified_stats():
    stats = fetch(STATS_URL)
    published = fetch(STATS_URL + ".md5").decode("ascii", "replace")
    expected = re.search(r"\b[0-9a-f]{32}\b", published)
    if expected is None or hashlib.md5(stats).hexdigest() != expected.group(0):
        raise SystemExit("RIPE NCC statistics do not match their published MD5")
    return stats.decode("ascii", "replace")


def country_networks(stats, countries):
    networks = {4: [], 6: []}
    for line in stats.splitlines():
        fields = line.split("|")
        if len(fields) < 7 or fields[1] not in countries or fields[6] not in ("allocated", "assigned"):
            continue
        if fields[2] == "ipv4":
            first = ipaddress.IPv4Address(fields[3])
            last = first + int(fields[4]) - 1
            networks[4].extend(ipaddress.summarize_address_range(first, last))
        elif fields[2] == "ipv6":
            networks[6].append(ipaddress.IPv6Network(f"{fields[3]}/{fields[4]}"))
    return {version: list(ipaddress.collapse_addresses(found)) for version, found in networks.items()}


def ports(values):
    return ", ".join(str(port) for port in values)


def rules(networks, config):
    sets = []
    matches = []
    for version, family, address_type in ((4, "ip", "ipv4_addr"), (6, "ip6", "ipv6_addr")):
        if not networks[version]:
            continue
        elements = ",\n      ".join(str(network) for network in networks[version])
        sets.append(
            f"  set blocked_v{version} {{\n"
            f"    type {address_type}\n"
            f"    flags interval\n"
            f"    elements = {{\n      {elements}\n    }}\n"
            f"  }}\n"
        )
        for protocol in ("tcp", "udp"):
            protocol_ports = config.get(f"{protocol}_ports", [])
            if protocol_ports:
                matches.append(f"    {family} saddr @blocked_v{version} {protocol} dport {{ {ports(protocol_ports)} }} counter drop\n")
    return (
        f"table inet {TABLE}\n"
        f"delete table inet {TABLE}\n"
        f"table inet {TABLE} {{\n"
        + "".join(sets)
        + "  chain prerouting {\n"
        "    type filter hook prerouting priority raw; policy accept;\n"
        + "".join(matches)
        + "  }\n"
        "}\n"
    )


def nft(*arguments):
    subprocess.run(["nft", *arguments], check=True)


def table_loaded():
    return subprocess.run(["nft", "list", "table", "inet", TABLE], capture_output=True).returncode == 0


def load_saved():
    if not os.path.exists(RULES_PATH):
        raise SystemExit(f"No saved rules at {RULES_PATH}")
    nft("-f", RULES_PATH)


def proc_address(text):
    raw = bytes.fromhex(text)
    host_order = b"".join(raw[index:index + 4][::-1] for index in range(0, len(raw), 4))
    address = ipaddress.ip_address(host_order)
    if address.version == 6 and address.ipv4_mapped is not None:
        return address.ipv4_mapped
    return address


def ssh_peers():
    established = "01"
    ssh_port = 22
    peers = set()
    for table in ("/proc/net/tcp", "/proc/net/tcp6"):
        if not os.path.exists(table):
            continue
        with open(table, encoding="ascii") as connections:
            next(connections)
            for line in connections:
                fields = line.split()
                local_port = int(fields[1].split(":")[1], 16)
                if fields[3] == established and local_port == ssh_port:
                    peers.add(str(proc_address(fields[2].split(":")[0])))
    return sorted(peers)


def refused_matches(networks, refused_addresses):
    found = []
    for text in refused_addresses:
        address = ipaddress.ip_address(text)
        if any(address in network for network in networks[address.version]):
            found.append(text)
    return found


def apply(refused_addresses):
    with open(CONFIG_PATH, encoding="utf-8") as config_file:
        config = json.load(config_file)
    networks = country_networks(verified_stats(), set(config["countries"]))
    range_count = len(networks[4]) + len(networks[6])
    if range_count < config["min_ranges"]:
        raise SystemExit(f"Only {range_count} ranges for {config['countries']}, expected at least {config['min_ranges']}")
    blocked_callers = refused_matches(networks, refused_addresses)
    if blocked_callers:
        print(f"Refusing to block {', '.join(blocked_callers)}, connected over SSH from a blocked country", file=sys.stderr)
        sys.exit(REFUSED_EXIT_CODE)
    content = rules(networks, config)
    previous = open(RULES_PATH, encoding="utf-8").read() if os.path.exists(RULES_PATH) else None
    if content == previous and table_loaded():
        print(f"unchanged: {range_count} ranges")
        return
    os.makedirs(os.path.dirname(RULES_PATH), exist_ok=True)
    draft = tempfile.NamedTemporaryFile("w", dir=os.path.dirname(RULES_PATH), delete=False, encoding="utf-8")
    with draft:
        draft.write(content)
    try:
        nft("--check", "-f", draft.name)
        nft("-f", draft.name)
        os.chmod(draft.name, 0o644)
        os.replace(draft.name, RULES_PATH)
    finally:
        if os.path.exists(draft.name):
            os.unlink(draft.name)
    print(f"changed: {range_count} ranges")


def main():
    parser = argparse.ArgumentParser(description="Drop SSH and VPN traffic from the configured countries")
    parser.add_argument("command", choices=["apply", "load"])
    parser.add_argument("--refuse-ip", action="append", default=[], help="fail instead of blocking this address")
    parser.add_argument("--refuse-ssh-peers", action="store_true", help="fail instead of blocking a connected SSH client")
    arguments = parser.parse_args()
    if arguments.command == "load":
        load_saved()
    else:
        apply(arguments.refuse_ip + (ssh_peers() if arguments.refuse_ssh_peers else []))


if __name__ == "__main__":
    main()
