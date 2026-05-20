# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project follows Semantic Versioning for tagged releases.

## [Unreleased]

### Added
- GitHub Actions build workflow with artifact upload.
- Tag-based GitHub release workflow.
- Issue templates for bugs, features, and compatibility reports.

### Changed
- Repository documentation expanded for installation, configuration, commands, and updates.

## [0.2.0-alpha] - 2026-05-20

### Added
- Read-only Bukkit API through `ServicesManager`.
- `CaravanSummary` DTO for safe read-only external integrations.
- Optional PlaceholderAPI integration.
- Indexed caravan placeholders for player dashboards and scoreboards.
- Russian GitHub Wiki documentation.
- User-friendly caravan identifiers for players and admins.

### Changed
- `config.yml` `config-version` bumped to `2`.
- Improved caravan identifier resolution system.
- `config.yml` / `lang.yml` / `gui.yml` split finalized.

### Notes
- HTTP API endpoints are not included yet.
- DoomerAPI bridge integration is planned separately.
- This is still alpha-stage software.

## [0.1.0] - 2026-05-20

### Added
- Initial Paper plugin foundation for persistent caravans.
- SQLite-backed caravan, inventory, trade, movement, and route systems.
- Split `config.yml`, `lang.yml`, and `gui.yml` defaults.
- Russian-localized default resources.
