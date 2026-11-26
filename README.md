# Burmalda AI Assistant

## Подготовка

Запустите Docker Desktop. Все команды выполняются из корня проекта.

```sh
cp -n .env.example .env
```

Создаёт `.env`, если его ещё нет. Заполните `TELEGRAM_BOT_TOKEN` и
`TELEGRAM_ALLOWED_PEER_ID` — токен бота и ID разрешённого личного чата.

Backend пока не добавлен в Compose. Для API на компьютере задайте в `.env`:
`BACKEND_HOST=host.docker.internal`, `BACKEND_PORT=8080` и
`TELEGRAM_API_BASE_URL=http://host.docker.internal:8080`.

## Весь проект — Web + Telegram

```sh
docker compose --profile telegram up --build -d
```

Собрать и запустить оба клиента. Web: http://localhost:3000.

```sh
docker compose --profile telegram down
docker compose --profile telegram up --build -d
```

Полностью остановить, пересобрать и поднять оба клиента с текущими настройками.

```sh
docker compose --profile telegram down
```

Остановить и удалить контейнеры обоих клиентов.

## Web отдельно

```sh
docker compose up --build -d web
```

Собрать и запустить Web: http://localhost:3000.

```sh
docker compose up --build --force-recreate --no-deps -d web
```

Пересобрать и пересоздать Web, не затрагивая Telegram.

```sh
docker compose stop web
```

Остановить только Web.

## Telegram отдельно

```sh
docker compose up --build -d telegram
```

Собрать и запустить бота. При явном указании сервиса профиль включается автоматически.

```sh
docker compose up --build --force-recreate --no-deps -d telegram
```

Пересобрать и пересоздать бота, не затрагивая Web. Выбранную сессию нужно выбрать заново.

```sh
docker compose stop telegram
```

Остановить только Telegram.

Команды бота: `/start`, `/sessions`, `/create_session`, `/pick_session {id}`,
`/current_session`. После выбора сессии отправляйте текст. История при переключении не выводится.

## Логи

```sh
docker compose logs -f web telegram
```

Логи обоих клиентов. Для одного оставьте только `web` или `telegram`.
