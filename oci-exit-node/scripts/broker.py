#!/usr/bin/env python3
"""Privileged, local-only gateway operations. No TCP listener or shell commands."""
import contextlib
import importlib.util
import io
import json
import os
import re
import socketserver
import threading
import time
from pathlib import Path

spec = importlib.util.spec_from_file_location('control', '/usr/local/libexec/mullvad-exit-control.py')
control = importlib.util.module_from_spec(spec)
spec.loader.exec_module(control)
SOCKET = '/run/mullvad-controller/control.sock'
lock = threading.Lock()
job = None
history = {}
verified = []


def validate_change(data):
    if not isinstance(data, dict) or set(data) != {'request_id', 'kind', 'value'}:
        raise ValueError('Invalid change request')
    if not isinstance(data['request_id'], str) or not re.fullmatch(r'[a-f0-9-]{36}', data['request_id']):
        raise ValueError('Invalid request ID')
    if data['kind'] == 'location':
        if not isinstance(data['value'], str) or not re.fullmatch(r'[a-z]{2}(?:-[a-z0-9]+(?:-wg-[0-9]+)?)?', data['value']):
            raise ValueError('Invalid location')
        if not any(p.stem == data['value'] or p.stem.startswith(data['value'] + '-') for p in (control.ROOT / 'profiles').glob('*.conf')):
            raise ValueError('Unknown location')
    elif data['kind'] == 'dns':
        control.validate_mask(data['value'])
    else:
        raise ValueError('Unknown change')


def worker(current):
    global verified
    try:
        # Do not send subprocess output or sensitive configuration to clients/logs.
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            if current['kind'] == 'location':
                result = control.switch(current['value'])
                with lock:
                    verified = result
            else:
                control.set_dns(current['value'])
        with lock:
            current.update(status='done', message='Location verified on IPv4 and IPv6.' if current['kind'] == 'location' else 'DNS filters applied and resolver checked.')
    except Exception:
        with lock:
            current.update(status='failed', message='Change failed. Previous settings were restored where possible. Refresh gateway status before retrying.')
    finally:
        with lock:
            current['finished_at'] = int(time.time())


def dispatch(data):
    global job
    if not isinstance(data, dict):
        raise ValueError('Expected an object')
    if data == {'operation': 'state'}:
        result = control.snapshot()
        with lock:
            return {**result, 'job': dict(job) if job else None, 'verified_egress': list(verified)}
    if data == {'operation': 'locations'}:
        profiles = {p.stem for p in (control.ROOT / 'profiles').glob('*.conf')}
        catalog = json.loads((control.ROOT / 'catalog.json').read_text())
        return {'locations': [p for p in catalog if p['hostname'] in profiles]}
    if set(data) != {'operation', 'change'} or data['operation'] != 'change':
        raise ValueError('Unknown operation')
    change = data['change']
    validate_change(change)
    with lock:
        key = change['request_id']
        if key in history:
            old = history[key]
            if old['kind'] != change['kind'] or old['value'] != change['value']:
                raise ValueError('Request ID already used')
            return dict(old)
        if job and job['status'] == 'running':
            return {'error': 'A change is already running', 'busy': True}
        job = {**change, 'status': 'running', 'started_at': int(time.time()), 'message': 'Applying change…'}
        history[key] = job
        while len(history) > 64:
            del history[next(iter(history))]
        threading.Thread(target=worker, args=(job,), daemon=True).start()
        return dict(job)


class Handler(socketserver.StreamRequestHandler):
    def handle(self):
        self.request.settimeout(8)
        try:
            line = self.rfile.readline(4097)
            if len(line) > 4096 or not line.endswith(b'\n'):
                raise ValueError('Request too large')
            response = dispatch(json.loads(line))
        except (ValueError, TypeError, KeyError):
            response = {'error': 'Invalid request'}
        except Exception:
            response = {'error': 'Gateway is unavailable'}
        self.wfile.write(json.dumps(response).encode() + b'\n')


class Server(socketserver.ThreadingUnixStreamServer):
    daemon_threads = True


if __name__ == '__main__':
    Path(SOCKET).unlink(missing_ok=True)
    with Server(SOCKET, Handler) as server:
        os.chmod(SOCKET, 0o660)
        server.serve_forever()
