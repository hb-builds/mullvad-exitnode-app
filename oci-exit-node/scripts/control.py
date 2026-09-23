#!/usr/bin/env python3
"""List locations, switch Mullvad profiles, and verify the selected exit."""
import argparse
from collections import Counter
import fcntl
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time

ROOT = Path('/etc/mullvad-exit')
NS = 'mullvad-exit'
SERVICE = 'mullvad-exit-wireguard.service'


def run(*args, timeout=30):
    result = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    if result.returncode:
        # Do not echo output from processes that could handle configuration.
        raise RuntimeError(f'{args[0]} failed (exit {result.returncode})')
    return result.stdout


def atomic_write(path, data):
    fd, temporary = tempfile.mkstemp(prefix='.update-', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as output:
            output.write(data)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def health(expected=None):
    addresses = json.loads(run('ip', '-n', NS, '-j', 'address', 'show', 'dev', 'wg-mullvad'))[0]['addr_info']
    results = []
    for family, host in [('inet', 'am.i.mullvad.net'), ('inet6', 'ipv6.am.i.mullvad.net')]:
        address = next(a['local'] for a in addresses if a['family'] == family and a['scope'] == 'global')
        result = json.loads(run('ip', 'netns', 'exec', NS, 'curl', '-fsS', '--interface', address,
                               '--connect-timeout', '5', '--max-time', '12', '--retry', '1',
                               '--retry-delay', '1', f'https://{host}/json', timeout=30))
        if result.get('mullvad_exit_ip') is not True:
            raise RuntimeError('Egress is not a Mullvad exit IP')
        if expected and result.get('mullvad_exit_ip_hostname') != expected:
            raise RuntimeError('Reported exit server does not match the selected profile')
        results.append({k: result.get(k) for k in ('ip', 'country', 'city', 'mullvad_exit_ip_hostname')})
    return results


def show_health(results):
    for family, result in zip(('IPv4', 'IPv6'), results):
        print(f'{family}: {result["country"]}, {result["city"]} — {result["mullvad_exit_ip_hostname"]} ({result["ip"]})')


def switch(selector):
    if not re.fullmatch(r'[a-z]{2}(?:-[a-z0-9]+(?:-wg-[0-9]+)?)?', selector):
        raise RuntimeError('Use a country, city, or server code, e.g. us, us-nyc, us-nyc-wg-001')
    candidates = sorted(p for p in (ROOT / 'profiles').glob('*.conf')
                        if p.stem == selector or p.stem.startswith(selector + '-'))
    catalog = ROOT / 'catalog.json'
    if catalog.exists():
        active = {row['hostname'] for row in json.loads(catalog.read_text()) if row.get('active', True)}
        candidates = [p for p in candidates if p.stem in active]
    if not candidates:
        raise RuntimeError('No matching profile; use mullvad-exit list')
    selected = candidates[0]
    with open('/run/lock/mullvad-exit-switch.lock', 'w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        target = ROOT / 'wg-mullvad.conf'
        previous = target.read_bytes() if target.exists() else None
        print(f'Switching to {selected.stem}…', flush=True)
        run('systemctl', 'stop', SERVICE)
        try:
            atomic_write(target, selected.read_bytes())
            run('systemctl', 'start', SERVICE)
            results = health(selected.stem)
        except Exception:
            run('systemctl', 'stop', SERVICE)
            if previous is not None:
                atomic_write(target, previous)
                try:
                    run('systemctl', 'start', SERVICE)
                    health()
                    print('Selected server failed verification; restored the previous tunnel.', file=sys.stderr)
                except Exception:
                    run('systemctl', 'stop', SERVICE)
                    print('Rollback could not connect; tunnel stopped and traffic remains blocked.', file=sys.stderr)
            else:
                target.unlink(missing_ok=True)
                print('Selected server failed verification; traffic remains blocked.', file=sys.stderr)
            raise RuntimeError('Switch failed; choose another server using mullvad-exit list') from None
        atomic_write(ROOT / 'active-profile', (selected.stem + '\n').encode())
        show_health(results)
        return results


def dns_mask():
    path = ROOT / 'dns-mask'
    return validate_mask(int(path.read_text()) if path.exists() else 0)


def validate_mask(mask):
    if type(mask) is not int or not 0 <= mask <= 7:
        raise ValueError('DNS selection must contain only ads, trackers and malware')
    return mask


def apply_dns(mask):
    """Keep the resolver address stable; atomically change its in-tunnel destination."""
    validate_mask(mask)
    exists = subprocess.run(['ip', 'netns', 'exec', NS, 'nft', 'list', 'table',
                             'ip', 'mullvad_dns'], capture_output=True).returncode == 0
    rules = 'delete table ip mullvad_dns\n' if exists else ''
    rules += 'table ip mullvad_dns { chain output { type nat hook output priority dstnat; policy accept;\n'
    if mask:
        rules += f'ip daddr 10.64.0.1 meta l4proto {{ tcp, udp }} th dport 53 counter dnat to 100.64.0.{mask}\n'
    rules += '}\n}\n'
    proc = subprocess.run(['ip', 'netns', 'exec', NS, 'nft', '-f', '-'],
                          input=rules, text=True, capture_output=True, timeout=10)
    if proc.returncode:
        raise RuntimeError('Could not apply DNS routing')
    # Existing UDP/TCP mappings must not retain the previous filter selection.
    for protocol in ('udp', 'tcp'):
        proc = subprocess.run(['ip', 'netns', 'exec', NS, 'conntrack', '-D',
                              '-p', protocol, '--orig-dst', '10.64.0.1', '--dport', '53'],
                              capture_output=True, timeout=10)
        if proc.returncode not in (0, 1):
            raise RuntimeError('Could not clear old DNS mappings')


def set_dns(mask):
    validate_mask(mask)
    with open('/run/lock/mullvad-exit-switch.lock', 'w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        previous = dns_mask()
        try:
            apply_dns(mask)
            result = run('ip', 'netns', 'exec', NS, 'dig', '@10.64.0.1',
                         'mullvad.net', 'A', '+time=4', '+tries=1', timeout=8)
            if 'status: NOERROR' not in result or 'ANSWER: 0' in result:
                raise RuntimeError('Selected DNS resolver did not answer')
            atomic_write(ROOT / 'dns-mask', f'{mask}\n'.encode())
        except Exception:
            apply_dns(previous)
            raise


def snapshot():
    active = ROOT / 'active-profile'
    age = None
    try:
        lines = run('ip', 'netns', 'exec', NS, 'wg', 'show', 'wg-mullvad', 'latest-handshakes')
        stamp = max(int(line.split()[1]) for line in lines.splitlines())
        if stamp:
            age = max(0, int(time.time()) - stamp)
    except (RuntimeError, ValueError):
        pass
    return {'profile': active.read_text().strip() if active.exists() else None,
            'dns_mask': dns_mask(), 'handshake_age': age,
            'tunnel_active': subprocess.run(['systemctl', 'is-active', '--quiet', SERVICE]).returncode == 0}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['list', 'switch', 'status', 'dns', 'dns-apply'])
    parser.add_argument('location', nargs='?')
    args = parser.parse_args()
    if os.geteuid() != 0:
        sys.exit('Run with sudo.')
    if args.command == 'list':
        profiles = sorted(p.stem for p in (ROOT / 'profiles').glob('*.conf'))
        if args.location:
            for name in profiles:
                if name == args.location or name.startswith(args.location + '-'):
                    print(name)
        else:
            counts = Counter('-'.join(name.split('-')[:2]) for name in profiles)
            for city, count in sorted(counts.items()):
                print(f'{city:12} {count:3} servers')
            print('Switch with: sudo mullvad-exit switch us-nyc')
    elif args.command == 'switch':
        if not args.location:
            parser.error('switch requires a country, city, or server code')
        switch(args.location)
    elif args.command == 'dns':
        if args.location is None:
            print(dns_mask())
        else:
            set_dns(int(args.location))
            print('DNS filters updated.')
    elif args.command == 'dns-apply':
        apply_dns(dns_mask())
    else:
        active = ROOT / 'active-profile'
        print('Selected:', active.read_text().strip() if active.exists() else '(not recorded)')
        show_health(health())


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, ValueError, StopIteration, subprocess.TimeoutExpired) as error:
        sys.exit(f'Gateway check failed: {error}')
