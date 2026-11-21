# MobCraft Season 3 for NeoForge

MobCraft Season 3 Beta 5.16, made to run on NeoForge. It plays the same as the Fabric version in
[MobCraft-Season3](../MobCraft-Season3): the same mobs, abilities, items, RTS mode and commands.
Players need NeoForge, not Fabric API.

## Versions

Each folder in `versions` is one Minecraft version. Each builds on its own with `./gradlew build`,
and the jar lands in its `build/libs` folder.

| Folder | Minecraft | NeoForge it was tested on |
|---|---|---|
| mc1.21.4 | 1.21.4 | 21.4.161 |
| mc1.21.5 | 1.21.5 | 21.5.101 |
| mc1.21.6 | 1.21.6 | 21.6.20-beta |
| mc1.21.7 | 1.21.7 | 21.7.25-beta |
| mc1.21.8 | 1.21.8 | 21.8.57 |
| mc1.21.9 | 1.21.9 | 21.9.16-beta |
| mc1.21.10 | 1.21.10 | 21.10.64 |
| mc1.21.11 | 1.21.11 | 21.11.48 |
| mc26.1-26.1.2 | 26.1, 26.1.1 and 26.1.2, one jar for all three | 26.1.0.19-beta, 26.1.1.15-beta, 26.1.2.128 |
| mc26.2 | 26.2 | 26.2.0.95 |
| mc26.3 | 26.3 | 26.3.0.115 |

On a server, set `allow-flight=true` in server.properties. Without it, the server kicks players using the
flying and burrowing mobs for "floating". MobCraft's own anti-cheat still catches fake flying.

## How it works

MobCraft's own code is the same as the Fabric version's. The `com.reality.mobcraft.neo` package is a small
stand-in for the parts of Fabric API that MobCraft uses: its events, networking, data attachments, key
binds, HUD layers and render layers. Each one is answered by NeoForge's own events, so the rest of the
code doesn't need to know which loader it's on. `MobCraftNeo` is the NeoForge entry point.

## Port scripts

`port-scripts/make_neo.sh` builds a NeoForge copy from a Fabric version's source:

```
OVER="1216 1217 1219 12110 12111" bash port-scripts/make_neo.sh 26.3 <fabric source folder> 26.3.0.115
```

`base` is the NeoForge layer every version gets. Each `over<version>` folder holds what changed from that
Minecraft version on, and `to26.py`, `to262.py` and `to263.py` rename things for the 26.x versions. The
scripts were written for the build machine, so the folder paths at the top of `make_neo.sh` need changing
before you run them.

## License

CC0 1.0, the same as the Fabric version. See LICENSE.
