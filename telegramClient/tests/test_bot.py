import unittest
from unittest.mock import AsyncMock

from aiogram import Bot, Dispatcher
from aiogram.methods import SendMessage
from aiogram.types import Update
from aiohttp import ClientSession, web

from bot.api import AgentApi, AgentApiError
from bot.handlers import create_router


class BotTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.api = AsyncMock(spec=AgentApi)
        self.api.create.return_value = 'session-one'
        self.api.send.return_value = 'Ответ агента'
        self.bot = Bot('123456:TEST_TOKEN')
        self.bot.session = AsyncMock()
        self.dispatcher = Dispatcher(disable_fsm=True)
        self.dispatcher.include_router(create_router(self.api, 42))
        self.update_id = 0

    async def send(self, text, peer_id=42, chat_type='private'):
        self.bot.session.reset_mock()
        self.update_id += 1
        update = Update.model_validate({
            'update_id': self.update_id,
            'message': {
                'message_id': self.update_id, 'date': 0,
                'chat': {'id': peer_id, 'type': chat_type},
                'from': {'id': peer_id, 'is_bot': False, 'first_name': 'Test'},
                'text': text,
                'entities': ([{'type': 'bot_command', 'offset': 0,
                               'length': len(text.split()[0])}] if text.startswith('/') else []),
            },
        })
        await self.dispatcher.feed_update(self.bot, update)
        return [call.args[1] for call in self.bot.session.call_args_list
                if isinstance(call.args[1], SendMessage)]

    async def test_access_and_local_commands_do_not_call_api(self):
        for command in ['/start', '/sessions', '/create_session', '/pick_session x',
                        '/current_session', 'hello']:
            self.assertEqual(await self.send(command, 99), [])
            self.assertEqual(await self.send(command, 42, 'group'), [])
        self.assertTrue(await self.send('/start'))
        self.assertTrue(await self.send('/current_session'))
        self.assertTrue(await self.send('hello'))
        self.assertEqual(self.api.mock_calls, [])

    async def test_pick_hides_history_and_preserves_selection_on_error(self):
        await self.send('/create_session')
        self.api.check_session.side_effect = AgentApiError('Сессия не найдена')
        await self.send('/pick_session missing')
        replies = await self.send('/current_session')
        self.assertEqual(replies[0].text, '<code>session-one</code>')
        self.api.check_session.side_effect = None
        self.api.check_session.return_value = [{'content': 'SECRET HISTORY'}]
        replies = await self.send('/pick_session session-two')
        self.assertNotIn('SECRET HISTORY', str(replies))
        await self.send('hello')
        self.api.send.assert_awaited_once_with('session-two', 'hello')

    async def test_list_and_long_reply(self):
        self.api.sessions.return_value = ['a', 'b']
        replies = await self.send('/sessions')
        self.assertEqual(replies[0].text, '<code>a\nb</code>')
        self.assertEqual(replies[0].parse_mode, 'HTML')
        await self.send('/create_session')
        content = '<tag>😀' * 1500
        self.api.send.return_value = content
        replies = await self.send('hello')
        self.assertEqual(''.join(reply.text for reply in replies), content)
        self.assertTrue(all(len(reply.text.encode('utf-16-le')) // 2 <= 4096
                            and reply.parse_mode is None for reply in replies))

    async def test_invalid_commands_and_failed_create_preserve_session(self):
        await self.send('/create_session')
        self.api.create.side_effect = AgentApiError('Ошибка сервера')
        await self.send('/create_session')
        await self.send('/pick_session')
        await self.send('/pick_session a b')
        await self.send('/unknown')
        self.api.check_session.assert_not_awaited()
        self.api.send.assert_not_awaited()
        replies = await self.send('/current_session')
        self.assertIn('session-one', replies[0].text)


class ApiTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.requests = []
        self.status = 200
        self.body = []

        async def handler(request):
            body = await request.json() if request.can_read_body else None
            self.requests.append((request.method, request.path, body))
            return web.json_response(self.body, status=self.status)

        app = web.Application()
        app.router.add_route('*', '/{path:.*}', handler)
        self.runner = web.AppRunner(app)
        await self.runner.setup()
        self.site = web.TCPSite(self.runner, '127.0.0.1', 0)
        await self.site.start()
        port = self.runner.addresses[0][1]
        self.client = ClientSession()
        self.api = AgentApi(f'http://127.0.0.1:{port}', self.client)

    async def asyncTearDown(self):
        await self.client.close()
        await self.runner.cleanup()

    async def test_contract(self):
        self.body = [{'id': 'one', 'title': 'Test'}]
        self.assertEqual(await self.api.sessions(), ['one'])
        self.body = {'id': 'two', 'title': 'Test'}
        self.assertEqual(await self.api.create(), 'two')
        self.body = []
        await self.api.check_session('two')
        self.body = {'content': 'reply'}
        self.assertEqual(await self.api.send('two', 'hello'), 'reply')
        self.assertEqual(self.requests, [
            ('GET', '/sessions', None), ('POST', '/sessions', None),
            ('GET', '/sessions/two/messages', None),
            ('POST', '/sessions/two/messages', {'content': 'hello'}),
        ])

    async def test_errors_and_malformed_success(self):
        for status in [400, 404, 409, 500, 503]:
            self.status = status
            with self.assertRaisesRegex(AgentApiError, str(status)):
                await self.api.send('one', 'hello')
        self.status = 200
        self.body = {'unexpected': True}
        with self.assertRaises(AgentApiError):
            await self.api.sessions()
        with self.assertRaises(AgentApiError):
            await self.api.check_session('one')
        with self.assertRaises(AgentApiError):
            await self.api.send('one', 'hello')


if __name__ == '__main__':
    unittest.main()
