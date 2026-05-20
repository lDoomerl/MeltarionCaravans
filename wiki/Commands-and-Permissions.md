# Команды и права

## Команды игроков

| Команда | Описание |
|---|---|
| `/caravan help` | Показать справку |
| `/caravan create [name]` | Создать караван |
| `/caravan list` | Показать свои караваны |
| `/caravan info <identifier>` | Показать информацию о своём караване |
| `/caravan rename <identifier> <new name>` | Переименовать свой караван |
| `/caravan delete <identifier>` | Удалить свой караван |
| `/caravan spawn <identifier>` | Призвать физический караван |
| `/caravan move <identifier> <x> <z>` | Отправить караван в точку |
| `/caravan stop <identifier>` | Остановить караван |
| `/caravan return <identifier>` | Отправить караван домой |
| `/caravan reload` | Перезагрузить конфиги |

## Админ-команды

| Команда | Описание |
|---|---|
| `/caravan admin list <player>` | Показать караваны игрока |
| `/caravan admin info <identifier>` | Показать любой караван |
| `/caravan admin open <identifier>` | Открыть инвентарь каравана |
| `/caravan admin setup <identifier>` | Открыть GUI настройки владельца |
| `/caravan admin trades <identifier>` | Открыть GUI управления торговлей |
| `/caravan admin sell <identifier> <slot> <price>` | Создать SELL-операцию |
| `/caravan admin buy <identifier> <material> <amount> <price> <max>` | Создать BUY-операцию |
| `/caravan admin spawn <identifier> [player]` | Призвать физический караван |
| `/caravan admin despawn <identifier>` | Убрать физическую проекцию |
| `/caravan admin move <identifier> <world> <x> <z>` | Задать перемещение |
| `/caravan admin position <identifier>` | Показать виртуальную позицию |
| `/caravan admin debug <identifier>` | Показать расширенную диагностику |
| `/caravan admin routeinfo <identifier>` | Показать остановки маршрута |
| `/caravan admin routeclear <identifier>` | Очистить маршрут |
| `/caravan admin return <identifier>` | Отправить караван домой |
| `/caravan admin delete <identifier>` | Удалить любой караван |
| `/caravan admin reload` | Перезагрузить конфиги |
| `/caravan admin givelicense <player> [amount]` | Выдать лицензию |

## Что такое идентификатор каравана

Под `<identifier>` плагин понимает один из следующих вариантов:

- номер каравана из `/caravan list`;
- имя каравана;
- short ID;
- полный UUID.

Для админ-команд дополнительно поддерживаются:

- `owner:index`
- `owner:name`

Примеры:

```text
/caravan info 1
/caravan spawn 2
/caravan rename 1 Severnyy_karavan
/caravan admin debug Anton:1
/caravan admin info Anton:Severnyy_karavan
```

Приоритет для обычных команд игрока:

1. Полный UUID
2. Short UUID
3. Номер из списка игрока
4. Точное имя каравана без учёта регистра

Если имя оказалось неоднозначным, используйте номер из `/caravan list` или short ID.

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
/caravan create Severnyy_Karavan
```

### Посмотреть номера и short ID

```text
/caravan list
```

### Открыть караван по номеру из списка

```text
/caravan info 1
/caravan spawn 2
```

### Открыть караван по owner:index

```text
/caravan admin debug Anton:1
```

### Открыть караван по owner:name

```text
/caravan admin info Anton:Severnyy_karavan
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
