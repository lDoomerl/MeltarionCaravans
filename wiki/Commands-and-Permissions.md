# Команды и права

## Команды игроков

| Команда | Описание |
|---|---|
| `/caravan help` | Показать справку |
| `/caravan create [name]` | Создать караван |
| `/caravan list` | Показать свои караваны |
| `/caravan info <id>` | Показать информацию о своём караване |
| `/caravan rename <id> <new name>` | Переименовать свой караван |
| `/caravan delete <id>` | Удалить свой караван |
| `/caravan spawn <id>` | Призвать физический караван |
| `/caravan move <id> <x> <z>` | Отправить караван в точку |
| `/caravan stop <id>` | Остановить караван |
| `/caravan return <id>` | Отправить караван домой |
| `/caravan reload` | Перезагрузить конфиги |

## Админ-команды

| Команда | Описание |
|---|---|
| `/caravan admin list <player>` | Показать караваны игрока |
| `/caravan admin info <id>` | Показать любой караван |
| `/caravan admin open <id>` | Открыть инвентарь каравана |
| `/caravan admin setup <id>` | Открыть GUI настройки владельца |
| `/caravan admin trades <id>` | Открыть GUI управления торговлей |
| `/caravan admin sell <id> <slot> <price>` | Создать SELL-операцию |
| `/caravan admin buy <id> <material> <amount> <price> <max>` | Создать BUY-операцию |
| `/caravan admin spawn <id> [player]` | Призвать физический караван |
| `/caravan admin despawn <id>` | Убрать физическую проекцию |
| `/caravan admin move <id> <world> <x> <z>` | Задать перемещение |
| `/caravan admin position <id>` | Показать виртуальную позицию |
| `/caravan admin debug <id>` | Показать расширенную диагностику |
| `/caravan admin routeinfo <id>` | Показать остановки маршрута |
| `/caravan admin routeclear <id>` | Очистить маршрут |
| `/caravan admin return <id>` | Отправить караван домой |
| `/caravan admin delete <id>` | Удалить любой караван |
| `/caravan admin reload` | Перезагрузить конфиги |
| `/caravan admin givelicense <player> [amount]` | Выдать лицензию |

## Permissions

| Permission | Назначение | По умолчанию |
|---|---|---|
| `meltarion.caravans.use` | Базовые команды игроков | `true` |
| `meltarion.caravans.reload` | `/caravan reload` | `op` |
| `meltarion.caravans.admin` | Все админ-команды и обходы | `op` |
| `meltarion.caravans.limit.2` | Повышает лимит караванов до 2 | `false` |
| `meltarion.caravans.limit.3` | Повышает лимит караванов до 3 | `false` |

## Примеры использования

### Выдать лицензию

```text
/caravan admin givelicense Steve 3
```

### Создать караван с именем

```text
/caravan create Северный Караван
```

### Посмотреть короткие ID

```text
/caravan list
```

### Запустить маршрут домой

```text
/caravan return 1a2b3c4d
```

## Как работают limit permissions

Плагин проверяет:

- `default-caravan-limit`
- затем все узлы из `permission-limits`

Если у игрока несколько прав, используется **наибольшее значение**.

Пример:

- есть `meltarion.caravans.limit.2`
- есть `meltarion.caravans.limit.3`

Итоговый лимит будет `3`.
