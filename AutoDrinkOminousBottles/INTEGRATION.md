# Reality's Minecraft mods integration

This folder contains the upstream **Auto-Drink Ominous Bottles** Fabric project,
pinned to upstream commit
[`76f6ba0ea5b6d43149252b2f698544037bd4614f`](https://github.com/shumsic/auto-drink-ominous-bottles/commit/76f6ba0ea5b6d43149252b2f698544037bd4614f).

## Current supported build

- Minecraft Java Edition: **26.1.2 only**
- Loader: Fabric Loader 0.19.3 or newer
- Fabric API: 0.150.0+26.1.2
- Java: 25
- Build: `./gradlew test build`

This is the current upstream target. Minecraft 1.21.x, 26.0, 26.2, and 26.3
are **not included or verified in this imported source snapshot**. They need
separate compatibility work and must not be treated as supported yet.

## What the mod does

The client watches the visible raid boss bar for the raid-victory state. When
detected, it drinks one Ominous Bottle, prioritizing the highest Omen level
available in the hotbar; it can pull a bottle from the main inventory when the
hotbar is empty. The feature can be toggled with the default **R** keybind.
The raid boss bar must be visible.

## Source and license

Upstream project: https://github.com/shumsic/auto-drink-ominous-bottles

The upstream MIT license is preserved in this folder's `LICENSE`. This
subproject is separate from the root repository's CC0-licensed projects.
