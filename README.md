# MeltarionCaravans

`MeltarionCaravans` is a Paper plugin for persistent caravans with storage, public trading, virtual movement, Towny-aware route stops, and physical in-world projection.

## Requirements

- Java `21`
- Paper or Leaf `1.21.x`

## Optional Integrations

- `Towny`
  Used for own-town spawn restrictions, Shop Plot checks, and route town selection.
- `Dynmap`
  Used for optional caravan markers.
- `PlaceholderAPI`
  Used for optional read-only player placeholders based on the MeltarionCaravans public API.

The plugin starts without Dynmap or PlaceholderAPI. Towny is optional overall, but Towny-dependent mechanics such as physical spawn restrictions and route town selection require it.

## Public API

The plugin now exposes a read-only Bukkit `ServicesManager` API for external plugins.

Service interface:

```java
net.meltarion.caravans.api.MeltarionCaravansApi
```

Lookup example:

```java
RegisteredServiceProvider<MeltarionCaravansApi> provider =
    Bukkit.getServicesManager().getRegistration(MeltarionCaravansApi.class);

if (provider != null) {
    MeltarionCaravansApi api = provider.getProvider();
    if (api.isCaravanPluginReady()) {
        List<CaravanSummary> caravans = api.getCaravansByOwner(playerUuid);
    }
}
```

Available methods:

- `getCaravansByOwner(UUID ownerUuid)`
- `getCaravan(UUID caravanId)`
- `getCaravanCount(UUID ownerUuid)`
- `getCaravanLimit(UUID ownerUuid)`
- `isCaravanPluginReady()`

`CaravanSummary` is a safe immutable DTO with read-only metadata only:

- IDs and owner info
- status and HP
- virtual position and target
- ETA and route flags
- active SELL/BUY offer counts
- update timestamp

Not included:

- PlaceholderAPI hooks
- HTTP endpoints
- write/control API
- caravan inventories or item stacks

## PlaceholderAPI

If PlaceholderAPI is installed and `placeholderapi.enabled: true`, MeltarionCaravans registers an internal expansion with the identifier `meltarioncaravans`.

Examples:

- `%meltarioncaravans_count%`
- `%meltarioncaravans_limit%`
- `%meltarioncaravans_first_name%`
- `%meltarioncaravans_1_status%`
- `%meltarioncaravans_1_eta%`

Configuration:

```yml
placeholderapi:
  enabled: true
  empty-value: ""
  eta-format: "mm:ss"
  hp-decimals: 0
```

If PlaceholderAPI is missing, the plugin still starts normally and only logs an informational message.

## Installation

1. Download the latest release jar from the repository Releases page.
2. Place the jar into your server `plugins/` folder.
3. Start the server once to generate:
   - `config.yml`
   - `lang.yml`
   - `gui.yml`
4. Stop the server and adjust configuration if needed.
5. Start the server again.

## Configuration Files

- `config.yml`
  Gameplay, system, movement, route, projection, integration, and license settings.
- `lang.yml`
  Player/admin messages, help text, debug output, and notifications.
- `gui.yml`
  GUI titles, button labels, lore, and icon materials.

The plugin includes a safe resource update system:

- New keys are merged into existing files automatically.
- Existing admin-defined values are preserved.
- Timestamped backups are created before file upgrades.
- `/caravan reload` re-checks and reloads all three resource files.

## Commands

### Player

- `/caravan help`
- `/caravan create [name]`
- `/caravan list`
- `/caravan info <id>`
- `/caravan rename <id> <new name>`
- `/caravan delete <id>`
- `/caravan spawn <id>`
- `/caravan move <id> <x> <z>`
- `/caravan stop <id>`
- `/caravan return <id>`
- `/caravan reload`

### Admin

- `/caravan admin list <player>`
- `/caravan admin info <id>`
- `/caravan admin open <id>`
- `/caravan admin setup <id>`
- `/caravan admin trades <id>`
- `/caravan admin sell <id> <slot> <price>`
- `/caravan admin buy <id> <material> <amount-per-transaction> <price> <max-total>`
- `/caravan admin spawn <id> [player]`
- `/caravan admin despawn <id>`
- `/caravan admin move <id> <world> <x> <z>`
- `/caravan admin position <id>`
- `/caravan admin debug <id>`
- `/caravan admin routeinfo <id>`
- `/caravan admin routeclear <id>`
- `/caravan admin return <id>`
- `/caravan admin delete <id>`
- `/caravan admin reload`
- `/caravan admin givelicense <player> [amount]`

## Permissions

- `meltarion.caravans.use`
  Basic caravan usage commands.
- `meltarion.caravans.reload`
  Allows `/caravan reload`.
- `meltarion.caravans.admin`
  Administrative caravan commands and bypasses.
- `meltarion.caravans.limit.2`
  Raises caravan limit to `2`.
- `meltarion.caravans.limit.3`
  Raises caravan limit to `3`.

## Build

```bash
./gradlew clean build
```

The distributable plugin jar is generated in `build/libs/`.

## GitHub Actions

- `build.yml`
  Runs on `push` and `pull_request`, builds with Java 21, and uploads the plugin jar as an artifact.
- `release.yml`
  Runs on tags matching `v*.*.*`, builds the plugin, and attaches the jar to the GitHub Release.

## Update Notes

- Keep your existing configuration files; do not delete them for normal upgrades.
- On startup and reload, the plugin checks `config.yml`, `lang.yml`, and `gui.yml` versions and merges new defaults safely.
- If a resource file is invalid YAML, the plugin backs it up and regenerates a fresh default copy.
- Legacy compatibility fallbacks remain in place for older message and HP keys where applicable.

## Development Notes

- Main plugin class: `net.meltarion.caravans.MeltarionCaravansPlugin`
- Public API: `net.meltarion.caravans.api.MeltarionCaravansApi`
- API target: Paper `1.21`
- Java toolchain: `21`

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for release notes and repository history summaries.
