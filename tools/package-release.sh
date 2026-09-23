#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION=$(python3 - <<'PY'
import re, xml.etree.ElementTree as E
v=E.parse('exit-controller/app/src/main/AndroidManifest.xml').getroot().get('{http://schemas.android.com/apk/res/android}versionName')
if not re.fullmatch(r'[0-9]+\.[0-9]+(?:\.[0-9]+)?',v):raise SystemExit('Invalid release version')
print('v'+v)
PY
)
mkdir -p build/release
printf '%s\n' "$VERSION" > build/release-version
cp exit-controller/build/exit-controller.apk "build/release/mullvad-exit-node-$VERSION.apk"
git archive --format=zip --prefix="mullvad-exitnode-app-$VERSION/" HEAD > "build/release/mullvad-exitnode-app-$VERSION-source.zip"
python3 - <<'PY'
from pathlib import Path
import hashlib
p=Path('build/release')
files=sorted([*p.glob('*.apk'),*p.glob('*.zip')])
(p/'SHA256SUMS').write_text(''.join(f'{hashlib.sha256(f.read_bytes()).hexdigest()}  {f.name}\n' for f in files))
PY
