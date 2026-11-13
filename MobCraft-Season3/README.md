# MobCraft Season 3 (beta 5.10)

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
- **Pets.** Sneak and right-click a mob of your own kind with an empty hand to
  claim it: a Shulker claims shulkers, a Piglin claims piglins. Variants count
  too: a Skeleton also claims strays, bogged and parched, a Piglin piglin brutes
  and zombified piglins, a Ghast happy ghasts, an Elder Guardian guardians.
  Players without
  a mob can't claim, and bosses and other players' pets can't be claimed.
  Pets follow (Follow Me), teleport to you from over 20 blocks away in the same
  world, defend you and attack what you hit, take RTS orders, never despawn and
  don't burn in sunlight. Sneak + right-click your pet to make it sit and stay;
  an RTS order stands it up. /pets lists your pets and counts, and /pets release
  lets go of the one you're looking at. Switching to another mob frees every
  pet the new mob couldn't claim: a Shulker who becomes a Creeper loses his
  shulkers. Pets in unloaded chunks go wild when they load. They count as
  released, and the pet file says why. Each player has a file in the world
  folder, mobcraft_pets/<uuid>.json, with how many pets they've claimed, lost
  and released, and a line for each pet: type, name, when claimed, kills, and
  when and how it died or was released. It's updated on every change. Mobs
  owned through /rtstest claim or summons aren't counted.
- **Dual hitbox.** Players under 1 block tall keep their small collision box,
  but every attack hits a normal-size box. F3+B shows it in green.

- **RTS mode, layer 1.** Press J for a top-down view over your position. You
  can still be hurt, and a hit closes RTS and puts back the view you had,
  like Freecam. WASD or the screen edges pan in the
  direction you point on screen, scroll zooms. Your mobs glow green, selected ones
  yellow. Click a mob or drag a box to select (shift adds), click a block to
  send selected mobs there, marked with particles only you see. J or Escape
  goes back. Endermen and shulkers teleport where you send them instead of
  walking. Shulkers hop up to 12 blocks a second, and on an attack they hop
  until they're within 16 blocks and can see the target, then fire bullets.
  Their bullets, and a Shulker player's, only hit the enemy they're homing on.
  If that enemy dies first, the bullets still flying go after the next enemy.
  They fly past your other shulkers, your other mobs and you.
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
  | Rabbit | Escorts, holding a rabbit's foot | | left-click with the foot also calls escorts. Jump in midair to double jump, 3 energy, up to 3 times before you land. No fall damage |
  | Iron Golem | Golem Toss | | right-click an iron ingot for Iron Repair |
  | Skeleton | Lock On | Skeleton Horse or Four Horsemen | |
  | Axolotl | Play Dead | Call the Pod | |
  | Piglin | Gold Rush | Crossbow | |
  | Bat | Confuse | Echolocation | |
  | Ghast | Fireball | Barrage | teammates right-click you to ride, up to 4. Your fireball blasting near you launches you, like a rocket jump |
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

