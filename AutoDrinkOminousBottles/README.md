# Auto-Drink Ominous Bottles

A client-side mod that drinks an Ominous Bottle for you the moment a raid is won, so you can chain raids
without stopping.

- When the raid boss bar turns to **Raid - Victory**, it drinks one Ominous Bottle.
- It picks the highest Bad Omen level in your hotbar. If the hotbar has none, it pulls the best one from the
  rest of your inventory into an empty hotbar slot first.
- It switches back to the slot you were holding when it's done.
- If a menu or chat is open when the raid ends, it waits and drinks as soon as you close it.
- If you have no bottles, it tells you above the hotbar.

Press **K** to turn it on or off. You can rebind it under Options, Controls, Key Binds, Miscellaneous. It's off
until you turn it on, and it remembers your choice in `config/auto_drink_ominous_bottles.properties`.

It only runs on your game, so servers don't need it. Raid boss bars have to be showing on your screen, because
that's how it knows the raid was won.

## Versions

One jar per Minecraft version and loader:

| Loader | Minecraft |
|---|---|
| Fabric | every version from 1.21 to 26.3 |
| Forge | 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.8, 1.21.11, 26.1.2, 26.2, 26.3 |
| NeoForge | 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.8, 1.21.11, 26.1.2, 26.2, 26.3 |

Fabric needs Fabric API. Forge and NeoForge need nothing extra.

## How the code is laid out

- `src/core` is the logic, the same for every version: when to drink, which bottle, putting your slot back, and
  the on/off setting. It only talks to the game through the small `Game` interface.
- `src/era-*` fills in `Game` for each era of Minecraft's code, plus the toggle key:
  - `era-121` is 1.21 and 1.21.1.
  - `era-1212` is 1.21.2 to 1.21.4.
  - `era-1215` is 1.21.5 to 1.21.8.
  - `era-1219` is 1.21.9 to 1.21.11.
  - `era-261` is 26.1.
  - `era-262` is 26.2 and 26.3.
- `src/hook-*` is each loader's way of registering the key and running every tick.
- `src/res-*` is each loader's mod info file, and `src/common` the language file.
- `versions/<minecraft>-<loader>/gradle.properties` picks the era, hook and resources for one jar.

## Building

You need Java 25 and Gradle 9.6 or newer. Each version builds on its own:

    ADOB_TARGETS=26.3-fabric gradle :26.3-fabric:build

`build-all.sh` builds every version you name, one at a time, and `package.py` collects the jars into `pack/` with
a README listing which jar is for which version.

`tests.sh` runs the logic tests, which need no Minecraft.

`upload_modrinth.py` uploads every jar in the pack to a Modrinth project, each tagged with its loader and
Minecraft versions. `package.py` puts it in the pack with a `manifest.json` it reads. Run it from the unzipped
pack with `--dry-run` first. It needs your Modrinth token in the `MODRINTH_TOKEN` environment variable.

## Credits and license

The original Fabric mod is by shumsic: https://github.com/shumsic/auto-drink-ominous-bottles. This version was
rebuilt for Fabric, Forge and NeoForge across every version by Reality Studios.

MIT, the same as the original. See LICENSE.
