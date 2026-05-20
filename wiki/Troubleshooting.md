# Решение проблем

## Частые проблемы

### GitHub Actions: `./gradlew Permission denied`

Причина:

- у `gradlew` нет исполняемого бита в git.

В текущем репозитории это уже исправлено, но если проблема вернётся:

```bash
git update-index --chmod=+x gradlew
git commit -m "Fix gradlew executable bit"
```

### Towny missing

Симптомы:

- нельзя спавнить караван по обычной команде;
- нельзя использовать маршруты с выбором Towny городов;
- публичная торговля по Shop Plot не работает корректно.

Проверьте:

- установлен ли Towny;
- загружен ли он без ошибок;
- есть ли он в `plugins/`.

### No Shop Plot available

Причины:

- в выбранном городе нет Shop Plot;
- все города отфильтрованы правилами маршрута;
- Towny не может корректно определить доступные точки.

Проверьте:

- Towny town blocks;
- типы plot;
- настройки `route.allow-*`.

### Player not found for givelicense

Команда:

```text
/caravan admin givelicense <player> [amount]
```

Ищет онлайн-игрока через `Bukkit.getPlayerExact`. Если игрок офлайн или ник указан не точно, выдача не сработает.

### Caravan does not spawn

Проверьте:

- есть ли Towny;
- стоите ли вы в своём городе;
- включён ли `physical-caravan.enabled`;
- не заспавнен ли уже этот караван;
- есть ли у каравана позиция/дом;
- нет ли ошибок storage в `latest.log`.

Если вы указываете караван по имени и команда не срабатывает, попробуйте:

- сначала выполнить `/caravan list`;
- затем использовать номер из списка;
- либо использовать short ID.

### Public trading unavailable

Проверьте:

- стоит ли караван на `Shop Plot`;
- находится ли он в `STOPPED`;
- есть ли активные SELL или BUY операции;
- не находится ли он в `ATTACKED`.

### Неоднозначное имя каравана

Если у команды появляется ошибка про неоднозначное имя:

- выполните `/caravan list`;
- возьмите номер слева, например `1` или `2`;
- либо используйте short ID в квадратных скобках.

### Dynmap marker missing

Проверьте:

- `dynmap.enabled: true`
- установлен ли Dynmap;
- активен ли караван;
- есть ли у него виртуальная позиция;
- нет ли ошибок reflection/integration в логе.

### Deprecated BukkitObjectOutputStream warning

Это связано с сериализацией `ItemStack[]` для SQLite и обычно не ломает работу плагина. Проверять стоит:

- нет ли реальных stacktrace;
- сохраняются ли инвентари после перезапуска.

## Что смотреть в latest.log

Полезно искать:

- ошибки загрузки YAML;
- ошибки SQLite;
- Towny/Dynmap warnings;
- сообщения resource updater;
- ошибки открытия GUI;
- ошибки сохранения inventory/trade/movement state.

## Какие debug-команды использовать

### Глубокая диагностика

```text
/caravan admin debug <identifier>
```

Показывает:

- статус;
- виртуальную позицию;
- цель;
- ETA;
- состояние физической проекции;
- UUID сущностей;
- home position;
- число активных сделок;
- наличие строки инвентаря в БД.

### Позиция

```text
/caravan admin position <identifier>
```

### Маршрут

```text
/caravan admin routeinfo <identifier>
```
