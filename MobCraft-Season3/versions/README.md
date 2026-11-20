# MobCraft Season 3 for older Minecraft versions

The folder above this one is the main source, for Minecraft 26.3. Each folder here is the same
beta for another Minecraft version, with the changes that version needs. Each one builds on its
own with `./gradlew build`, and the jar lands in its `build/libs` folder.

| Folder | Minecraft |
|---|---|
| mc26.2 | 26.2 |
| mc26.1.2 | 26.1.2 |
| mc1.21.11 to mc1.21.4 | 1.21.11, 1.21.10, 1.21.9, 1.21.8, 1.21.7, 1.21.6, 1.21.5 and 1.21.4 |

The 1.21 versions are made from the 26.3 source by the scripts in `port-scripts`. Each
`make_<version>.sh` copies the next newer version and applies that version's edits, so 1.21.4
is made from 1.21.5, which is made from 1.21.6, and so on. The scripts were written for the
build machine, so the folder paths at the top of each one need changing before you run them.

1.21.4 is the oldest version MobCraft supports.
