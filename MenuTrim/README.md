# Menu Trim

A small client mod that hides the Essential menu buttons you don't use, and keeps the ones you do.

- Hides Wardrobe, Pictures, Account, the Beta badge, the player model, the sale banner and the NEW badge.
- Keeps Social (friends), Messages, Host, Essential Settings and the Update button.
- The Menu button at the top left of the title screen and pause menu lets you show or hide each one.
- Essential Extras turns off cosmetics, emotes, the Essential icons and the screenshot pop-up, using Essential's own settings.

It never changes Essential's code or files. For every button it draws, Essential also puts a plain,
invisible game button on the screen with an id like `wardrobe`. That's how menu mods like FancyMenu
move Essential's buttons. Menu Trim hides those plain buttons, and Essential hides its own to match.
Hiding needs Essential 1.3.9 or newer.

## Versions

One jar per Minecraft version and loader, for every version Essential supports:

| Loader | Minecraft |
|---|---|
| Fabric | 1.16.5 to 26.3 |
| Forge | 1.8.9, 1.12.2, 1.16.5 to 26.3 |
| NeoForge | 1.20.4 to 26.3 |

## How the code is laid out

- `src/core` is the logic, the same for every version. It only uses plain Java and reflection, so it
  compiles unchanged from 1.8.9 to 26.x.
- `src/era-*` is the settings screen and button helpers for each era of Minecraft's menu code.
- `src/hook-*` is each loader's way of finding the title screen and pause menu.
- `src/res-*` is each loader's mod info file.
- `versions/<minecraft>-<loader>/gradle.properties` picks the era, hook and resources for one jar.

## Building

You need Java 25 and Gradle 9.6 or newer. Each version builds on its own:

    MENUTRIM_TARGETS=1.21.11-fabric gradle :1.21.11-fabric:build

`build-all.sh` builds every version in `versions/` one at a time, and `package.py` collects the jars
into `pack/` with a README listing which jar is for which version.

The build uses Essential's fork of Architectury Loom and its toolkit's version defaults, from
https://repo.essential.gg.

## License

CC0 1.0, see LICENSE. Menu Trim is in the public domain: anyone can use, change, share or build on
it, for anything, with no need to ask or give credit. Improvements are welcome.

Menu Trim is not made by or affiliated with Essential or Spark Universe.