- **Mob parts.** Players get parts of their mob, drawn with the real mob's
  own textures, so resource packs change them too. Everyone sees them. See
  client/MobLookLayer.

  | Mob | Part | What it does |
  |---|---|---|
  | Warden | the warden's tendrils on the head | twitch and glow while it senses something. An enemy within 16 blocks moving without sneaking sends a vibration flying to the tendrils, like a real warden's: when it lands they click, twitch and glow, then there's a 2s wait before the next one. They keep going through Echolocation and Sniffing |
  | Creeper | the whole body | swells and flashes white during Gunpowder Blast's 1.5s fuse and the Supernova, and the charged-creeper swirl covers you after Self Charge |
  | Elder Guardian | the guardian's spikes on the back and shoulders | shoot out for 1.5s when you're hit up close, and the attacker takes 1 heart of thorns |
  | Ender Dragon | the dragon's wings on the back | flap while flying, and hang down the back like a folded elytra on the ground. Right-click an elytra to merge it into them: they grow to an elytra's size and she flies faster, until she stops being the Ender Dragon. An elytra is never drawn on her |
  | Bat | the bat's wings on the back and its ears on the head | the wings beat while flying and fold up otherwise |
  | Blaze | the blaze's twelve rods spinning around you in three rings | always lit, and spin faster during Fire Shield and the Combat Phase |
  | Rabbit | the rabbit's ears on the head and its tail | the ears twitch now and then, flop back when running and lie flat while cursed. White fur in snowy biomes, gold in the desert, brown elsewhere |
  | Vex | the vex's wings | always fluttering, beating hard in flight. See-through while phasing, and red glowing eyes during a Charge |
  | Breeze | the breeze's wind swirl around the legs | grows during Whirlwind |
  | Enderman | a purple glow over the whole body | builds up for half a second before Teleport goes, with portal particles, and fades where you land. Auto Dodge stays instant, with a flash where you land |
  | Axolotl | the axolotl's pink gills around the head | fan out and flutter in water or rain, and droop back when dry |
  | Piglin | the piglin's ears on the head | flap as you walk and flop out when you're hit. Zombified ears while cursed |
  | Wither | the wither's two side heads on the shoulders | each turns to look at the closest players or monsters within 20 blocks, and looks around when there's no one. Below half health the wither's blue armor swirls over you. It's only a look |
  | Ghast | wisps of cloud, and a cloud to ride | wisps drift off the body. While flying you stand on a puffy blocky cloud that grows under your feet, bobs and sheds wisps behind you, and shrinks away when you land. In the Nether it's an ash cloud instead: dark smoky gray with glowing embers, shedding smoke, with ash raining from underneath |
  | Skeleton | a leather quiver across the back, with arrows | an arrow for each kind you carry, up to three: feathers in a tipped arrow's potion color, gold for spectral, white for plain. Tipped arrows give off their potion colors from its mouth. The arrows drop in and a lid swings shut while flying with an elytra |
  | Silverfish | the silverfish's body as a tail from the lower back | wiggles like a silverfish, more when you move |
  | Shulker | the shulker's shell | during Blocking, the base appears at your feet and the lid above your head, then the lid comes down and shuts you in. It sits square on the block and doesn't turn as you look around, and you can't move while it's shut. It opens and goes when Blocking ends |

  Some mobs have particles instead of parts. Everyone nearby sees them.

  | Mob | Particles |
  |---|---|
  | Iron Golem | gray-white iron chips fly off every hit, more for harder hits and when worn down |
  | Evoker | spell swirls round the hands while casting Fangs (dusky) or Summon Vexes (pale), like a real evoker. Raid omen swirls while raid captain |
  | Illusioner | a puff of smoke and blue sparkles where you vanish and where each clone appears. Dark spell swirls for Blindness, on you and the target |
  | Villager | an angry storm cloud when hit, green sparkles when giving yourself emeralds |

  Invisible players show none of their mob parts.

  Apart from the Elder Guardian's spikes, the Vex's eyes and the Enderman's glow, the parts are the vanilla models' own parts, so they match the real mobs exactly.

- **Armor upgrades.** Mob drops that attach to armor: a helmet, chestplate or
  boots. Put the piece and the upgrade in a crafting grid, and you get the piece
  back with the upgrade on it. Ears and tendrils go beside the helmet in a row.
  The items have textures, and they show on the armor.

  | Upgrade | Comes from | Goes on | Power |
  |---|---|---|---|
  | Left or Right Bat Ear | the Bat dying, one of them | helmet | one: echolocation. Both: also, underground, everything within 16 blocks glows |
  | Left or Right Warden Tendril | a player killing the Warden, one of them | helmet | one: echolocation. Both: also sniffing, and wardens glow |
  | Ender Pearl Shard | the Enderman dying | chestplate | Z: random teleport, 10s cooldown |
  | Stalker Shard | crafted: Ender Pearl Shard + Creaking Splinter (creakings drop it) | helmet or chestplate | Z: teleport behind an enemy who isn't looking at you, 20s cooldown |

