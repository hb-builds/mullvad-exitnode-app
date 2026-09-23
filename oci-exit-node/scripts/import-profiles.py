#!/usr/bin/env python3
"""Read the generator ZIP from stdin; install validated profiles without extraction."""
import importlib.machinery
import importlib.util
import io
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path('/etc/mullvad-exit')

def require(condition):
    if not condition:
        raise ValueError('Invalid profile archive')

def main():
    if os.geteuid() != 0 or len(sys.argv) != 2:
        sys.exit('Usage: sudo mullvad-exit-import-profiles EXPECTED_PUBLIC_KEY < profiles.zip')
    expected_key = sys.argv[1]
    loader = importlib.machinery.SourceFileLoader('normalizer', '/usr/local/libexec/mullvad-exit-import')
    spec = importlib.util.spec_from_loader(loader.name, loader)
    module = importlib.util.module_from_spec(spec)
    loader.exec_module(module)
    profiles = {}
    try:
        data = sys.stdin.buffer.read(32 * 1024 * 1024 + 1)
        require(len(data) <= 32 * 1024 * 1024)
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            require(len(archive.infolist()) <= 5000)
            require(sum(info.file_size for info in archive.infolist()) <= 32 * 1024 * 1024)
            keys = set()
            for info in archive.infolist():
                if info.is_dir() or not info.filename.endswith('.conf'):
                    continue
                name = Path(info.filename).name
                require(re.fullmatch(r'[a-z]{2}-[a-z0-9]+-wg-[0-9]+\.conf', name))
                require(name not in profiles and info.file_size <= 65536)
                text = module.normalize(archive.read(info).decode('utf-8'))
                keys.add(next(line.split(' = ', 1)[1] for line in text.splitlines() if line.startswith('PrivateKey = ')))
                profiles[name] = text
            require(profiles and len(keys) == 1)
            public = subprocess.run(['wg', 'pubkey'], input=next(iter(keys)) + '\n',
                                    capture_output=True, text=True, check=True).stdout.strip()
            require(public == expected_key)
    except Exception:
        sys.exit('Archive rejected: expected standard Mullvad profiles matching the supplied public key; no files changed.')
    sets = ROOT / 'profile-sets'
    sets.mkdir(mode=0o700, parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix='set-', dir=sets))
    link = ROOT / '.profiles-next'
    try:
        for name, text in profiles.items():
            fd = os.open(staging / name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, 'w') as out:
                out.write(text)
        link.unlink(missing_ok=True)
        link.symlink_to(staging)
        os.replace(link, ROOT / 'profiles')
    except Exception:
        shutil.rmtree(staging)
        link.unlink(missing_ok=True)
        raise
    countries = {name.split('-')[0] for name in profiles}
    cities = {'-'.join(name.split('-')[:2]) for name in profiles}
    print(f'Imported {len(profiles)} server profiles across {len(countries)} countries and {len(cities)} cities. Active tunnel unchanged.')


if __name__ == '__main__':
    main()
