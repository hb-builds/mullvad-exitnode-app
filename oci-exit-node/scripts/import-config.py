#!/usr/bin/env python3
"""Normalize a Mullvad config from stdin into a root-only destination.

Never log source text or values: configuration includes a private key. Only
allow declarative WireGuard fields; downloaded wg-quick hooks are discarded.
"""
import base64
import configparser
import ipaddress
import os
from pathlib import Path
import sys
import tempfile


def key(value):
    if len(base64.b64decode(value, validate=True)) != 32:
        raise ValueError('Invalid WireGuard key')
    return value


def normalize(text):
    if len(text.encode('utf-8')) > 65536:
        raise ValueError('Configuration too large')
    config = configparser.ConfigParser(interpolation=None, strict=True)
    config.optionxform = str
    config.read_string(text)
    if set(config.sections()) != {"Interface", "Peer"}:
        raise ValueError('Expected one interface and one peer')
    interface, peer = config["Interface"], config["Peer"]
    addresses = [ipaddress.ip_interface(x.strip()) for x in interface["Address"].split(",")]
    if {a.version for a in addresses} != {4, 6}:
        raise ValueError('This gateway requires both IPv4 and IPv6 addresses')
    # The cloud underlay is IPv4. A numeric endpoint also avoids a circular
    # dependency on in-tunnel DNS during startup and recovery.
    endpoint, port = peer["Endpoint"].rsplit(":", 1)
    if ipaddress.ip_address(endpoint).version != 4 or not 1 <= int(port) <= 65535:
        raise ValueError('Expected an IPv4 endpoint and valid port')
    allowed = {str(ipaddress.ip_network(x.strip())) for x in peer["AllowedIPs"].split(",")}
    if allowed != {"0.0.0.0/0", "::/0"}:
        raise ValueError('Both default routes must be allowed')
    lines = ["[Interface]", "PrivateKey = " + key(interface["PrivateKey"]),
             "Address = " + ", ".join(map(str, addresses)), "MTU = 1280", "Table = off", "",
             "[Peer]", "PublicKey = " + key(peer["PublicKey"]),
             "AllowedIPs = 0.0.0.0/0, ::/0", f"Endpoint = {endpoint}:{int(port)}",
             "PersistentKeepalive = 25"]
    if "PresharedKey" in peer:
        lines.append("PresharedKey = " + key(peer["PresharedKey"]))
    return "\n".join(lines) + "\n"


def main():
    if os.geteuid() != 0 or len(sys.argv) != 2:
        sys.exit("Usage: sudo import-config.py /etc/mullvad-exit/wg-mullvad.conf < downloaded.conf")
    try:
        result = normalize(sys.stdin.read(65537))
    except Exception:
        sys.exit("Configuration rejected; expected one standard Mullvad WireGuard peer with an IPv4 endpoint.")
    path = Path(sys.argv[1])
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=".import-", dir=path.parent)
    try:
        with os.fdopen(fd, "w") as f:
            f.write(result)
            f.flush()
            os.fsync(f.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    print("WireGuard configuration imported with mode 0600; executable hooks omitted.")


if __name__ == "__main__":
    main()
