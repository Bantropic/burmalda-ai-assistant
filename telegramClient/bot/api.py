import asyncio
import json
from dataclasses import dataclass
from urllib.parse import quote

import aiohttp


class AgentApiError(Exception):
    pass


@dataclass(frozen=True)
class Session:
    id: str
    title: str


class AgentApi:
    def __init__(self, base_url: str, client: aiohttp.ClientSession):
        self.base_url = base_url.rstrip('/')
        self.client = client

    async def request(self, method: str, path: str, content: str | None = None):
        try:
            async with self.client.request(
                method,
                self.base_url + path,
                json={'content': content} if content is not None else None,
                timeout=aiohttp.ClientTimeout(total=120 if content is not None else 20),
            ) as response:
                if not 200 <= response.status < 300:
                    descriptions = {
                        400: 'Сервер отклонил запрос.',
                        404: 'Сессия не найдена. Проверьте ID или создайте новую.',
                        409: 'Агент уже работает в этой сессии. Дождитесь завершения.',
                        500: 'Ошибка выполнения агента.',
                    }
                    raise AgentApiError(
                        f'Ошибка HTTP {response.status}. '
                        + descriptions.get(response.status, 'Не удалось выполнить запрос.')
                    )
                return await response.json()
        except (aiohttp.ContentTypeError, json.JSONDecodeError, UnicodeDecodeError) as error:
            raise AgentApiError('Сервер вернул некорректный ответ.') from error
        except asyncio.TimeoutError as error:
            raise AgentApiError(
                'Сервер не ответил вовремя. Запрос мог быть выполнен; '
                'повторная отправка может создать дубликат.'
            ) from error
        except aiohttp.ClientError as error:
            raise AgentApiError('Соединение с агентом потеряно или недоступно. '
                                'Проверьте сервер. Запрос мог быть выполнен.') from error

    @staticmethod
    def session_id(value) -> str:
        if not isinstance(value, dict) or not isinstance(value.get('id'), str) or not value['id'].strip():
            raise AgentApiError('Сервер вернул некорректную сессию.')
        return value['id']

    async def sessions(self) -> list[Session]:
        result = await self.request('GET', '/sessions')
        if not isinstance(result, list):
            raise AgentApiError('Сервер вернул некорректный список сессий.')
        sessions = []
        for item in result:
            session_id = self.session_id(item)
            if not isinstance(item.get('title'), str):
                raise AgentApiError('Сервер вернул некорректное название сессии.')
            sessions.append(Session(session_id, item['title']))
        return sessions

    async def create(self) -> str:
        return self.session_id(await self.request('POST', '/sessions'))

    async def check_session(self, session_id: str) -> None:
        result = await self.request('GET', self.messages_path(session_id))
        if not isinstance(result, list):
            raise AgentApiError('Сервер вернул некорректную историю сессии.')

    async def send(self, session_id: str, content: str) -> str:
        result = await self.request('POST', self.messages_path(session_id), content)
        if not isinstance(result, dict) or not isinstance(result.get('content'), str):
            raise AgentApiError('Сервер вернул некорректное сообщение.')
        return result['content']

    @staticmethod
    def messages_path(session_id: str) -> str:
        return f'/sessions/{quote(session_id, safe="")}/messages'
