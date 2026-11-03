# MobCraft Season 3 (alpha)

Minecraft 26.3, Fabric. Every player needs this mod installed on their own game.

## License

CC0 1.0, see LICENSE. MobCraft is in the public domain: anyone can use, change,
share or build on it, for anything, with no need to ask or give credit.
Improvements are welcome.

## What's in this build

- **Mob roles.** /setmob gives a player a mob. Their mob, energy and curse are
  saved with the world and kept through death.
- **Energy.** Each mob has a max and a regen rate. The energy bar shows the
  mob name, energy and curse timer. Press H to drag it anywhere on screen.
- **Curses.** /curse and /uncurse. Each mob's kit decides what its curse does.
- **Ability keys.** R, G and V by default, rebindable under MobCraft in
  Controls. Sneak while pressing for alternate versions.
- **Death drops.** When a player with a mob dies, their mob's drops land where
  they died.
- **Mob Item Menu.** An M button sits to the right of the recipe book button
  in the survival inventory and follows it when the recipe book opens. The
  menu shows your current item, a Switch Item button to cycle through your own
  mob's items, an amount slider from 1 to 64, Give Items, and a Drop Items
  ON/OFF toggle. With it on, Give Items throws the items on the ground in front
  of you, like pressing Q, so they can land in a hopper or auto crafter.
- **Curses** lift when /setmob switches you to a different mob.
- **MobCraft Settings.** /mobcraftsettings opens a screen with an ON/OFF
  toggle for each of your mob's abilities. Saved through death, reset when you
  get a new mob.
- **Mob Behavior.** The Mob Behavior... button in /mobcraftsettings opens a
  second page with Follow Me, Defend Me and Attack What I Hit, each ON or OFF.
  They apply to every mob you own. A mob you give an RTS order holds that spot
  for 60 seconds before following again.
- **Dual hitbox.** Players under 1 block tall keep their small collision box,
  but every attack hits a normal-size box. F3+B shows it in green.

- **RTS mode, layer 1.** Press J for a top-down view over your position. You
  can still be hurt, and a hit closes RTS and puts back the view you had,
  like Freecam. WASD or the screen edges pan in the
  direction you point on screen, scroll zooms. Your mobs glow green, selected ones
  yellow. Click a mob or drag a box to select (shift adds), click a block to
  send selected mobs there, marked with particles only you see. J or Escape
  goes back.
- **Divisions and army groups.** In RTS, Ctrl+1-9 makes your selection a
  division, 1-9 selects it, pressing twice jumps the camera to it. The bottom
  bar shows divisions 1-9 with live counts and Army Groups I-III. Select some
  divisions, then press Set next to an army group to put them in it. Groups
  are saved on the server. A division sits in one army group at a time. Mobs
  in Army Group I glow aqua, II gold and III pink, and the buttons use the
  same colors.
- **Mob kits.** Rabbit, Iron Golem, Skeleton, Axolotl, Piglin, Bat, Ghast,
  Evoker, Warden, Vex, Shulker, Creeper, Silverfish, Enderman, Wither, Illusioner and Blaze have their abilities and passives from the finished sheets. Each kit's
  class in mob/kit lists exactly what it does at the top.

  | Mob | R | G | Other |
  |---|---|---|---|
  | Rabbit | Escorts, holding a rabbit's foot | | left-click with the foot also calls escorts. Jump in midair to double jump, 3 energy. No fall damage |
  | Iron Golem | Golem Toss | | right-click an iron ingot for Iron Repair |
  | Skeleton | Lock On | Skeleton Horse | |
  | Axolotl | Play Dead | Call the Pod | |
  | Piglin | Gold Rush | Crossbow | |
  | Bat | Confuse | Echolocation | |
  | Ghast | Fireball | Barrage | teammates right-click you to ride, up to 4 |
  | Evoker | Fang line, sneak for a ring | Summon Vexes | needs a totem in hand for fangs |
  | Warden | Sonic Shriek, sneak for Rage | Echolocation, sneak for Sniffing | V: Burrow, sneak for Sculk Teleport |
  | Vex | Phase on/off | Charge | |
  | Shulker | Blocking | Teleport | V: Shulker Bullet, sneak for Elytra Gamble. In RTS with nothing selected, click a shulker or a spot in an End City to teleport |
  | Creeper | Gunpowder Blast | Self Charge | V: Leaf Hide |
  | Silverfish | Stone Swim, sneak for Infest | Summon Silverfish | V: Curse Attack |
  | Enderman | Teleport | | Auto Dodge is passive |
  | Wither | Black Skull, sneak for Blue Skull | Charge | V: Block Destruction |
  | Illusioner | Clones | Arrow Burst | V: Blindness |
  | Blaze | Fireballs | Fire Shield | |

