# MeltarionCaravans

`MeltarionCaravans` is a Paper plugin for persistent, route-driven caravans on a Towny server.

## Current scope

- Paper/Leaf `1.21.x`
- Java `21`
- SQLite-backed caravan persistence
- Virtual caravan movement with physical projection
- Public trading on Towny Shop Plots
- Owner/admin caravan setup GUIs
- Optional Dynmap marker integration
- Split runtime configuration files:
  - `config.yml` for gameplay and system settings
  - `lang.yml` for all messages and help text
  - `gui.yml` for GUI titles, buttons, lore, and icon materials

## Development

```bash
./gradlew build
```

The plugin jar will be generated in `build/libs/`.

`/caravan reload` reloads `config.yml`, `lang.yml`, and `gui.yml`.

## Roadmap

- Multi-stop route refinement
- Additional Towny route restrictions and permissions
- Expanded trading rules and economy integration
- More polished map and admin tooling
