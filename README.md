# Burmalda AI Assistant

Flutter web-клиент — `webClient`, контракт API — `openapi.yaml`.

Запустите Docker Desktop и выполните из корня проекта:

```sh
docker compose up --build -d
```

Откройте http://localhost:3000.

Остановка:

```sh
docker compose down
```

Будущий сервис `backend` добавляется в `compose.yaml` и слушает порт `8080`.
Nginx перенаправляет `/api/...` на него, убирая `/api`. Интерфейс работает и без backend.
