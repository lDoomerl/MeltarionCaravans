# PlaceholderAPI

## Назначение

Если на сервере установлен PlaceholderAPI, MeltarionCaravans регистрирует внутреннюю expansion:

```text
%meltarioncaravans_*%
```

Identifier:

```text
meltarioncaravans
```

Интеграция:

- опциональная;
- не мешает запуску плагина без PlaceholderAPI;
- использует read-only `MeltarionCaravansApi`.

## Регистрация expansion

Expansion регистрируется только если:

- PlaceholderAPI установлен;
- PlaceholderAPI включён;
- в `config.yml` стоит `placeholderapi.enabled: true`.

Если PlaceholderAPI отсутствует:

- плагин не падает;
- MeltarionCaravans пишет только info-сообщение в лог;
- остальные функции работают как обычно.

## Раздел config.yml

```yml
placeholderapi:
  enabled: true
  empty-value: ""
  eta-format: "mm:ss"
  hp-decimals: 0
```

### enabled

Включает или выключает регистрацию expansion.

### empty-value

Что возвращать, если:

- игрок `null`;
- у игрока нет нужного каравана;
- индекс вышел за пределы списка.

### eta-format

Формат ETA.

Поддерживаются токены:

- `hh`
- `mm`
- `ss`

Примеры:

- `mm:ss`
- `hh:mm:ss`

### hp-decimals

Сколько знаков после запятой показывать у `hp` и `max_hp`.

## Плейсхолдеры

### Общие

- `%meltarioncaravans_count%`
- `%meltarioncaravans_limit%`
- `%meltarioncaravans_active%`
- `%meltarioncaravans_traveling%`
- `%meltarioncaravans_stopped%`
- `%meltarioncaravans_attacked%`
- `%meltarioncaravans_returning%`

### Первый караван игрока

- `%meltarioncaravans_first_name%`
- `%meltarioncaravans_first_status%`
- `%meltarioncaravans_first_hp%`
- `%meltarioncaravans_first_max_hp%`
- `%meltarioncaravans_first_eta%`
- `%meltarioncaravans_first_route_running%`
- `%meltarioncaravans_first_physical_spawned%`

### Индексированные

- `%meltarioncaravans_1_name%`
- `%meltarioncaravans_1_status%`
- `%meltarioncaravans_1_hp%`
- `%meltarioncaravans_1_max_hp%`
- `%meltarioncaravans_1_eta%`
- `%meltarioncaravans_1_route_running%`
- `%meltarioncaravans_1_physical_spawned%`
- `%meltarioncaravans_1_active_sell_offers%`
- `%meltarioncaravans_1_active_buy_orders%`

Индекс:

- 1-based;
- совпадает с порядком из `/caravan list`.

## Что возвращается

### status

Возвращает стабильную raw enum-строку:

- `IDLE`
- `TRAVELING`
- `STOPPED`
- `ATTACKED`
- `RETURNING`

### hp и max_hp

Возвращаются с точностью из `hp-decimals`.

### eta

Форматируется по `placeholderapi.eta-format`.

### route_running и physical_spawned

Возвращаются как:

- `true`
- `false`

## Примеры

```text
%meltarioncaravans_count%
%meltarioncaravans_limit%
%meltarioncaravans_first_name%
%meltarioncaravans_1_status%
%meltarioncaravans_2_eta%
```

## Ограничения

- write API не добавляется;
- инвентари и `ItemStack` не выдаются;
- HTTP endpoint не поднимается.
