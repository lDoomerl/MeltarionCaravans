# API

## Текущее состояние API

Начиная с текущей версии плагин предоставляет **read-only Bukkit API** через `ServicesManager`.

Публичный интерфейс:

```java
net.meltarion.caravans.api.MeltarionCaravansApi
```

Важно:

- API только для чтения;
- API регистрируется через Bukkit `ServicesManager`;
- HTTP endpoint в MeltarionCaravans **не входит**;
- PlaceholderAPI в этой задаче **не добавляется**.

## Как получить API из другого плагина

Пример:

```java
RegisteredServiceProvider<MeltarionCaravansApi> provider =
    Bukkit.getServicesManager().getRegistration(MeltarionCaravansApi.class);

if (provider == null) {
    return;
}

MeltarionCaravansApi api = provider.getProvider();
if (!api.isCaravanPluginReady()) {
    return;
}

List<CaravanSummary> caravans = api.getCaravansByOwner(playerUuid);
```

## Методы MeltarionCaravansApi

### `List<CaravanSummary> getCaravansByOwner(UUID ownerUuid)`

Возвращает все караваны игрока в безопасном read-only виде.

### `Optional<CaravanSummary> getCaravan(UUID caravanId)`

Возвращает один караван по полному UUID.

### `int getCaravanCount(UUID ownerUuid)`

Возвращает количество караванов игрока.

### `int getCaravanLimit(UUID ownerUuid)`

Возвращает лимит караванов для игрока.

Примечание:

- если владелец онлайн, используется его актуальный permission-based лимит;
- если владелец офлайн, API возвращает безопасный fallback из `default-caravan-limit`.

### `boolean isCaravanPluginReady()`

Показывает, что плагин полностью инициализирован и сервис готов к использованию.

## Что содержит CaravanSummary

`CaravanSummary` — immutable DTO, безопасный для сериализации в JSON и для передачи во внешний API.

Поля:

- `id`
- `shortId`
- `ownerUuid`
- `ownerName`
- `name`
- `status`
- `hp`
- `maxHp`
- `worldName`
- `virtualX`
- `virtualY`
- `virtualZ`
- `targetWorldName`
- `targetX`
- `targetY`
- `targetZ`
- `etaSeconds`
- `routeRunning`
- `currentRouteStopIndex`
- `totalRouteStops`
- `routeLoopEnabled`
- `physicalSpawned`
- `activeSellOffers`
- `activeBuyOrders`
- `updatedAt`

В DTO **не попадают**:

- `Inventory`
- `ItemStack`
- содержимое склада каравана
- write-методы управления караваном

## Путь интеграции с DoomerAPI

Текущий API специально сделан как база для будущей интеграции с DoomerAPI:

- получить список караванов игрока;
- отдать их в личный кабинет;
- показать read-only summary:
  - имя;
  - статус;
  - HP;
  - позицию;
  - маршрут;
  - ETA;
  - количество активных SELL/BUY-операций.

Планируется дальше:

- адаптер MeltarionCaravans → DoomerAPI;
- read-only summary endpoint уже на стороне DoomerAPI;
- отдельный слой совместимости версий.

## Что не входит в этот API

- HTTP endpoints
- PlaceholderAPI
- write/control API
- управление маршрутами извне
- внешнее изменение торговли
