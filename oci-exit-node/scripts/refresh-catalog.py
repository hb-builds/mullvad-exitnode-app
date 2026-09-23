#!/usr/bin/env python3
"""Refresh public relay names/availability; never handles account credentials."""
import json
import re
import sys
import urllib.request
from pathlib import Path

URL = 'https://api.mullvad.net/www/relays/wireguard/'
FIELDS = ('hostname', 'country_code', 'country_name', 'city_code', 'city_name', 'active')
with urllib.request.urlopen(URL, timeout=30) as response:
    data=response.read(4_000_001)
if len(data)>4_000_000:
    raise SystemExit('Relay response too large')
rows=json.loads(data)
if not isinstance(rows,list) or not rows:
    raise SystemExit('Invalid relay response')
result=[]
for row in rows:
    item={field:row[field] for field in FIELDS}
    if not re.fullmatch(r'[a-z]{2}-[a-z0-9]+-wg-[0-9]+',item['hostname']):continue
    if any(not isinstance(item[field],str) for field in FIELDS[:-1]) or type(item['active']) is not bool:
        raise SystemExit('Invalid relay entry')
    result.append(item)
if not result:
    raise SystemExit('No WireGuard relays found')
output=Path(sys.argv[1]) if len(sys.argv)>1 else Path(__file__).resolve().parents[1]/'locations.json'
output.write_text(json.dumps(sorted(result,key=lambda r:r['hostname']),indent=2)+'\n')
print(f'Saved {len(result)} public relay entries.')
