# Burmalda AI Assistant

## Подготовка

Запустите Docker Desktop. Команды выполняются из корня проекта.

```sh
cp -n .env.example .env
```

Для Telegram заполните в `.env` поля `TELEGRAM_BOT_TOKEN` и `TELEGRAM_ALLOWED_PEER_ID`.

## Установка модели

Скачайте Ollama:
| ОС | Установка |
|---|---|
| **Windows** | Скачать [установщик](https://ollama.com/download/windows), запустить и пройти установку. Ollama работает в фоне; команды доступны в PowerShell. |
| **macOS** | Скачать [приложение](https://ollama.com/download/mac), перенести в `Applications` и открыть. При запросе разрешить установку команды `ollama`. |
| **Linux** | Выполните команду ниже |

Команда для Linux:
```
curl -fsSL https://ollama.com/install.sh | sh
```

Скачайте модель: модель устанавливается локально для каждого пользователя. В Docker не получится его завернуть.
```
ollama pull <имя-модели>
```

В нашем проекте будет использоваться Qwen 3.5 на 4b параметров поэтому команда будет следующая:
```
ollama pull qwen3.5:4b
```

Вы можете использовать любую другую LLM под ваши требования и/или системные возможности.

## Запуск

#### Backend + Web
```sh
docker compose up --build -d
```

#### Backend + Web + Telegram
```sh
docker compose --profile telegram up --build -d
```

Web: http://localhost:3000. API: http://localhost:8080.

## Перезапуск всего проекта

```sh
docker compose --profile telegram down
docker compose --profile telegram up --build -d
```

## Отдельный сервис

Вместо `backend` можно указать `web` или `telegram`. Клиентам нужен запущенный backend.

```sh
# Запуск
docker compose up --build -d backend

# Пересборка и перезапуск
docker compose up --build --force-recreate --no-deps -d backend
```

## Остановка

```sh
docker compose --profile telegram down
```

## Backend без Docker

Нужны JDK 17+ и Maven.

```sh
mvn -f agent-core/pom.xml package
java -jar agent-core/target/agent-core-1.0-SNAPSHOT.jar
```
