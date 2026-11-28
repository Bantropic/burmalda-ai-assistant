from html import escape

from aiogram import F, Router
from aiogram.filters import Command, CommandObject
from aiogram.types import Message

from .api import AgentApi, AgentApiError

HELP = (
    'Burmalda — ваш ИИ-ассистент.\n\n'
    '/start — показать команды\n'
    '/sessions — названия и ID сессий\n'
    '/create_session — создать и выбрать сессию\n'
    '/pick_session {id} — выбрать существующую сессию\n'
    '/current_session — текущая сессия\n\n'
    'Выберите или создайте сессию, затем отправьте текст. '
    'История при переключении не выводится. '
    'После перезапуска бота выберите сессию заново.'
)


async def answer(message: Message, text: str, *, monospace: bool = False):
    # 2000 Unicode characters occupy at most 4000 UTF-16 code units.
    for start in range(0, len(text), 2000):
        chunk = text[start:start + 2000]
        await message.answer(
            f'<code>{escape(chunk)}</code>' if monospace else chunk,
            parse_mode='HTML' if monospace else None,
        )


def create_router(api: AgentApi, allowed_peer_id: int) -> Router:
    router = Router()
    current_session: str | None = None

    @router.message.outer_middleware()
    async def restrict_access(handler, event: Message, data):
        if event.chat.type != 'private' or event.chat.id != allowed_peer_id:
            return
        try:
            return await handler(event, data)
        except AgentApiError as error:
            await answer(event, str(error))

    @router.message(Command('start'))
    async def start(message: Message):
        await answer(message, HELP)

    @router.message(Command('sessions'))
    async def sessions(message: Message):
        sessions = await api.sessions()
        if not sessions:
            await answer(message, 'Сессий пока нет. Создайте: /create_session')
            return
        # Keep each session on its own line when splitting large lists.
        lines = []
        length = 0
        for session in sessions:
            title = ' '.join(session.title.split())
            line = f'{escape(title)}: <code>{escape(session.id)}</code>'
            visible_length = len(title) + 2 + len(session.id)
            if lines and length + visible_length + 1 > 2000:
                await message.answer('\n'.join(lines), parse_mode='HTML')
                lines, length = [], 0
            lines.append(line)
            length += visible_length + 1
        await message.answer('\n'.join(lines), parse_mode='HTML')

    @router.message(Command('create_session'))
    async def create_session(message: Message):
        nonlocal current_session
        current_session = await api.create()
        await answer(message, 'Создана и выбрана сессия:')
        await answer(message, current_session, monospace=True)

    @router.message(Command('pick_session'))
    async def pick_session(message: Message, command: CommandObject):
        nonlocal current_session
        args = (command.args or '').split()
        if len(args) != 1:
            await answer(message, 'Используйте: /pick_session {id}')
            return
        await api.check_session(args[0])
        current_session = args[0]
        await answer(message, 'Выбрана сессия:')
        await answer(message, current_session, monospace=True)

    @router.message(Command('current_session'))
    async def show_current_session(message: Message):
        if current_session is None:
            await answer(message, 'Сессия не выбрана. Используйте /create_session или /pick_session {id}.')
        else:
            await answer(message, current_session, monospace=True)

    @router.message(F.text.startswith('/'))
    async def unknown_command(message: Message):
        await answer(message, 'Неизвестная команда. Список команд: /start')

    @router.message(F.text)
    async def send_message(message: Message):
        if current_session is None:
            await answer(message, 'Сначала выберите сессию: /pick_session {id} или /create_session.')
            return
        if not message.text.strip():
            await answer(message, 'Отправьте непустое текстовое сообщение.')
            return
        pending = await message.answer('<i>Burmalda думает…</i>', parse_mode='HTML')
        try:
            response = await api.send(current_session, message.text)
        except AgentApiError as error:
            response = str(error)
        response = response if response.strip() else 'Агент вернул пустой ответ.'
        await pending.edit_text(response[:2000], parse_mode=None)
        await answer(message, response[2000:])

    @router.message()
    async def unsupported(message: Message):
        await answer(message, 'Пока поддерживаются только текстовые сообщения.')

    return router
