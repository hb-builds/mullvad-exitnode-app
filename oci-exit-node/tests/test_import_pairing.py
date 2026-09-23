import base64
import importlib.util
import json
from pathlib import Path
import stat
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1] / 'scripts'

def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, SCRIPTS / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

normalizer = load('normalizer_test', 'import-config.py')
pairing = load('pairing_test', 'pairing-config.py')
# Deliberately synthetic test material; not a real WireGuard/account key.
KEY = base64.b64encode(bytes(range(32))).decode()
CONFIG = f'''[Interface]
PrivateKey = {KEY}
Address = 10.10.0.2/32, fd00::2/128
DNS = 100.64.0.7
PostUp = touch /tmp/never-execute
[Peer]
PublicKey = {KEY}
Endpoint = 192.0.2.10:51820
AllowedIPs = 0.0.0.0/0, ::/0
'''

class ImportPairingTests(unittest.TestCase):
    def test_hooks_removed_and_routes_disabled(self):
        normalized = normalizer.normalize(CONFIG)
        self.assertNotIn('PostUp', normalized)
        self.assertNotIn('DNS', normalized)
        self.assertIn('Table = off', normalized)
        self.assertIn('PersistentKeepalive = 25', normalized)

    def test_rejects_bad_keys_addresses_endpoints_and_routes(self):
        invalid = [CONFIG.replace(KEY, 'not-a-key'), CONFIG.replace(', fd00::2/128', ''),
                   CONFIG.replace('192.0.2.10:51820', 'example.com:51820'),
                   CONFIG.replace(':51820', ':0'), CONFIG.replace('0.0.0.0/0, ::/0', '10.0.0.0/8'),
                   CONFIG + '\n[Peer2]\nPublicKey = invalid\n', CONFIG + '#' * 65536]
        for text in invalid:
            with self.subTest(text_length=len(text)), self.assertRaises((ValueError, KeyError)):
                normalizer.normalize(text)

    def test_pairing_rejects_non_tailnet_and_empty_allowlist(self):
        for address in ('127.0.0.1','192.0.2.1','example.com','100.128.0.1','::1'):
            with self.assertRaises(ValueError): pairing.validate_addresses(address, ['100.64.1.2'])
            with self.assertRaises(ValueError): pairing.validate_addresses('100.64.1.2', [address])
        with self.assertRaises(ValueError): pairing.validate_addresses('100.64.1.2', [])

    def test_pairing_preserves_token_and_private_permissions(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root/'server.crt').write_text('synthetic certificate for filesystem test')
            pairing.configure('100.64.1.2', ['100.64.1.3'], root)
            first = json.loads((root/'pairing.json').read_text())
            pairing.configure('100.64.1.2', ['100.64.1.4'], root)
            second = json.loads((root/'pairing.json').read_text())
            self.assertEqual(first['token'], second['token'])
            self.assertEqual(second['endpoint'], 'https://100.64.1.2:8443')
            self.assertEqual(json.loads((root/'access.json').read_text())['allowed_ips'], ['100.64.1.4','100.64.1.2'])
            for name in ('pairing.json','access.json'):
                self.assertEqual(stat.S_IMODE((root/name).stat().st_mode), 0o600)

if __name__ == '__main__': unittest.main()
