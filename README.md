# HakuneChat

## Русский

HakuneChat — плагин чата для Paper 1.21.x с поддержкой локального/глобального чата, PlaceholderAPI, Hex-цветов, Bedrock/Java форматов, Telegram-моста, таб-листа и голов игроков.

### Возможности
- Локальный/глобальный чат (символ для глобального).
- Hex-цвета (`&#RRGGBB` и `#RRGGBB`).
- PlaceholderAPI в форматах и сообщениях.
- Разные форматы для Java/Bedrock по получателю.
- Сообщения входа/выхода с форматами.
- Telegram-мост (обе стороны).
- Tab-лист: header/footer, формат строки игрока, сортировка.
- Головы игроков через SkinRestorer.
- Bedrock-скины для offline-сервера (Floodgate/Geyser).

### Файлы конфигурации
- `config.yml` — основные настройки и переключатели.
- `formatting.yml` — форматы чата, join/quit.
- `tab.yml` — настройки таб-листа.

### Плейсхолдеры
- `{player}`, `{world}`, `{message}`
- `{head}` — голова игрока (если включено).

### Разделение Java/Bedrock
Формат выбирается **по получателю**: Java игроки видят java-формат, Bedrock — bedrock-формат, независимо от того, кто написал.

### Обновление Bedrock-скинов
Настраивается в `config.yml` в секции `bedrock-skins`:
- `update-mode: interval | command | both`
- `update-interval-seconds`
- `command-triggers`

### Зависимости (softdepend)
- PlaceholderAPI
- Floodgate
- SkinsRestorer
- Geyser (Geyser-Spigot)

### Контакты
Если есть пожелания или вопросы — пишите в Telegram: kenzu925 или Discord: kenzuofficial.

---

## English

HakuneChat is a Paper 1.21.x chat plugin with local/global chat, PlaceholderAPI, hex colors, Bedrock/Java formats, Telegram bridge, tab list, and player heads.

### Features
- Local/global chat (global prefix symbol).
- Hex colors (`&#RRGGBB` and `#RRGGBB`).
- PlaceholderAPI in formats and messages.
- Separate Java/Bedrock formats per viewer.
- Join/quit messages with formats.
- Telegram bridge (both directions).
- Tab list: header/footer, player line format, sorting.
- Player heads via SkinRestorer.
- Bedrock skins on offline servers (Floodgate/Geyser).

### Config files
- `config.yml` — main settings and toggles.
- `formatting.yml` — chat/join/quit formats.
- `tab.yml` — tab list settings.

### Placeholders
- `{player}`, `{world}`, `{message}`
- `{head}` — player head (if enabled).

### Java/Bedrock separation
Format is chosen **per viewer**: Java players see Java format, Bedrock players see Bedrock format regardless of the sender.

### Bedrock skin updates
Configured in `config.yml` under `bedrock-skins`:
- `update-mode: interval | command | both`
- `update-interval-seconds`
- `command-triggers`

### Soft dependencies
- PlaceholderAPI
- Floodgate
- SkinsRestorer
- Geyser (Geyser-Spigot)

### Contacts
If you have suggestions or questions, contact Telegram: kenzu925 or Discord: kenzuofficial.