- **Armor upgrades.** Mob drops that attach to armor. Hold the helmet or
  chestplate in your main hand and the upgrade in your off hand, then
  right-click. The items have textures. They don't show on the armor yet.

  | Upgrade | Comes from | Goes on | Power |
  |---|---|---|---|
  | Left or Right Bat Ear | the Bat dying, one of them | helmet | one: echolocation. Both: also, underground, everything within 16 blocks glows |
  | Left or Right Warden Tendril | a player killing the Warden, one of them | helmet | one: echolocation. Both: also sniffing, and wardens glow |
  | Ender Pearl Shard | the Enderman dying | chestplate | Z: random teleport, 10s cooldown |
  | Stalker Shard | crafted: Ender Pearl Shard + Creaking Splinter (creakings drop it) | helmet or chestplate | Z: teleport behind an enemy who isn't looking at you, 20s cooldown |

- **Warden tendril textures.** The two tendril items use the exact pixels of the
  vanilla warden's tendrils, cut straight out of the game's own warden texture
  (assets/minecraft/atlases). Nothing is copied, so resource packs that change
  the warden change these too. If that cut ever fails, the hand-drawn tendril
  textures in textures/item show instead.
- **Client-side glowing.** The server tells one player's game to outline an
  entity in a color. Nobody else sees it. This is the base for RTS selection
  glow, the Enderman's attacker glow and similar effects.
- **Warden ghost armor stands.** Invisible glowing armor stands that exist only
  on the Warden's game. Echolocation shows white ghosts where each enemy was
  1 second ago, and sneaking hides them. Sniffing shows red ghosts where they
  were 10 seconds ago, and sneaking doesn't hide those.
- **Teams.** /teams lists every team by its chosen name, in its color, with
  its player count and members. /myteam shows your own team. /myteam name
  renames it to any words, and /myteam color sets one of the 16 chat colors.
  If the team's king is online, only the king can change it.
  Players make their own teams with /myteam create, bring others in with
  /myteam invite and accept, and go with /myteam leave. The leader or king
  can /myteam kick.
- **Mob Guide.** A Mob Guide button at the top right of the title screen and
  the pause menu opens a book of every mob: passives, abilities with their
  keys, home, allies, curse and ultimate. It opens on your own mob. The text
  is in assets/mobcraft/mob_guide.txt.
- **Ultimates.** Every mob has one on U, charged only by fighting other players. Each kit's
  onUltimate describes its own. Nothing charges the bar for 10 seconds after
  an ultimate fires.
- **Wither death.** When a player kills the Wither, a nether star rises from
  the body, bursts in light and splinters into 2 Star Fragments.
- **Advancement tabs.** Everyone gets the vanilla tab roots on join, so every
  tab shows from the start.

## Build

You need Java 25. Open the folder in IntelliJ as a Gradle project and let it
import, or just run this in the folder:

    ./gradlew build

On Windows it's `gradlew build`. The jar lands in build/libs.

/shutdownjeff has no password built in. On a server, set one in
config/mobcraft-jeff.txt with `password=...`. On the next start it's swapped
for its SHA-256 scramble, so the real password isn't left in the file.

## Test

    /setmob @s rabbit          you become the Rabbit, HUD appears top left
    /mobenergy @s 20           set your energy to 20, watch it refill
    /curse @s 30               cursed for 30 seconds, bar turns purple
    /uncurse @s                lift it early
    /setmob @s none            remove your mob
    press R, G or V            "No ability on that key yet" until kits exist
    press H                    click and drag the energy bar, Done saves it
    open your inventory        M button beside the recipe book, gives your mob's items
    /mobcraftsettings          switch your mob's abilities on or off
    /rtstest claim 10          make mobs within 10 blocks yours, then press J
    /rtstest release           give them back
    /scaletest 0.4             shrink yourself, then F3+B for the green box

    /glowtest @e[type=cow,distance=..20] red        cows glow red on your screen only
    /glowtest @e[type=cow,distance=..20] aqua 10    aqua for 10 seconds
    /glowtest clear @e[type=cow,distance=..20]      stop


Colors: white, red, green, blue, yellow, purple, aqua, orange.

## Vanilla source dump

To check method names against real 26.3 code:

    gradlew genSources
    gradlew dumpVanillaSource

Send build/vanilla-src.zip back.