- **Mob items.** Merc's idea: every mob has its own item, and other players use
  it to make their own mob stronger. Each drops when another player kills that
  mob, one per death. Dying any other way drops nothing, so nobody farms their
  own. The Bat, Warden, Enderman and Wither already had theirs: the Bat Ear,
  Warden Tendril, Ender Pearl Shard and Star Fragment. See item/MobRelics.

  Two have textures so far: the Breeze Core, a snapped breeze rod with wind
  coming out of the break, and the Blaze Ember, a blaze rod snapped in half and
  burnt to charcoal, glowing at the break. The rest show the purple and black
  missing texture. To add them, put 16x16 PNGs in
  assets/mobcraft/textures/item named after each item: lucky_paw, breeze_core,
  iron_heart, creeper_fuse, shulker_essence, skull_visor, illusion_lens,
  elder_scale, piglin_truffle, silverfish_grub, villager_bread, axolotl_roe,
  dragon_essence, ghast_wisp, blaze_ember, vex_wing and broken_totem. The models
  already point at those names. Tooltips say whose item each one is.

  Armor items attach like the other upgrades, anywhere in the crafting grid
  with the piece, one upgrade per piece, and work while worn:

  | Mob | Item | Goes on | Does |
  |---|---|---|---|
  | Rabbit | Rabbit Ears | helmet | Armor Power key (Z): turn into a rabbit. Everyone sees a plain rabbit, you shrink and hop, and mobs ignore you. Hitting or getting hurt turns you back, 10s cooldown |
  | Breeze | Breeze Core | boots | no fall damage |
  | Iron Golem | Iron Golem Head | chestplate | half knockback |
  | Creeper | Creeper Essence | absorbed | B charges what you hold; throw it to blow up, harder the denser it is, breaking blocks like TNT; 45s cooldown. Sneak+B: blow yourself up, sized by your inventory's density, and die |
  | Vex | Vex Wing | chestplate | fly like the Vex for 3 energy a second (50% more than the Vex's Phase), and at 0 energy you drop until you're back to 10; the wing has 216 durability (half an elytra), loses 1 a second while flying, breaks at 0, and is repaired only by killing allays (54 each); can't go on a chestplate with Mending (VexWing) |
  | Skeleton | Skull Visor | worn as a helmet itself | skeletons, strays, bogged, parched and wither skeletons can't hurt you, 40% less projectile damage, bows draw 25% faster. But you burn in sunlight and see out only through the two goggle slits of its eye sockets. Leather-strength armor, repaired with bones |
  | Illusioner | Illusion Lens | helmet | Blindness and Darkness can't affect you, except the Bat's own curse |
  | Elder Guardian | Elder Spike | helmet | Water Breathing and Night Vision underwater |

  The Shulker's item is an essence instead. Right-click the Shulker Essence to
  absorb it, then press Essence Power (B) to close a shulker shell around
  yourself. Nothing hurts you inside and you can't move. Two hits from any
  pickaxe, sword or axe, at least half a second apart, crack it and then break
  it, with a crack texture over the shell (textures/entity/shell_cracks.png)
  and Slowness II for 5 seconds after. It lasts up to 10 seconds, B opens it
  early, and the cooldown is 30 seconds. The essence stays until you die. The
  Shulker player can't absorb one. Old Shulker Scales turn into essences.

  The Ender Dragon's item is the Dragon Essence, its egg cracked open with a
  purple glow coming out. Absorb it the same way, then B gives Absorption IV
  for 2:00, Regeneration II for 0:30 and Resistance I for 1:00, every 4
  minutes (DragonEssence). The Ender Dragon player can't absorb one. Old Dragon
  Hearts turn into Dragon Essences. A player holds one essence at a time, and
  absorbing a different one replaces it.

  The Evoker's item is the Broken Totem: a totem of undying gone grey and
  cracked, with red eyes and red fog leaking out. Absorb it for two uses,
  spent on either power (EvokerEssence). B swaps places with the player you're
  looking at, up to 32 blocks away, teammate or enemy. Sneak + B readies the
  chain for 30 seconds: the next melee hit with a sword, axe, spear, trident,
  or a mace without Density or Wind Burst, throws a red chain onto whoever you
  hit and pulls them to you. The chain is drawn in the world by
  client/EvokerChainRenderer: chunky solid 3D links in see-through red
  (textures/entity/red_chain.png), with the Enderman's teleport glow turned red
  drifting over them (textures/entity/red_glow.png). When both
  uses are gone the totem crumbles, and a red essence orb floats up out of
  you and fades (client/EssenceOrbs): the game's wind charge model in red
  (textures/entity/red_orb.png), with a glowing core and a second wind ring
  spinning the other way, trailing red dust. Old Evoker Fangs turn into Broken Totems,
  and the Potion of Fangs can't be brewed any more.

  Eaten items, always edible, wearing off like a potion:

  | Mob | Item | Gives |
  |---|---|---|
  | Piglin | Piglin Truffle | Strength I and Absorption II, 2:00 |
  | Silverfish | Silverfish Grub | Haste II, 3:00 |
  | Villager | Villager Bread | Hero of the Village, 5:00 |
  | Axolotl | Axolotl Roe | Water Breathing 5:00, Regeneration I 0:30, Dolphin's Grace 1:00 |

  Brewed items go into an Awkward Potion. Gunpowder and dragon's breath make
  splash and lingering versions:

  | Mob | Item | Potion |
  |---|---|---|
  | Ghast | Ghast Wisp | Thrown: a small fireball that sets what it hits on fire for 3s and bursts into a weaker wind charge gust. Brewed: Drifting, Slow Falling and Fire Resistance, 3:00 |
  | Blaze | Blaze Ember | Blazing: Strength I and Fire Resistance, 1:30 |

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
- **Piglin Barter Shop.** Anyone holding gold ingots, nuggets or blocks can
  right-click an adult piglin that isn't angry at them to open a shop of
  everything piglins barter. Click an item to buy it. Nothing in the menu can
  be taken or moved. Throwing gold to a piglin still gives a random barter.

  Items that belong to a MobCraft mob aren't sold to anyone while a player is
  that mob, online or offline. While Merc is the Enderman, only Merc has ender
  pearls, so everyone trades with him. Once nobody is the Enderman the piglin
  sells them again. An item belongs to a mob if the mob gives it to itself or
  drops it on death. Who is which mob is kept in the world folder in
  mobcraft_roster.json, updated on /setmob and when a player joins, so after
  updating, each player with a mob needs to join once to be counted. Right now that blocks ender pearls while there's an Enderman, crying
  obsidian while there's a Piglin, iron nuggets while there's a Silverfish and
  fire charges while there's a Ghast. Blocked items stay in the menu marked
  "Not for sale" with who has them.

  Prices follow how much gold you own, so gold farms don't make everything
  cheap. The shop counts ingots, nuggets, blocks and raw gold in your inventory,
  in shulker boxes and bundles you carry, and in your ender chest. Gold in a
  chest at home isn't counted. The base prices below are for 64 gold, and the
  multiplier is the square root of your gold divided by 64, from 0.5 to 3:

  | Gold you own | Prices |
  |---|---|
  | 16 or less | half |
  | 64 | as listed |
  | 256 | double |
  | 576 or more | triple |

  You pay with gold on you: ingots, then nuggets, then blocks, with change.
  Prices are in shop/PiglinShop.java.

  | Barter item | Base gold |
  |---|---|
  | Soul Speed book, level I to III at random | 20 |
  | Iron boots with Soul Speed I to III at random | 18 |
  | Ender pearls x4 | 8 |
  | Splash potion of Fire Resistance | 7 |
  | Potion of Fire Resistance | 6 |
  | Spectral arrows x8 | 4 |
  | Obsidian x1, crying obsidian x2, quartz x8 | 3 each |
  | Iron nuggets x16, string x8, leather x4 | 2 each |
  | Fire charge, soul sand x8, nether bricks x8, gravel x16, blackstone x16, water bottle | 1 each |

- **Evoker bound totems.** Totems the Evoker gives himself say "Bound to
  <name> and their team". They save him and anyone on his team, even while he's
  offline, but anyone else holding one dies as if it weren't there. The totems he
  drops on death aren't bound. See item/BoundTotem and mixin/BoundTotemMixin.
- **Team & Pets screen.** Press Y, or Team & Pets... in /mobcraftsettings.
  Without a team: a name box with Create, and Accept Invite. On a team: the
  team's name in its color and its players, Rename, the 16 colors as squares,
  a player picker with Invite or Kick, Leave Team (click twice) and Accept
  Invite. Below that, your pet counts and My Pets..., which lists every pet
  with its kills, whether it's sitting or following and how far away, with Sit
  or Stand and Release (click twice) buttons. Those only work on pets loaded
  near you. Each
  button runs the matching /myteam command, so the same rules apply.
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

## Gear and items

- **Duskvein and Gloamite.** Duskvein is a rare Deep Dark ore. In the enchanting
  table's lapis slot it can push maxed enchants past their max and awaken custom
  enchants. Mining it sometimes gives Gloamite, a tier above netherite: seal it
  with rose gold (Dusk Binding) and smith it onto netherite gear with the
  template from Ancient City chests.
- **Gloamite traits.** Every Gloamite tool rolls 2 traits from its own list of
  5 or so (24 in all). The Duskvein Anvil rerolls them.
- **Tiers.** Weapons, tools, armor, bows and crossbows roll a tier the first
  time you get them (Broken to Legendary), shown before the name. Reroll at an
  anvil. 12 rare troll tiers, like Squeaky, Butterfingers, Backwards and
  Chicken Shot.
- **Custom enchants.** 24 enchants for every kind of gear, from Phantom Edge and
  Ricochet to Homing, Grapple, Maelstrom, Dash and Magnetic. Found in Ancient
  City chests and ominous vaults, or awakened by Duskvein.
- **Mace enchants.** Gravity Well, Aftershock, Skybreaker and Echo Slam, plus
  Wind Burst IV and V.
- **The Stronghold Tome.** A rare book with four powers: Binding Word, Word of
  Silence, Unwriting and Page Storm. The Strongholder's Mantle appears while
  you read. The Unbinding Severance enchant cuts its chains faster.
- **The Witch's Shadow.** Kill a normal player for a Shadow Essence, combine it
  with an amethyst shard and absorb it. It gives Shadow Crush (X) and lets you
  see Unseen Hands.
- **Unseen Hand.** A chestplate enchant: shadow hands rise from your back and
  drag whoever you look at to you. Only you and Shadow holders see the hands.
- **The Unseenhush.** A dev item, coded but switched off until the players vote
  on it. Moderators can test it with `/mobcraftdev unseenhush on`.

The full history is in src/main/resources/assets/mobcraft/update_log.txt.

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
    /rtstest move <x> <y> <z>  send your mobs within 64 blocks there, like an RTS click
    /rtstest attack <target>   send them after a target, like clicking an enemy in RTS
    /scaletest 0.4             shrink yourself, then F3+B for the green box
    /flytest                   lift off and fly, as a mob that can, to see wings flap

    /glowtest @e[type=cow,distance=..20] red        cows glow red on your screen only
    /glowtest @e[type=cow,distance=..20] aqua 10    aqua for 10 seconds
    /glowtest clear @e[type=cow,distance=..20]      stop


Colors: white, red, green, blue, yellow, purple, aqua, orange.

## Vanilla source dump

To check method names against real 26.3 code:

    gradlew genSources
    gradlew dumpVanillaSource

Send build/vanilla-src.zip back.
