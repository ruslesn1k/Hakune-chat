# HakuneChat

## Русский

HakuneChat — многофункциональный чат-плагин для Paper 1.21.x с отдельными форматами Java/Bedrock, интеграциями (Telegram/Discord), табом, личными сообщениями, уведомлениями о стримах и дополнительными утилитами.

### Возможности
- Локальный/глобальный чат (`chat.local-distance`, `chat.global-symbol`).
- Hex-цвета (`&#RRGGBB` и `#RRGGBB`).
- PlaceholderAPI в шаблонах и сообщениях.
- Раздельные форматы Java/Bedrock по получателю.
- Кастомные join/quit-сообщения.
- Личные сообщения (`/msg`, `/reply`) с кликабельным ответом для Java-клиентов.
- Режим прослушки локального чата (`/listenlocal`) с форматом `listenlocal`.
- Telegram-мост (Minecraft -> Telegram и Telegram -> Minecraft).
- Discord-мост:
  - отправка из Minecraft в Discord через webhook;
  - получение из Discord в Minecraft через bot token + channel id.
- Авто-уведомления о лайв-стримах (Twitch, YouTube, TikTok, VkLive) + ручная команда `/stream`.
- Мини-игра крестики-нолики (`/ttt`) с интерактивным полем для Java.
- Индикатор голосового чата PlasmoVoice (`{voice}`).
- Tab-лист: header/footer, формат игрока, сортировка, кастомный name-tag.
- Поддержка голов игроков через SkinRestorer (`{head}`).
- Bedrock-скины на offline-сервере (Floodgate/Geyser API) с обновлением по таймеру/командам.
- Настройка цвета/градиента никнейма (`/nickcolor`) с сохранением в `nickcolors.yml`.
- Кастомный MOTD в списке серверов (`motd.lines`, `{online}`, `{max}`).

### Команды
- `/chatreload` или `/hakunechat reload` (`/hchat reload`) — перезагрузка настроек.
- `/listenlocal` — включить/выключить прослушку локального чата.
- `/msg <player> <message>` — личное сообщение.
- `/reply <message>` (`/r`) — ответ последнему собеседнику.
- `/ttt <player>`, `/ttt accept <player>`, `/ttt decline <player>`, `/ttt move <1-9>`.
- `/stream <url>` — ручное уведомление о стриме.
- `/nickcolor <player> color <color>`
- `/nickcolor <player> gradient <from> to <to>`
- `/nickcolor <player> reset`

### Права
- `hakunechat.reload`
- `hakune.listenlocal`
- `hakune.pm`
- `hakune.ttt`
- `hakune.live`
- `hakune.nickcolor`

### Файлы конфигурации
- `config.yml` — чат, features, bedrock-skins, voice-indicator, minigames, motd.
- `formatting.yml` — форматы chat/join/quit/listenlocal/private/notifications.
- `tab.yml` — настройки tab-листа и name-tag.
- `integration.yml` — Telegram, Discord, live-notifications.
- `nickcolors.yml` — сохраняемые стили никнеймов (создаётся автоматически).

### Плейсхолдеры форматов
- Базовые: `{player}`, `{world}`, `{message}`
- Дополнительно: `{head}`, `{voice}`
- Уведомления: `{platform}`, `{name}`, `{url}`, `{title}`
- MOTD: `{online}`, `{max}`

### Зависимости (softdepend)
- PlaceholderAPI
- Floodgate
- SkinsRestorer
- Geyser (Geyser-Spigot)

### Контакты
Если есть пожелания или вопросы — Telegram: `kenzu925`, Discord: `kenzuofficial`.

---

## English

HakuneChat is a feature-rich chat plugin for Paper 1.21.x with Java/Bedrock viewer-based formats, integrations (Telegram/Discord), tab customization, private messages, stream notifications, and utility features.

### Features
- Local/global chat (`chat.local-distance`, `chat.global-symbol`).
- Hex colors (`&#RRGGBB` and `#RRGGBB`).
- PlaceholderAPI in templates and messages.
- Separate Java/Bedrock formats per viewer.
- Custom join/quit messages.
- Private messages (`/msg`, `/reply`) with clickable reply for Java clients.
- Local chat monitor mode (`/listenlocal`) with dedicated `listenlocal` format.
- Telegram bridge (Minecraft -> Telegram and Telegram -> Minecraft).
- Discord bridge:
  - Minecraft -> Discord via webhook;
  - Discord -> Minecraft via bot token + channel id polling.
- Automatic live notifications (Twitch, YouTube, TikTok, VkLive) plus manual `/stream`.
- Tic-tac-toe minigame (`/ttt`) with clickable board for Java.
- PlasmoVoice indicator support (`{voice}`).
- Tab list: header/footer, player format, sorting, custom name-tag.
- Player heads via SkinRestorer (`{head}`).
- Bedrock skins for offline servers (Floodgate/Geyser API), with interval/command updates.
- Nickname color/gradient management (`/nickcolor`) persisted in `nickcolors.yml`.
- Custom MOTD for server list (`motd.lines`, `{online}`, `{max}`).

### Commands
- `/chatreload` or `/hakunechat reload` (`/hchat reload`) - reload plugin settings.
- `/listenlocal` - toggle local chat monitoring.
- `/msg <player> <message>` - private message.
- `/reply <message>` (`/r`) - reply to the last PM partner.
- `/ttt <player>`, `/ttt accept <player>`, `/ttt decline <player>`, `/ttt move <1-9>`.
- `/stream <url>` - send a manual stream notification.
- `/nickcolor <player> color <color>`
- `/nickcolor <player> gradient <from> to <to>`
- `/nickcolor <player> reset`

### Permissions
- `hakunechat.reload`
- `hakune.listenlocal`
- `hakune.pm`
- `hakune.ttt`
- `hakune.live`
- `hakune.nickcolor`

### Config files
- `config.yml` - chat/features/bedrock-skins/voice-indicator/minigames/motd.
- `formatting.yml` - chat/join/quit/listenlocal/private/notification formats.
- `tab.yml` - tab list and name-tag settings.
- `integration.yml` - Telegram, Discord, live notification integration settings.
- `nickcolors.yml` - persisted nickname styles (auto-generated).

### Placeholders
- Base: `{player}`, `{world}`, `{message}`
- Extra: `{head}`, `{voice}`
- Notifications: `{platform}`, `{name}`, `{url}`, `{title}`
- MOTD: `{online}`, `{max}`

### Soft dependencies
- PlaceholderAPI
- Floodgate
- SkinsRestorer
- Geyser (Geyser-Spigot)

### Contacts
For suggestions or questions: Telegram `kenzu925`, Discord `kenzuofficial`.
