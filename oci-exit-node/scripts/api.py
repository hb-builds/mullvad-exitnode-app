#!/usr/bin/env python3
"""Unprivileged HTTPS facade, reachable only on the Tailscale interface."""
import hmac
import json
import socket
import ssl
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

CONFIG = None


def broker(data):
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as connection:
        connection.settimeout(12)
        connection.connect('/run/mullvad-controller/control.sock')
        connection.sendall(json.dumps(data).encode() + b'\n')
        with connection.makefile('rb') as stream:
            return json.loads(stream.readline(1_000_000))


class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.0'
    server_version = 'ExitController'
    sys_version = ''

    def setup(self):
        self.request.settimeout(8)
        super().setup()

    def log_message(self, *_):
        pass  # No credentials, request bodies or browsing data in logs.

    def reply(self, status, data):
        body = json.dumps(data).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.end_headers()
        self.wfile.write(body)

    def authorized(self):
        if self.client_address[0] not in CONFIG['allowed_ips']:
            self.reply(403, {'error': 'Device is not allowed'})
            return False
        actual = self.headers.get('Authorization', '')
        if not hmac.compare_digest(actual.encode(), ('Bearer ' + CONFIG['token']).encode()):
            self.reply(401, {'error': 'Pair this app with the gateway'})
            return False
        # No browser cross-origin control; native app sends no Origin header.
        if self.headers.get('Origin'):
            self.reply(403, {'error': 'Browser requests are not supported'})
            return False
        return True

    def do_GET(self):
        if not self.authorized():
            return
        paths = {'/v1/state': 'state', '/v1/locations': 'locations'}
        if self.path not in paths:
            return self.reply(404, {'error': 'Not found'})
        self.call({'operation': paths[self.path]})

    def do_POST(self):
        if not self.authorized():
            return
        if self.path != '/v1/change':
            return self.reply(404, {'error': 'Not found'})
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 < length <= 4096 or self.headers.get_content_type() != 'application/json':
                raise ValueError()
            data = json.loads(self.rfile.read(length))
        except (ValueError, TypeError):
            return self.reply(400, {'error': 'Invalid request'})
        self.call({'operation': 'change', 'change': data}, 202)

    def call(self, data, status=200):
        try:
            result = broker(data)
            self.reply(409 if result.get('busy') else 400 if 'error' in result else status, result)
        except (OSError, ValueError):
            self.reply(503, {'error': 'Gateway is unavailable'})


class Server(HTTPServer):
    # Bound workers and deferred handshakes prevent a slow client blocking everyone.
    def __init__(self, address, handler, context):
        self.context = context
        self.pool = ThreadPoolExecutor(max_workers=8)
        import threading
        self.slots = threading.BoundedSemaphore(8)
        super().__init__(address, handler)

    def process_request(self, request, address):
        if not self.slots.acquire(blocking=False):
            request.close()
            return
        self.pool.submit(self.process, request, address)

    def process(self, request, address):
        wrapped = None
        try:
            request.settimeout(8)
            if address[0] not in CONFIG['allowed_ips']:
                return
            wrapped = self.context.wrap_socket(request, server_side=True)
            self.finish_request(wrapped, address)
        except (OSError, ssl.SSLError):
            pass
        finally:
            self.shutdown_request(wrapped or request)
            self.slots.release()


if __name__ == '__main__':
    CONFIG = json.loads(Path('/etc/mullvad-controller/access.json').read_text())
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain('/etc/mullvad-controller/server.crt', '/etc/mullvad-controller/server.key')
    Server((CONFIG['address'], 8443), Handler, context).serve_forever()
