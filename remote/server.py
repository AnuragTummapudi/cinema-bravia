#!/usr/bin/env python3
"""Local-only BRAVIA Remote. All TV control travels over Remote v2, never ADB."""
from __future__ import annotations

import argparse
import asyncio
import contextlib
import hashlib
import hmac
import ipaddress
import json
import logging
import os
from pathlib import Path
import re
import secrets
import time

from aiohttp import web
from androidtvremote2 import AndroidTVRemote, CannotConnect, ConnectionClosed, InvalidAuth
from cryptography import x509
from cryptography.hazmat.primitives import serialization

ROOT = Path(__file__).resolve().parent
KEYS = frozenset({
    'DPAD_UP', 'DPAD_DOWN', 'DPAD_LEFT', 'DPAD_RIGHT', 'DPAD_CENTER', 'HOME', 'BACK',
    'VOLUME_UP', 'VOLUME_DOWN', 'VOLUME_MUTE', 'MEDIA_PLAY_PAUSE', 'MEDIA_PLAY',
    'MEDIA_PAUSE', 'MEDIA_STOP', 'MEDIA_NEXT', 'MEDIA_PREVIOUS', 'MEDIA_REWIND',
    'MEDIA_FAST_FORWARD', 'TV_INPUT', 'POWER', 'SEARCH', 'DEL',
})
APPS = {
    'netflix': {'label': 'Netflix', 'package': 'com.netflix.ninja', 'link': 'https://www.netflix.com/browse'},
    'youtube': {'label': 'YouTube', 'package': 'com.google.android.youtube.tv', 'link': 'https://www.youtube.com'},
    'prime': {'label': 'Prime Video', 'package': 'com.amazon.amazonvideo.livingroom', 'link': 'https://app.primevideo.com'},
    'sonyliv': {'label': 'Sony LIV', 'package': 'com.sonyliv', 'link': 'sonyliv://'},
}
UNSUPPORTED_KEYS = {
    'TV_INPUT': 'This Sony did not open Inputs through Remote v2. Use the physical TV remote for input selection.'
}


