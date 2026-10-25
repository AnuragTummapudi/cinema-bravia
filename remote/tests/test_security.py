import asyncio
import json
from pathlib import Path
import re
import sys
import tempfile
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from aiohttp.test_utils import TestClient, TestServer
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from server import Controller, create_app, validate_host
from androidtvremote2 import ConnectionClosed


class LocalBoundaryTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.controller = Controller(Path(self.temp.name)/'state', '192.168.1.8')
        self.client = TestClient(TestServer(create_app(self.controller, 8765)))
        await self.client.start_server()
        self.headers = {'Host':'127.0.0.1:8765'}
        response = await self.client.get('/', headers=self.headers)
        page = await response.text()
        self.token = re.search(r'name="remote-token" content="([^"]+)"',page)[1]

    async def asyncTearDown(self):
        await self.client.close()
        self.temp.cleanup()

    async def post(self, body, **extra):
        headers = {**self.headers, 'X-Remote-Token':self.token, **extra}
        return await self.client.post('/api/command', json=body, headers=headers)

    async def test_dns_rebinding_and_cross_origin_are_rejected(self):
        response = await self.client.get('/api/state', headers={'Host':'attacker.example:8765'})
        self.assertEqual(response.status,403)
        response = await self.post({'kind':'key','value':'HOME'}, Origin='https://attacker.example')
        self.assertEqual(response.status,403)

    async def test_mutations_need_session_token(self):
        response = await self.client.post('/api/command',json={'kind':'key','value':'HOME'},headers=self.headers)
        self.assertEqual(response.status,403)

    async def test_disconnected_commands_fail_instead_of_simulating_success(self):
        response = await self.post({'kind':'key','value':'HOME'})
        self.assertEqual(response.status,503)
        self.assertIsNone(self.controller.last_command)

    async def test_allowlist_blocks_arbitrary_commands_and_app_links(self):
        self.controller.client = MagicMock()
        self.controller.status = 'connected'
        for command in [{'kind':'key','value':'pm disable-user com.android.settings'},
                        {'kind':'key','value':'TV_INPUT'},
                        {'kind':'app','value':'https://attacker.example'},
                        {'kind':'key','value':['HOME']},
                        {'kind':'text','value':'x'*1001}, []]:
            response = await self.post(command)
            self.assertEqual(response.status,400)
        self.controller.client.send_key_command.assert_not_called()

    async def test_text_is_not_retained_in_status(self):
        self.controller.client = MagicMock()
        self.controller.status = 'connected'
        self.controller.send('text','private test phrase')
        self.assertNotIn('private test phrase',json.dumps(self.controller.last_command))
        self.controller.client.send_text.assert_called_once_with('private test phrase')

    async def test_private_files_are_not_served(self):
        response = await self.client.get('/client-key.pem',headers=self.headers)
        self.assertEqual(response.status,404)

    async def test_expired_pairing_does_not_save_credentials(self):
        self.controller.status = 'pairing'
        self.controller.client = MagicMock()
        self.controller.pair_started = 0
        with self.assertRaisesRegex(ValueError,'expired'):
            await self.controller.pair_finish('A1B2C3')
        self.assertFalse(self.controller.config_file.exists())

    async def test_changed_tv_identity_never_enables_controls(self):
        self.controller.config['pin'] = 'expected'
        fake = MagicMock()
        fake.async_connect = AsyncMock()
        with patch.object(self.controller,'prepare',AsyncMock(return_value=fake)),patch('server.peer_pin',return_value='different'):
            await self.controller.connect_loop()
        self.assertEqual(self.controller.status,'pairing_required')
        self.assertIsNone(self.controller.client)
        with self.assertRaises(ConnectionClosed):
            self.controller.send('key','HOME')

    async def test_reconnect_uses_actual_transport_loss_recovery(self):
        self.controller.config['pin'] = 'expected'
        self.controller.status = 'connected'
        self.controller.client = MagicMock()
        self.controller.worker = MagicMock()
        self.controller.worker.done.return_value = False
        await self.controller.reconnect()
        self.controller.client._transport.close.assert_called_once_with()
        self.controller.worker.cancel.assert_not_called()
        self.assertEqual(self.controller.status,'reconnecting')
        self.controller.worker = None

    def test_public_and_loopback_tv_targets_are_rejected(self):
        for address in ['8.8.8.8','127.0.0.1','169.254.1.2','0.0.0.0','192.0.2.1','example.com']:
            with self.assertRaises(ValueError):validate_host(address)
        self.assertEqual(validate_host('192.168.1.8'),'192.168.1.8')


if __name__ == '__main__':
    unittest.main()
