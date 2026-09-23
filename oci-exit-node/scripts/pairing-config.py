#!/usr/bin/env python3
"""Validate Tailscale addresses and write private controller pairing files."""
import ipaddress
import json
import os
from pathlib import Path
import secrets
import sys
import tempfile

TAILNET = ipaddress.ip_network('100.64.0.0/10')
ROOT = Path('/etc/mullvad-controller')


def validate_addresses(address, allowed):
    for value in [address, *allowed]:
        parsed = ipaddress.ip_address(value)
        if parsed.version != 4 or parsed not in TAILNET or str(parsed) != value:
            raise ValueError('Use literal Tailscale IPv4 addresses')
    if not allowed:
        raise ValueError('At least one controller device must be allowed')
    return list(dict.fromkeys([*allowed, address]))


def atomic_private(path, data):
    fd, name = tempfile.mkstemp(dir=path.parent, prefix='.pairing-')
    try:
        with os.fdopen(fd, 'w') as output:
            json.dump(data, output)
            output.flush()
            os.fsync(output.fileno())
        os.replace(name, path)
    finally:
        Path(name).unlink(missing_ok=True)


def configure(address, allowed, root=ROOT):
    allowed = validate_addresses(address, allowed)
    previous = root / 'access.json'
    token = json.loads(previous.read_text())['token'] if previous.exists() else secrets.token_urlsafe(32)
    certificate = (root / 'server.crt').read_text()
    atomic_private(previous, {'address': address, 'allowed_ips': allowed, 'token': token})
    atomic_private(root / 'pairing.json', {'name': 'Mullvad exit node',
                   'endpoint': f'https://{address}:8443', 'token': token, 'certificate': certificate})


if __name__ == '__main__':
    try:
        mode, address, *allowed = sys.argv[1:]
        if mode not in ('validate', 'write'):
            raise ValueError('Unknown mode')
        validate_addresses(address, allowed)
        if mode == 'write':
            if os.geteuid() != 0:
                raise ValueError('Run as root')
            configure(address, allowed)
    except (ValueError, KeyError, OSError):
        sys.exit('Could not configure pairing. Check the Tailscale IPv4 addresses and existing credential files.')
