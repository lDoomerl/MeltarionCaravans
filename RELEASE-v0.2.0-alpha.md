# MeltarionCaravans v0.2.0-alpha

Первая публичная alpha-версия MeltarionCaravans с read-only интеграционным API и PlaceholderAPI-поддержкой.

## Added

- Read-only Bukkit API через `ServicesManager`
- `CaravanSummary` DTO для безопасной внешней интеграции
- Опциональная интеграция с PlaceholderAPI
- Индексированные placeholder'ы караванов
- Русская GitHub Wiki-документация
- Более удобные идентификаторы караванов для игроков и администраторов

## Changed

- `config.yml` обновлён до `config-version: 2`
- Улучшена система разрешения идентификаторов караванов
- Финализировано разделение `config.yml`, `lang.yml` и `gui.yml`

## Notes

- HTTP API endpoint'ы пока не входят в MeltarionCaravans
- Отдельный мост интеграции с DoomerAPI планируется позже
- Релиз остаётся alpha-stage и требует аккуратного тестирования на боевом сервере
