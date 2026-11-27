# Burmalda Web

```sh
flutter pub get
flutter run -d chrome
```

Без `API_BASE_URL` интерфейс открывается без запросов к серверу.
Подключение локального ядра после его запуска:

```sh
flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:8080
```

Сервер должен разрешать CORS для адреса веб-клиента.
Для Docker с моковым backend и Nginx: `docker compose up --build -d` из корня.
В Compose адрес API по умолчанию `/api`.

- `lib/presentation` — интерфейс и состояние через ChangeNotifier.
- `lib/domain` — модели чата и сообщения.
- `lib/data` — HTTP-клиент и JSON.

Enter отправляет сообщение, Shift + Enter добавляет строку.
API возвращает готовый ответ; стриминга нет. История хранится на сервере.
