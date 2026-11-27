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

В нашей конфигурации Ollama и модель запускаются локально у каждого участника, а LiteLLM — в Docker.
```
ollama pull <имя-модели>
```

В нашем проекте будет использоваться Qwen 3.5 на 4b параметров поэтому команда будет следующая:
```
ollama pull qwen3.5:4b
```

Вы можете использовать любую другую LLM под ваши требования и/или системные возможности.

## LLM-шлюз

В `.env` укажите установленную модель с префиксом `ollama_chat/` и токен доступа в шлюз:

```dotenv
LITELLM_MODEL=ollama_chat/qwen3.5:4b
LITELLM_PORT=4000
```

Шлюзу нужен ключ доступа. Каждый участник один раз генерирует свой ключ следующей командой.

macOS / Linux:

```sh
echo "LITELLM_MASTER_KEY=sk-$(openssl rand -hex 32)"
```

Windows (PowerShell):

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
"LITELLM_MASTER_KEY=sk-" + [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
$rng.Dispose()
```

Скопируйте выведенную строку целиком в `.env`, заменив существующую строку `LITELLM_MASTER_KEY=`, если она есть. Команда только выводит ключ, файл не меняет. Не коммитьте ключ.

Если шлюз уже запускался, примените ключ:

```sh
docker compose up -d --force-recreate litellm
```

В запросах к шлюзу передавайте заголовок `Authorization: Bearer <значение LITELLM_MASTER_KEY>`.

Разрешите Ollama принимать подключения из Docker:

**macOS:** выполните команду и перезапустите приложение Ollama. После перезагрузки Mac команду может потребоваться повторить.

```sh
launchctl setenv OLLAMA_HOST "0.0.0.0:11434"
```

**Windows (PowerShell):** закройте Ollama в трее, выполните команду и откройте Ollama через «Пуск».

```powershell
[Environment]::SetEnvironmentVariable("OLLAMA_HOST", "0.0.0.0:11434", "User")
```

**Linux (systemd):** выполните `sudo systemctl edit ollama.service` и добавьте:

```ini
[Service]
Environment="OLLAMA_HOST=0.0.0.0:11434"
```

```sh
sudo systemctl daemon-reload
sudo systemctl restart ollama
```

`0.0.0.0` разрешает подключения через все сетевые интерфейсы, включая локальную сеть. Не открывайте порт `11434` в интернет.

Шлюз запускается вместе с проектом. Отдельный запуск и применение изменений `.env` или конфига:

```sh
docker compose up -d --force-recreate litellm
docker compose logs -f litellm
```

Для ядра в Docker: `POST http://litellm:4000/v1/chat/completions`; с компьютера: `POST http://localhost:4000/v1/chat/completions` (или порт `LITELLM_PORT`). В теле запроса используйте `"model": "burmalda"`, историю передавайте в `messages`, инструменты — в `tools`. Подключение ядра к шлюзу реализуется отдельно.

## Запуск

#### Backend + Web + LiteLLM
```sh
docker compose up --build -d
```

#### Backend + Web + LiteLLM + Telegram
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

Вместо `backend` можно указать `web`, `telegram` или `litellm`. Клиентам нужен запущенный backend, шлюзу — локальная Ollama.

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
