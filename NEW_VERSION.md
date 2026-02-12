
## HakuneChat - New Version

### RU

#### Добавлено
- Поддержка Markdown-ссылок для Java-игроков: `[текст](ссылка)`.
- Интеграция `bStats` для статистики использования плагина.
- Поддержка PlaceholderAPI в `motd` (строки `motd.lines`).
- Поддержка многострочного формата `head-message.format`.
- Новый режим слежения для `armorstand` в сообщениях над головой:
  - `head-message.armorstand-follow-mode: passenger | teleport`

#### Изменено
- Улучшена обработка ссылок: markdown-ссылки и обычные `http/https/www` автоматически становятся кликабельными.
- Улучшена работа `head-message` в `render-mode: armorstand` (многострочность, стабильное обновление).
- Для `bStats` `pluginId` зафиксирован в коде (не редактируется в `config.yml`).

#### Важно
- Градиент ника над головой в обычном `name-tag` (scoreboard) ограничен возможностями Minecraft и полноценно не поддерживается.
- Для сообщений над головой с более гибким форматированием рекомендуется `render-mode: armorstand`.

---

### EN

#### Added
- Markdown links support for Java players: `[text](url)`.
- `bStats` integration for plugin usage statistics.
- PlaceholderAPI support in `motd` (`motd.lines`).
- Multi-line support for `head-message.format`.
- New `armorstand` follow mode for overhead messages:
  - `head-message.armorstand-follow-mode: passenger | teleport`

#### Changed
- Link handling improved: both markdown links and plain `http/https/www` links are now clickable.
- Improved `head-message` behavior in `render-mode: armorstand` (multi-line rendering and stable updates).
- `bStats` `pluginId` is now fixed in code (not editable via `config.yml`).

#### Important
- Gradient nickname above player in regular `name-tag` (scoreboard) is limited by Minecraft and is not fully supported.
- For advanced overhead message formatting, `render-mode: armorstand` is recommended.