def validate_host(value: str) -> str:
    try:
        address = ipaddress.IPv4Address(value)
    except (ValueError, TypeError) as exc:
        raise ValueError('Enter a private IPv4 address from the TV’s Network settings.') from exc
    if not any(address in ipaddress.IPv4Network(net) for net in ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16')):
        raise ValueError('Only a private LAN address is allowed.')
    return str(address)


def peer_pin(transport) -> str:
    """Pin the TV public key authenticated by the six-character pairing code."""
    tls = transport.get_extra_info('ssl_object') if transport else None
    if tls is None:
        raise InvalidAuth('No encrypted TV connection was established.')
    cert = x509.load_der_x509_certificate(tls.getpeercert(binary_form=True))
    key = cert.public_key().public_bytes(serialization.Encoding.DER,
                                       serialization.PublicFormat.SubjectPublicKeyInfo)
    return hashlib.sha256(key).hexdigest()


class Controller:
    def __init__(self, state_dir: Path, host: str):
        self.directory = state_dir
        if self.directory.is_symlink():
            raise ValueError('Credential directory must not be a symlink.')
        self.directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        self.directory.chmod(0o700)
        self.config_file = self.directory / 'device.json'
        self.config = {'host': validate_host(host), 'pin': None, 'name': 'Sony BRAVIA BF1'}
        if self.config_file.exists():
            self.config.update(json.loads(self.config_file.read_text()))
            validate_host(self.config['host'])
        self.client = None
        self.worker = None
        self.lock = asyncio.Lock()
        self.status = 'pairing_required' if not self.config.get('pin') else 'connecting'
        self.message = 'Pair this Mac with the code shown on your television.'
        self.pair_started = 0.0
        self.pending_pin = None
        self.last_command = None
        self.last_send = 0.0
        self.connected_at = None
        self.device_info = None

    def save(self):
        temp = self.config_file.with_suffix('.tmp')
        with temp.open('w') as handle:
            json.dump(self.config, handle, indent=2)
            handle.write('\n')
        temp.chmod(0o600)
        os.replace(temp, self.config_file)

    def new_client(self):
        return AndroidTVRemote('BRAVIA Remote Mac', str(self.directory/'client-cert.pem'),
                               str(self.directory/'client-key.pem'), self.config['host'],
                               enable_ime=True, enable_voice=False)

    async def prepare(self):
        client = self.new_client()
        await client.async_generate_cert_if_missing()
        for path in self.directory.iterdir():
            if path.is_file():
                path.chmod(0o600)
        return client

    def snapshot(self):
        connected = self.status == 'connected' and self.client is not None
        return {
            'status': self.status, 'message': self.message, 'host': self.config['host'],
            'name': self.config.get('name', 'Sony BRAVIA BF1'), 'connected': connected,
            'paired': bool(self.config.get('pin')), 'transport': 'Remote v2 · TLS',
            'current_app': self.client.current_app if connected else None,
            'volume': self.client.volume_info if connected else None,
            'is_on': self.client.is_on if connected else None,
            'device_info': self.client.device_info if connected else None,
            'last_command': self.last_command, 'connected_at': self.connected_at,
            'unsupported_keys': UNSUPPORTED_KEYS,
            'pairing_expires_in': max(0, int(120-(time.monotonic()-self.pair_started))) if self.status == 'pairing' else None,
            'apps': [{'id': k, 'label': v['label'], 'package': v['package']} for k, v in APPS.items()],
        }

    async def stop_worker(self):
        if self.worker:
            self.worker.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await self.worker
            self.worker = None
        if self.client:
            self.client.disconnect()
            self.client = None

    async def start(self):
        if self.config.get('pin'):
            self.worker = asyncio.create_task(self.connect_loop())

    async def connect_loop(self):
        delay = 1
        while True:
            self.status = 'connecting' if delay == 1 else 'reconnecting'
            self.message = 'Connecting to your television…'
            client = await self.prepare()
            self.client = client
            try:
                await asyncio.wait_for(client.async_connect(), 12)
                # Upstream uses self-signed TLS certificates. Never expose controls until
                # the TV key matches the one authenticated during pairing, on EVERY connect.
                actual_pin = peer_pin(client._transport)
                if not hmac.compare_digest(actual_pin, self.config['pin']):
                    raise InvalidAuth('The TV identity changed. Pair again on the television.')
                self.status = 'connected'
                self.message = 'Connected securely to your television.'
                self.connected_at = time.time()
                self.device_info = client.device_info
                delay = 1
                protocol = client._remote_message_protocol
                await protocol.on_con_lost
                self.status = 'reconnecting'
                self.message = 'Connection lost. Reconnecting automatically…'
            except InvalidAuth as exc:
                self.status = 'pairing_required'
                self.message = str(exc) or 'The television needs to pair again.'
                return
            except (CannotConnect, ConnectionClosed, OSError, asyncio.TimeoutError) as exc:
                self.status = 'reconnecting'
                self.message = 'TV unavailable. Keep it on the same Wi-Fi; retrying automatically.'
                logging.info('Remote connection unavailable: %s', type(exc).__name__)
            finally:
                client.disconnect()
                if self.client is client:
                    self.client = None
            await asyncio.sleep(delay)
            delay = min(delay * 2, 30)

    async def discover(self):
        # Discover the explicitly targeted TV from its Remote v2 certificate; no subnet scan.
        client = await self.prepare()
        try:
            name, _mac = await asyncio.wait_for(client.async_get_name_and_mac(), 8)
            return {'host': self.config['host'], 'name': name, 'port': 6466,
                    'source': 'Remote v2 certificate', 'identity_verified': False}
        finally:
            client.disconnect()

    async def pair_start(self, host=None):
        async with self.lock:
            if host:
                host = validate_host(host)
            await self.stop_worker()
            if host and host != self.config['host']:
                self.config.update(host=host, pin=None)
            self.client = await self.prepare()
            self.status = 'connecting'
            try:
                await asyncio.wait_for(self.client.async_start_pairing(), 12)
                self.pending_pin = peer_pin(self.client._pairing_message_protocol.transport)
                self.pair_started = time.monotonic()
                self.status = 'pairing'
                self.message = 'Enter the six-character code displayed on your television.'
            except Exception:
                self.client.disconnect()
                self.client = None
                self.status = 'pairing_required'
                self.message = 'Could not start pairing. Check that the TV is awake and on the same Wi-Fi.'
                raise

    async def pair_finish(self, code):
        if not isinstance(code, str) or not re.fullmatch(r'[0-9A-Fa-f]{6}', code.strip()):
            raise ValueError('Enter the six letters and numbers shown on the television.')
        async with self.lock:
            if self.status != 'pairing' or not self.client:
                raise ValueError('Start pairing first to display a new code on the television.')
            if time.monotonic()-self.pair_started > 120:
                self.client.disconnect()
                self.status = 'pairing_required'
                raise ValueError('The code expired. Start pairing again.')
            try:
                await asyncio.wait_for(self.client.async_finish_pairing(code.strip().upper()), 12)
            except InvalidAuth:
                self.message = 'That code did not match. Check the TV code and try again.'
                raise ValueError(self.message) from None
            except (ConnectionClosed, asyncio.TimeoutError):
                self.client.disconnect()
                self.status = 'pairing_required'
                self.message = 'Pairing ended on the TV. Start again for a new code.'
                raise ValueError(self.message) from None
            self.config['pin'] = self.pending_pin
            self.save()
            self.pending_pin = None
            self.client = None
            self.status = 'connecting'
            await self.start()

    async def reconnect(self):
        async with self.lock:
            if not self.config.get('pin'):
                raise ValueError('Pair the television first.')
            if self.status == 'connected' and self.client and self.worker and not self.worker.done():
                # Close the actual TLS transport; the same recovery loop that handles
                # a Wi-Fi interruption reconnects and rechecks the saved TV identity.
                self.status = 'reconnecting'
                self.message = 'Reconnecting securely to your television…'
                self.client._transport.close()
                return
            await self.stop_worker()
            await self.start()

    def send(self, kind, value):
        if self.status != 'connected' or not self.client:
            raise ConnectionClosed('The TV is disconnected. Commands are not queued or replayed.')
        if time.monotonic()-self.last_send < 0.06:
            raise ValueError('Please slow down a little.')
        if not isinstance(kind, str) or not isinstance(value, str):
            raise ValueError('Command type and value must be strings.')
        label = value
        if kind == 'key':
            if value in UNSUPPORTED_KEYS:
                raise ValueError(UNSUPPORTED_KEYS[value])
            if value not in KEYS:
                raise ValueError('Unsupported remote button.')
            self.client.send_key_command(value)
        elif kind == 'app':
            if value not in APPS:
                raise ValueError('Unknown app shortcut.')
            self.client.send_launch_app_command(APPS[value]['link'])
            label = APPS[value]['label']
        elif kind == 'text':
            if not isinstance(value, str) or not value.strip() or len(value) > 1000:
                raise ValueError('Enter between 1 and 1,000 characters.')
            self.client.send_text(value)
            label = 'Text sent'  # Never retain typed content, passwords or search history.
        else:
            raise ValueError('Unknown command.')
        self.last_send = time.monotonic()
        self.last_command = {'label': label, 'at': time.time(), 'result': 'sent'}
        return {'result': 'sent', 'label': label,
                'note': 'Sent over Remote v2. This is not confirmation of an on-screen effect.'}


def create_app(controller: Controller, port=8765):
    token = secrets.token_urlsafe(32)
    hosts = {f'127.0.0.1:{port}', f'localhost:{port}'}
    origins = {'http://'+host for host in hosts}

    @web.middleware
    async def local_only(request, handler):
        if request.host not in hosts or request.remote not in {'127.0.0.1', '::1'}:
            raise web.HTTPForbidden(text='This remote accepts localhost connections only.')
        origin = request.headers.get('Origin')
        if origin and origin not in origins:
            raise web.HTTPForbidden(text='Cross-origin requests are not allowed.')
        if request.method not in {'GET', 'HEAD'}:
            if request.content_type != 'application/json' or not hmac.compare_digest(request.headers.get('X-Remote-Token', ''), token):
                raise web.HTTPForbidden(text='Reload the remote and try again.')
        try:
            response = await handler(request)
        except (ValueError, InvalidAuth) as exc:
            response = web.json_response({'error': str(exc)}, status=400)
        except (CannotConnect, ConnectionClosed, asyncio.TimeoutError, OSError):
            response = web.json_response({'error': 'Could not reach the TV. Check its power and Wi-Fi, then reconnect.'}, status=503)
        response.headers.update({
            'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff',
            'Referrer-Policy': 'no-referrer', 'X-Frame-Options': 'DENY',
            'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'",
        })
        return response

    app = web.Application(middlewares=[local_only], client_max_size=8192)

    async def index(request):
        page = (ROOT/'static'/'index.html').read_text().replace('__SESSION_TOKEN__', token)
        return web.Response(text=page, content_type='text/html')

    async def state(request):
        return web.json_response(controller.snapshot())

    async def command(request):
        body = await request.json()
        if not isinstance(body, dict):
            raise ValueError('Expected a command object.')
        return web.json_response(controller.send(body.get('kind'), body.get('value')))

    async def pair_start(request):
        body = await request.json()
        if not isinstance(body, dict):
            raise ValueError('Expected a pairing object.')
        await controller.pair_start(body.get('host'))
        return web.json_response(controller.snapshot())

    async def pair_finish(request):
        body = await request.json()
        if not isinstance(body, dict):
            raise ValueError('Expected a pairing object.')
        await controller.pair_finish(body.get('code'))
        return web.json_response(controller.snapshot())

    async def reconnect(request):
        await controller.reconnect()
        return web.json_response(controller.snapshot())

    async def discover(request):
        return web.json_response(await controller.discover())

    async def assets(request):
        name = request.match_info['name']
        if name not in {'app.js','style.css','icon.svg','manifest.webmanifest','icons.svg','netflix.svg','youtube.svg','prime.svg','sonyliv.png','geist.ttf','app-icon-192.png','app-icon-512.png','GEIST-LICENSE.txt','PHOSPHOR-LICENSE.txt'}:
            raise web.HTTPNotFound()
        return web.FileResponse(ROOT/'static'/name)

    async def lifecycle(app):
        await controller.start()
        yield
        await controller.stop_worker()

    app.cleanup_ctx.append(lifecycle)
    app.router.add_get('/', index)
    app.router.add_get('/api/state', state)
    app.router.add_post('/api/command', command)
    app.router.add_post('/api/pair/start', pair_start)
    app.router.add_post('/api/pair/finish', pair_finish)
    app.router.add_post('/api/reconnect', reconnect)
    app.router.add_post('/api/discover', discover)
    app.router.add_get('/{name}', assets)
    return app


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tv', default='192.168.1.100', help='Private TV IPv4 address; saved pairing takes precedence')
    parser.add_argument('--port', type=int, default=8765)
    parser.add_argument('--state-dir', type=Path, default=ROOT/'.state')
    args = parser.parse_args()
    os.umask(0o077)
    logging.basicConfig(level=logging.WARNING)
    logging.getLogger('androidtvremote2').setLevel(logging.WARNING)
    controller = Controller(args.state_dir, args.tv)
    web.run_app(create_app(controller, args.port), host='127.0.0.1', port=args.port,
                access_log=None, print=lambda _: print(f'BRAVIA Remote: http://127.0.0.1:{args.port}', flush=True))


if __name__ == '__main__':
    main()
