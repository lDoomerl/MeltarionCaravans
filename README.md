# MeltarionCaravans

`MeltarionCaravans` is a Paper plugin skeleton for a future caravan trading system on a Towny server.

## Current scope

- Paper/Leaf `1.21.x`
- Java `21`
- Config-driven currency item for MVP
- Service-oriented plugin core
- SQLite-backed caravan persistence
- `/caravan` root command with base subcommands
- No GUI, Towny, or Dynmap integration yet

## Development

```bash
./gradlew build
```

The plugin jar will be generated in `build/libs/`.

## Roadmap

- Towny-aware caravan ownership and territory rules
- Dynmap or LiveAtlas markers
- Virtual movement and physical caravan entities
- GUI management flows
