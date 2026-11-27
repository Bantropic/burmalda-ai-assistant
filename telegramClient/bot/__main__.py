import asyncio
import logging
import os
from pathlib import Path
from urllib.parse import urlsplit

import aiohttp
from aiogram import Bot, Dispatcher
from aiogram.utils.token import TokenValidationError
from dotenv import load_dotenv

from .api import AgentApi
from .handlers import create_router


async def main():
    load_dotenv(Path(__file__).resolve().parents[2] / '.env')
    token = os.getenv('TELEGRAM_BOT_TOKEN', '').strip()
    try:
        peer_id = int(os.getenv('TELEGRAM_ALLOWED_PEER_ID', ''))
        if peer_id <= 0:
            raise ValueError
    except ValueError:
        raise SystemExit('Укажите положительный TELEGRAM_ALLOWED_PEER_ID в .env.')
    base_url = os.getenv('TELEGRAM_API_BASE_URL', 'http://localhost:8080').strip().rstrip('/')
    url = urlsplit(base_url)
    if url.scheme not in ('http', 'https') or not url.netloc or url.query or url.fragment:
        raise SystemExit('Некорректный TELEGRAM_API_BASE_URL в .env.')
    try:
        bot = Bot(token=token)
    except TokenValidationError:
        raise SystemExit('Укажите корректный TELEGRAM_BOT_TOKEN в .env.')
    async with bot, aiohttp.ClientSession() as client:
        dispatcher = Dispatcher(disable_fsm=True)
        dispatcher.include_router(create_router(AgentApi(base_url, client), peer_id))
        # One allowed chat: process updates sequentially to preserve session/message order.
        await dispatcher.start_polling(bot, allowed_updates=['message'], handle_as_tasks=False)


if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO)
    asyncio.run(main())
