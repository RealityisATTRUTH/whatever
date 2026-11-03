# Fight Bot

A server mod for Minecraft 26.3 that turns a Carpet fake player into a fighting bot. It reads its own inventory, picks a fighting style that fits, and picks again when items run out. Only run it on servers you own or worlds you play alone. Bots against real players on public servers break their rules.

## What you need

- A Fabric server or singleplayer world on 26.3 with Fabric API.
- Carpet PvP Practice, which includes Carpet. The bot uses Carpet's fake players as its body.
- Java 25 to build.

```
gradlew build
```

Put `build/libs/fightbot-0.43.0.jar` (or `fightbot-0.43.0-learning.jar`) in the server's or world's `mods` folder.

## Use

```
/fightbot spawn BOT TARGET [PROFILE]   spawn a fake player where you stand, give it the sword kit, and fight TARGET
/fightbot fight BOT TARGET [PROFILE]   take over a fake player that already exists
/fightbot fight BOT everyone [PROFILE] free-for-all: fight anyone in survival or adventure it can see within 32 blocks
/fightbot stop BOT                     stop a bot and save its fight
/fightbot kit BOT KIT                   give a maxed netherite kit: sword, axe_shield, bow, trident, pots, uhc, elytra_mace, wind_mace, oreo, or crystal
/fightbot list                         show running bots, their style, and what state they are in
/fightbot inv BOT                      open a live, read-only view of the bot's inventory (add text for a chat list)
/fightbot why BOT                      say what the bot is doing and, in the crystal style, why it is or is not placing
/fightbot profile NAME                 show a style profile's numbers
/fightbot results                      show the last few results
/fightbot learning                     show persistent learning scores and opponent-read summaries (learning build only)
```

TARGET can be you, a friend, or another fake player. The spawn command uses Carpet PvP Practice's kit command to hand the bot a sword kit. If your Carpet has no kits, give the bot a sword and golden apples by hand, then use the fight command.

## Two builds

`gradlew build` makes two jars in `build/libs` from the same code:

| Jar | What it does |
|---|---|
| `fightbot-0.43.0-learning.jar` | Scores every round and saves the scores and opponent reads in `fightbot/learning/`. Has `/fightbot learning`. |
| `fightbot-0.43.0.jar` | Fights exactly the same, but keeps no learning data and has no `/fightbot learning`. |

Put only one of them in the `mods` folder. The only difference is the `learning` flag in `fightbot-build.properties`, read by `Build.java`.

## Learning and scoring

After each completed round, Fight Bot calculates two transparent quality scores:

- **CQS (control quality):** validator acceptance, fresh observations, aim alignment, and contradictory-input rate.
- **CIS (combat results):** confirmed hit rate, damage efficiency, round outcome, observed opponent-pattern coverage, survival, and resource-use discipline.

The overall score is 40% CQS (Control Quality Score) and 60% CIS (Combat Intelligence Score). Rejected attacks, uses, jumps, and blocks; stale observations; missed attacks; and losses lower the relevant scores. Each completed round updates an exponential moving average and records a coarse opponent pattern (aggressive, defensive, mobile, mixed, or insufficient data).

In the learning build, use `/fightbot learning` to display the saved profile. Data is stored in `fightbot/learning/profile.properties` and `fightbot/learning/history.csv`. Existing `results.csv` and episode files keep their original formats. See [LEARNING.md](LEARNING.md) for the formula and penalty ledger. This release records learning feedback and trends; it does not automatically rewrite combat decisions, imitate human players, or bypass server anti-cheat. Use bots only in worlds or servers where you have permission.

## Styles from the inventory

When a fight starts, and whenever the kit changes, it checks what it carries and picks a style. It announces the choice in chat.

| It carries | Style | Built |
|---|---|---|
| Riptide trident, Lunge III spear, Wind Burst mace, and water bucket or wet conditions | Riptide-Lunge-Mace combo | Yes |
| Mace, elytra, at least 2 fireworks, and a chestplate to swap to | Elytra mace | Yes |
| Mace and wind charges | Wind charge mace | Yes |
| End crystals and obsidian | Crystal | Yes |
| Respawn anchors and glowstone | Anchor | Yes |
| TNT minecarts and rails | Minecart TNT | Spotted only |
| Only a spear | Spear | Spotted only |
| Sword or axe, with or without a shield | Sword or axe | Yes |
| Bow and arrows only | Bow | Spotted only |

A spotted-only style is announced, then fought with the best built style the kit allows.

- **Elytra mace.** It swaps the elytra on, jumps, opens it, and rockets up. It glides until a fall from there would land where the opponent is heading. Then it swaps the chestplate back on to stop gliding, falls, and smashes with the mace on the last tick in reach for the most fall damage. A smash cancels the fall, so it swaps the elytra back on in the air and goes again. If a drop is going to miss, it opens the elytra before landing.
- **Wind charge mace.** It gets within 5 blocks, jumps, and throws a wind charge at its own feet to launch. It steers over the opponent in the air and smashes on the way down. Between launches it fights with the sword style on the floor instead of backing away. It will not launch right next to a full attack bar, because that hit knocks it away.
- **Riptide-Lunge-Mace (`oreo`).** The dedicated kit places water if needed, charges and releases a Riptide III trident aimed upward, tries to recover the source with its bucket, turns toward the target around the apex, then issues the existing one-tick Lunge spear jab and hands the fall to the mace policy. The policy confirms upward movement before continuing and falls back to the normal mace policy if the launch does not happen. Server-side behavior must be verified in a private test world; the Riptide animation itself is not proof that velocity carried over to the spear action.
- **Stun slam.** If the opponent holds a shield up during the drop and the bot has an axe, it hits the shield with the axe, then smashes with the mace on the next tick.
- **Out of rockets.** When fireworks drop below 2, it switches to the wind charge style if it has charges, then to the sword.

## Swaps, lunges, and restocking

These work on top of every style.

- **Attribute swaps.** In the mace styles it falls holding a charged sword and switches to the mace inside the hit. The hit keeps the sword's attack bar and damage, and the mace adds its fall bonus. Mojang calls this an unsupported exploit and may fix it. The first swap hit checks whether it still works on your server and says so in chat. If it is fixed, the hits still land with the normal damage.
- **Stun slam in one tick.** Against a raised shield it hits with the axe, then the mace, inside the same tick. Two hits in one tick is normal game behavior, so this works even if attribute swaps get fixed.
- **Lunge spear.** With a Lunge spear in the hotbar, it switches to the spear, jabs, and switches back inside one tick. On the ground it lunges at a player who is running, eating, or drawing a bow. In the air it lunges sideways when a mace drop is going to miss. Lunge needs more than 6 hunger and does not work while gliding.
- **Totems.** When a totem pops, it puts the next one straight into the offhand from anywhere in the inventory, whatever else it is doing that tick.
- **Elytra on the ground.** Up close on the ground it fights on foot with the chestplate on. In any other style, it swaps the elytra for the chestplate as soon as it lands.
- **Shulker boxes.** When it runs out of apples, totems, fireworks, or wind charges and nobody is within 14 blocks, it places a shulker box next to itself, opens it, and takes supplies one stack per tick. Totems come first, then apples, fireworks, wind charges, pearls, and the rest. Then it closes the box, mines it back up at normal pickaxe speed, and walks over it to pick it up. If the opponent comes within 6 blocks, it closes the box and goes back to fighting.

Swap hits, jabs, and the swap hands key go through the server's own packet handlers, the same ones a player's client uses, so the server applies its normal rules to them.

## Kits

`/fightbot kit BOT KIT` gives maxed netherite gear: Protection IV armor, Sharpness V netherite sword and axe, a Density V and Wind Burst III mace, a Lunge III netherite spear, and a pickaxe for shulker boxes. Everything has Unbreaking III and Mending. Each kit also has totems, golden apples, enchanted golden apples, and splash potions of healing, strength, speed, and fire resistance.

| Kit | Hotbar and offhand |
|---|---|
| sword | Sword, axe, spear, apples, healing, strength, speed, egaps, pearls. Shield in the offhand. |
| elytra_mace | Sword, axe, mace, fireworks, elytra, spear, apples, healing, strength. Totem in the offhand. |
| wind_mace | Sword, axe, mace, wind charges, spear, apples, healing, strength, speed. Totem in the offhand. |
| oreo | Sword, Wind Burst I mace, Lunge III spear, Riptide III trident, water bucket, wind charges, apples, healing, strength. Totem in the offhand. |

Spare totems, potions, and supplies go in the main inventory. If the game refuses an item, for example an enchantment name it does not know, the command says which one.

## Potions

- **When.** Potions go before the fighting style, in every style, but never in the middle of an elytra combo. Buffs wait for the ground. A healing potion also goes in the air when it's within about 1.6 blocks of the ground, or at any height when it's about to die.
- **Healing.** At 8 health or less it throws a healing splash potion straight down, which lands on itself at once. It does this even up close, since it only costs a tick.
- **Buffs.** When strength, speed, or fire resistance is missing or has under 10 seconds left, and the opponent is at least 7 blocks away or eating, it splashes that potion at its feet.
- **Drinking.** Drinkable potions take 1.6 seconds, so it only drinks with even more room, and stops if the opponent comes within 3.5 blocks.
- **Out of healing potions in the hotbar.** At low health with none left in the hotbar but more in the backpack, it moves one down right away, however close the opponent is, and splashes it the next tick.
- **Restock.** When a potion it needs is only in the main inventory, it moves it into the hotbar while there is room. A restock never pushes a weapon, apples, totems, or other fighting items out of the hotbar.

## Movement

When the opponent is more than 4 blocks away, it sprint jumps on every landing, which is faster than plain sprinting. It never sprint jumps into a hit trade.

The mace only adds fall damage when the player has fallen more than 1.5 blocks and is not gliding. That is why the elytra comes off before the smash, and why elytra mace needs a chestplate in the hotbar.

## Towers, pearls, water, and shulkers

- **Towers.** When the target stands at least 1.6 blocks above it for a few ticks, it walks to their tower and pillars up next to it until it is level. It jumps, looks down, and places a block under itself at the top of each jump. It builds with cobblestone or another common block, and with obsidian only when it has plenty.
- **Pearls.** When the target is 4 or more blocks up, it throws an ender pearl onto them instead. It traces the pearl's flight to pick the angle.
- **Short pearls.** If it lands beside the tower and starts falling, it places a block under itself against the tower's side and builds up from there.
- **Fire.** When it burns without fire resistance, it puts water at its feet and picks it back up a few ticks later. It skips this in the Nether.
- **Shulker box handling.** It places the box anywhere within reach that has a solid top under it and room for the lid, including on slopes. It mines the box back up with a pickaxe from anywhere in its inventory, or by hand without one. Then it walks onto the dropped box until it's back in its inventory, for up to 4 seconds. If the target comes close once the box is down, it stops taking items and still mines and picks up the box first. A slow restock that runs past 20 seconds also skips to getting the box back.
- **Shulker restock.** It restocks on its last totem, or when crystals, obsidian, anchors, glowstone, pearls, fireworks, wind charges, or apples drop to a fifth of what it started with. It needs the target 14 or more blocks away to start.
- **Kits.** Every kit now carries a water bucket, a building stack, and a shulker box of refills.

## Getting to the target

When it can't run straight at the target, it plans a route over the real blocks and follows it. It plans again every second, or sooner when the target moves. Route costs are counted in ticks, so it takes whatever gets there soonest.

- **Steps.** It jumps up one block steps and drops down up to three blocks.
- **Walls.** It walks around walls it can't break quickly.
- **Doors and gates.** It opens wooden doors and fence gates with a click. Iron doors count as walls.
- **Mining.** It mines through blocks with the right tool from anywhere in its inventory. Mining runs at the game's own speed and shows the crack animation.
- **Wind climbing.** With wind charges, it gets up walls 2 to 5 blocks tall by jumping and wind charging at its own feet, then steering onto the ledge. After three failed tries it stops trying for 5 seconds.
- **Upstairs.** When the target is on a floor above, it digs through the ceiling from the ground and pillars up.
- **Stuck.** If it hasn't moved for 2 seconds and can't reach the target, it plans a route no matter what.
- **Crystals.** With no tool that mines a wall fast, it places obsidian beside itself, sets a crystal on it, steps back and hits it. It needs 12 health plus absorption for this, and it never blasts obsidian.

## Pearls

- **Closing in.** When the target is 14 to 40 blocks away, or 22 to 40 with an elytra and rockets, and it has 10 or more health plus absorption, it throws an ender pearl at them. It traces the pearl's flight through the blocks and aims where a running target will be when it lands.
- **Fall saves.** When it falls into the void, drops 20 blocks or more, or would take a fall its health can't, it saves itself. Over the void or a long drop it pearls to the best solid ground, aiming for spots near the target. Near the ground it wind charges at its feet, or puts water down, or pearls straight down when it has neither. A mace drop onto the target counts as on purpose.
- **Own pearl catch.** With the wind mace, it can throw a pearl straight up and wind launch about 3.5 seconds later. The pearl comes down onto its head on the way up, and the teleport lifts it about 1.8 blocks higher than a plain launch. It holds still and keeps hitting while it waits, so it only tries this when the sky is open and the target isn't closing in.
- **Pearl catch.** With the wind mace, from 7 blocks or more, it throws a pearl at the target and then wind charges at its own feet 3 ticks before the pearl lands. A pearl teleport keeps the player's speed, so it arrives beside the target flying up, then drops back down with the mace.

## Water

In water every style fights the same way, since gliding, rockets, wind charges, pearls and mace drops don't work there.

- **Swimming.** It hits with the sword style's timing. Underwater it sprint swims straight at the target in the direction it looks.
- **Going up and down.** It holds jump to rise and sneaks to sink. Carpet's single jump does nothing off the ground, so it holds the key the way a player does.
- **Air.** It swims up for air when 90 ticks of breath are left.
- **Getting out.** When the target is on land, it swims to the surface and jumps out at the edge.
- **Armor.** It swaps the elytra for the chestplate.
- **No spam.** Rockets, gliding, wind charges and pearls are blocked in water, whatever asks for them.
- **Falls.** A fall into deep water counts as safe, so it doesn't waste a fall save on it.

## Free-for-all and mobs

- **Free-for-all.** With `everyone` as the target, it fights the closest player it can see. It keeps its target until they die or leave, or until someone at least 6 blocks closer shows up. Players in creative or spectator are left alone. The status line shows its current target and kill count.
- **Mobs.** A mob that hits it gets hit back with a sword or axe. It only swings at the mob when no player is in reach, so a mob never costs it a hit on the player. With no player within 10 blocks, it walks over to the mob.

## Ender chest

Every kit carries an ender chest and fills the bot's ender chest to all 27 slots. That's 6 packed shulker boxes, 8 totems, apples, pearls and the kit's ammo. When it has no shulker box left, it restocks from the ender chest the same way. Packed shulker boxes come out last, after the loose supplies. The kit pickaxe has Silk Touch, so the mined ender chest drops itself, and it walks over it the same way as a shulker box. Without Silk Touch an ender chest breaks into obsidian, so it never places one without a Silk Touch pickaxe. It says so once and keeps fighting.

When it needs a restock but the target is within 14 blocks, it pearls to solid ground about 20 blocks away on the far side first.

## Axe and shield

The axe_shield kit has a Sharpness V netherite axe and no sword, so the axe is the main weapon and a shield sits in the offhand.

- **Shield.** It raises the shield when the opponent's bar is nearly full and lowers it a tick before its own swing. It never holds the shield up against an axe, which would disable it.
- **Breaking shields.** Every axe hit on a raised shield disables it for 5 seconds, so it swings at a raised shield right away.
- **Crit jumps.** The axe's bar takes a full second to fill. At half charge it jumps in place without sprinting, so it comes down with a full bar for a 1.5 times hit. It skips the jump while the other player's shield is up.

## Bow and crossbow

The bow kit has a sword, a Power V bow with Punch II and Flame, a Quick Charge III crossbow with Multishot, 192 arrows, and the usual apples, pots and pearls.

- **Bow.** From 5 to 40 blocks away it draws the bow for the full second, then lets go. Each tick it works out the arrow's drop and leads a running target to where they will be when the arrow arrives. It side steps while drawing.
- **Crossbow.** It loads the crossbow while it walks in, then carries it loaded. It fires when you're within 30 blocks, or as an opener once you get close.
- **Shields.** It never shoots into a raised shield. It keeps the bow drawn and waits.
- **Up close.** Within 5 blocks it switches to the sword style.

## Pots only

The pots kit, also called no debuff, is maxed netherite armor and a sword. It also has 16 pearls, three Speed II pots, a Fire Resistance pot and 30 Healing II splash pots. There are no apples and no totems. With 6 or more healing pots, any kit gets this pot play:

- **No waste.** Each pot heals 8, so it pots at 12 health instead of 8.
- **No sharing.** A splash heals everyone within 4 blocks. With the opponent that close, it backs away for up to three quarters of a second before potting.
- **About to die.** At 6 health or less it pots right away, wherever the opponent is, and twice in a row if it has to.
- **Refilling.** It keeps at least 3 healing pots in the hotbar, refilling from the inventory whenever the opponent is 5 or more blocks away.
- **Buffs.** Speed and Fire Resistance get thrown again before they run out, as before.

## Trident

The trident kit has a sword, a Loyalty III trident and a Riptide III trident, plus the usual apples, pots, pearls and a water bucket.

- **Loyalty.** From 5 to 30 blocks away it holds right click for half a second, then throws. The aim uses the trident's own drop and speed and leads a running target. The trident comes back on its own.
- **Riptide.** In water or rain, from 4 to 18 blocks away, it charges for half a second and lets go aimed straight at the target. The dash throws it at them and the spin hits for 8. Riptide does nothing when dry, so it never tries it then.
- **Swimming.** Any style carrying a Riptide trident uses the dash to cross water toward the target, instead of swimming.
- **Up close.** Within 5 blocks it switches to the sword style.
- **In every style.** Any kit with a trident uses it, not only the trident style. With a Riptide trident in rain or water, it dashes at a target 4 to 18 blocks away. With a Loyalty trident, it throws at a target more than 8 blocks off. Mace styles keep their own launches instead.
- **From the backpack.** When it starts raining, it brings a Riptide trident down from the backpack. It brings a Loyalty trident down when the target is out of reach. Both wait until nobody is within 6 blocks.
- **Two Loyalty tridents.** The kit now has a second Loyalty trident in place of the hotbar water bucket, which moves to the backpack. One can be flying back while the other is thrown.
- **Moving while drawing.** It strafes side to side during the half second it draws a throw, instead of standing still.
- **Leading the dash.** A Riptide III dash covers about 3 blocks a tick. It aims where the target will be when it arrives, a little high for the drop, instead of where the target is.

## UHC

The uhc kit is diamond armor at Protection II and III and a Sharpness IV diamond sword. It also has a diamond axe, a bow, a crossbow, a shield, two water and two lava buckets, 8 cobwebs, 9 golden apples, and cobblestone and planks. It has no totems, potions, shulker box or ender chest. It fights with the bow and crossbow style, which uses the sword, axe and shield up close.

These moves work in every style whenever the bot carries the items:

- **Lava.** When the target stands 2 to 4 blocks away on the same level or lower, it pours lava at their feet and steps back. It takes the lava back two seconds later, or as soon as they leave it. It never pours lava on a target above it, where the lava would run down onto it, and never in the Nether.
- **Cobwebs on the target.** It webs the target's feet, so they can't back off or be knocked out of reach.
- **Cobwebs on itself.** At 8 health or less, with a charged opponent in reach, it webs its own feet to stop the combo.
- **Cutting out.** It cuts itself out of webs with the sword once it no longer wants to be in one.
- **Lava safety.** It stops before walking into lava. If it ends up in lava, it climbs out toward the nearest dry block, and its water bucket handles the fire after.

## Looking human

The last step before every input is a human-like layer. It changes how the bot looks, not what it decides. In the simulators it cost no wins.

- **Turns.** A turn is capped at 70 degrees a tick, a fast human flick. Turns that aren't clicks ease in over a few ticks.
- **Clicks wait for the aim.** A hit, throw, shot or crystal break that needs the crosshair waits until the crosshair is within 4 degrees. That takes a tick or two at most, and the click is never lost. A drawn bow stays drawn while it turns.
- **Mouse steps.** Every turn is a whole number of steps of a real mouse at 100% sensitivity, which is 0.15 degrees each.
- **Click rate.** Clicks are capped at 16 a second, left and right each.
- **Totems.** A popped totem goes back after 3 ticks, not the same tick.
- **Inventory.** Items move into the hotbar no more than once every 4 ticks, and never in the same tick as a hit.
- **Throws.** Pots, wind charges, rockets and crossbow bolts go straight into the air, never into a block under the crosshair.
- **Reach.** Crystal and mob hits are held to a player's reach, measured from the eyes to the hitbox.

Set humanize to 0 in a profile to turn the layer off.

## Repairs

Experience from a bottle o' enchanting repairs damaged Mending gear that's worn or held, at two durability a point.

- **When.** It starts once the most worn Mending armor piece has 35% of its durability left, and keeps throwing until everything is back to 90%.
- **How.** It stands still and throws a bottle at its feet every other tick.
- **Safety.** It only stops for this with the opponent 12 or more blocks away. Under 12% on a piece, it repairs with the opponent as close as 5 blocks, since the piece is about to break.
- **Pearling away.** With the opponent closer than 12 blocks and gear worn down, it pearls away first, the same as for a restock.
- **Elytra.** A worn elytra only takes repairs while worn, so it puts the elytra on after the armor is done, repairs it, then puts the chestplate back on.
- **Kits.** Every kit except pots and UHC carries a stack of bottles, and its shulker box and ender chest carry more.

## Routes and block placement

- **Diagonals.** Routes move diagonally too, but only with both side blocks open, so it never clips a corner.
- **Smooth lines.** It steers at the farthest point of the route it can walk straight to, instead of each block's middle in turn. On long straight stretches it sprint jumps. If it cuts a corner into a later block of the route, it skips ahead.
- **Gaps.** It sprint jumps gaps of up to three blocks, landing level, one lower, or one block up over a one-block gap. It backs up for a run-up when it's too slow for a long jump.
- **Speed bridging.** With blocks, it ninja bridges over drops. It walks backward at 45 degrees with sneak off and sneaks just before the edge. As soon as its eyes are past the edge, it places on the side of the block it stands on, then lets go of sneak again. It works out when to sneak from the game's own movement numbers. If a block doesn't show up or the server moves it, it sneaks, steps back and starts that block again. Set `speedBridge=0` for the slower sneak-the-whole-way bridge. On a wide gap it bridges part way and jumps the rest when that's faster.
- **Straight bridges.** A bridge toward someone off at an angle goes straight and turns once, not as a staircase that turns every block. A new plan keeps the bridge direction it's already on. At a corner it stops, sneaking, and turns before walking on.
- **Steering on a bridge.** It nudges its aim back toward the middle of the bridge as it goes, so a degree of drift doesn't walk it off the side over a long bridge. It picks S plus D or S plus A and an angle a few degrees either side of 45 for each run. It drifts its aim a little between blocks and sometimes clicks a tick later, the way a hand does.
- **Edge guard.** Whatever the fighting style picks, a last check works out where the keys would leave it once it stops sliding. It drops any key that would carry it off an edge into the void, lava or a fall over 3 blocks, and sneaks if even standing still would slide it off. In the air after a jump or a hit, it steers the landing back over the blocks. Gap jumps, drops and bridging check their own footing and are left alone.
- **Steering hand-off.** Once the route takes over, it keeps control until the way has looked open for half a second, so steering doesn't flip between the route and the fighting style.
- **Hazards.** Routes never go through lava or fire. They avoid cobwebs, magma, cactus and berry bushes unless there's no other way. A clear line over a pit or a lava pool no longer counts as a straight run.
- **Drops into water.** It drops any height into water, since water stops fall damage.
- **Replanning.** It plans again right away when someone builds or breaks a block on the next few steps of the route, or when it falls off a jump.
- **Clicks.** It only clicks a block face that points toward its eyes, the way a player has to. When a block hides the middle of a face, it tries the face's corners and edges.

## Crit smashes

A recorded session showed every elytra mace smash landing with the attack bar only 36 to 52% charged, about 7 ticks after the rocket down. Taking out a rocket and putting the weapon back both restart the bar. The bot also never crit, because it was still sprinting, and the game gives no crit while sprinting. A crit smash is 1.5 times the whole hit, fall bonus included, so these were the biggest losses.

- **Charge.** The fast dive climbs about 12 blocks now and skips the rocket down when there wouldn't be 12 ticks left for the bar to refill. Follow-up rockets follow the same rule. When the bar is still filling and the target will still be in reach next tick, it waits a tick.
- **Sprint.** In an elytra dive it stops sprinting within 5 blocks of the target, so the smash can crit. In a wind charge fall it keeps sprinting, since those short drops need the steering more.
- **Simulator.** It now applies the crit after the fall bonus, as the game does. With that, the old fast dive averaged 10.6 damage a smash and 10 seconds a fight. The new dive averages 15.9 and 5.3 seconds, and lands 904 crits where the old one landed 200.

## Apples and potions

It used to start a golden apple at 9 health, then splash a healing potion at 8, and the switch cancelled the apple. A recorded session finished 3 apples out of 32 started. At potion health with a healing potion on hand it now splashes instead of starting an apple. Once an apple is started, it finishes it unless it's about to die.

## Prediction and flyers

Every style now asks one shared predictor where the target is going. It runs the target forward with the game's own movement rules: the vanilla elytra step for gliders, with firework boosts spotted from their speed, and running, gravity and the ground for everyone else. Half a second ahead it is about 0.2 blocks off on a steady glide, against 0.85 for a straight-line guess.

- **Arrows, crossbows and tridents** aim where the glide or the jump will take the target when the shot arrives.
- **Crystals** are scored at the target's next position, with gravity, instead of only their running speed.
- **No chasing flyers.** While the target flies with an elytra, it never pearls, towers, pillars or routes after them. Mace styles don't launch after them either. They fight from the ground with the sword until the flyer lands.
- **Waiting.** It sprints in a circle on the ground, since a moving target is harder to dive on, and faces the flight path. It keeps its attack bar full and hits as they pass in reach or land.
- **Dives.** With a shield, it raises it in time for a predicted dive, since a shield takes 5 ticks to come up. It skips that when an axe is coming, because an axe disables shields. Without a shield, it sidesteps on the last tick, when the flyer can't correct.
- **Crystal style against a flyer.** It stays on the ground and keeps two obsidian bases beside itself, toward where the dive comes down. It sets crystals where the predicted dive path will pass within blast range and sets them off as the flyer arrives.

In the simulator, against a diver that homes in with fireworks, waiting beat chasing:

| Kit | Waiting | Chasing |
|---|---|---|
| Crystal, blast damage | 1813 | 111 |
| Sword, dives that reached it | 9 | 10 |
| Sword and shield, dives blocked | 10 of 10 | none |
| Sword, pearls thrown | 0 | 94 |

## Fixes from recorded fights

A recorded session against meowwbu showed the bot losing 18 rounds to 10. These are the fixes:

- **Shield flicks.** The other player raised a shield for a few ticks, took the sword hit, and swung back. Now the bot never swings a sword into a raised shield and keeps its charge for when the shield drops. It takes the axe out as soon as a shield starts to rise, then disables the shield with it.
- **Turning.** Two layers each slowed its turns, so it came round at only 20 degrees a tick. In a close sword or axe fight on the ground it now turns about 47 degrees a tick, still under the human-like cap.
- **No weapon in the hotbar.** Some rounds it fought with a potion in hand because the sword sat in the backpack. It now moves a weapon into the hotbar before any other restock. A round that starts with no weapon at all gets the last kit again.
- **Pearling at a falling player.** It stood looking straight up with a pearl while the other player dropped off a tower next to it. It now drops the tower plan when the player is falling back down.

## Crystal kit armor

The crystal kit's leggings have Blast Protection IV and the other three pieces have Protection IV. The game caps protection against explosions at 20 points. Three Protection IV pieces give 12 and Blast Protection IV gives 8, so the kit reaches the cap exactly while keeping the most protection against hits.

## How it decides

Every tick it reads both players on the server, then works in layers:

1. **Strategic state.** Engage, finish a low opponent, recover by eating, or disengage.
2. **Technique.** Sprint in, strafe and switch direction, W-tap after a hit, S-tap when too close, jump reset when hit, swing only at full charge.
   - **Shield.** With a shield in the offhand it raises it while the opponent's attack bar is filling and lowers it a tick before its own swing. When both bars are full it holds the shield and lets the opponent swing first. It never blocks an axe. If the shield does not come up, it treats it as disabled for 5 seconds.
   - **Breaking shields.** When the opponent keeps a shield up and the bot has an axe in the hotbar, it swaps to the axe and hits the shield. Then it goes back to the sword.
   - **Golden apples.** It eats a golden apple when low and the opponent is far enough away. It eats an enchanted golden apple, called an egap, when health is very low or no normal apples are left. It skips eating while absorption still covers it.
   - **Restock.** When apples are only in the main inventory and nobody is within 6 blocks, it moves them into the hotbar.
3. **Inputs.** Movement, sprint, jump, attack, hotbar, and aim, with aim turned at a human speed and a small wobble.
4. **Validation.** It drops any swing the bot cannot land: bar not full, out of reach, aimed away, no line of sight, or eating. Every drop is counted.
5. **Feedback.** Every tick is saved, and the fight's records are written when it ends.

Decisions react to what happened 2 ticks earlier, like a person would. The attack bar is read exactly from the server.

## Profiles

A profile is the bot's style as numbers in `fightbot/profiles/NAME.properties`: preferred distance, charge threshold, W-tap and S-tap chance, strafe timing, jump resets, crit jumps, turn speed, when to eat, shield and axe use, the egap health line, restocking, mace drop height, wind launch distance, stun slams, and reaction time. Set autoStyle to 0 to always fight with the sword style. `default` is created on first start.

**Copy a recorded player.** The calibrate script reads the fight recorder's training files and writes a profile that fights like that player:

```
python tools/calibrate.py Jorhe780 path/to/fightrecorder/fights --kit sword --out fightbot/profiles/jorhe780_style.properties
```

Then spawn with that profile name.

## Rounds

A fight keeps going until `/fightbot stop BOT`. When the target dies, the bot saves the round, waits for them to respawn, and starts the next round. When the bot dies, Carpet removes it, so the bot is spawned again where it died after 3 seconds, gets its last kit again, and keeps fighting. Every round starts with potting up: it splashes every buff it carries, strength, speed, and fire resistance, before it fights. Set `persistent=0` in the profile to stop after one round.

## Crystal and anchor PvP

With end crystals and obsidian, or respawn anchors and glowstone, it fights with blasts. It scores every option as damage to the target minus damage to itself, places and checks blocks through the server's own click handling, and logs every action with what really happened. See `CRYSTAL.md`.

## Elytra combo

During elytra drops it can run a full elytra, rocket, chestplate, elytra, chestplate, axe, mace combo. Each swap and glide is confirmed before the next step, and the combo abandons and recovers when anything goes wrong. See `COMBO.md`.

## Flight analysis

The mod also records elytra flight and movement for any player, works out movement states and action results, and checks the game numbers against what really happens. See `FLIGHT.md` for the commands, files, and test steps.

## Testing without Minecraft

The core has a small sword duel simulator with the vanilla attack-charge damage rule, a sword cooldown, crits, sprint knockback that needs a sprint reset, hit invulnerability, aim-based misses, and golden apples. Movement and knockback are simplified.

```
java -cp build/libs/fightbot-0.43.0.jar dev.reality.fightbot.core.DuelSim 200 fightbot/profiles/default.properties
```

A second simulator covers the mace styles in 3D on a flat floor. It copies vanilla's walking, falling, elytra gliding, firework boosts, mace smash damage, attack cooldowns, shields, attribute swaps, and diamond armor. Protection is a flat cut, there are no totems, and wind charge launches are simplified. The Lunge push is a guess, because the real number was not available.

```
java -cp build/libs/fightbot-0.43.0.jar dev.reality.fightbot.core.MaceSim 100
```

## Files

Everything is saved in the `fightbot` folder next to the server's mods folder.

- `episodes` has one training file per bot fight, in the same state, action, result layout as the fight recorder, plus the bot's state and technique on each record.
- `results.csv` has one row per fight: outcome, length, damage dealt and taken, swings and landed hits, rejected actions, stale ticks, and techniques used.
- `profiles` holds the style profiles.

## Tested and not tested

- **Tested.** The decision core, validator, profiles, episode records, and calibration script, with unit checks and simulated duels.
- **Not tested.** The Minecraft side compiles and both jars build, but it has not been run on a live server yet. Carpet's command names should be checked in game. Check that holding right click with a sword in hand raises the offhand shield, and that the restock move shows up in the bot's hotbar. For the mace styles, check that the elytra opens on the jump, that fireworks fire while gliding, and that a wind charge thrown straight down launches the bot. Also check that a Lunge jab dashes the bot, and that the shulker box places, opens, and breaks. Shulker restocking is not covered by the simulator.

## License

CC0 1.0, see LICENSE. It's in the public domain: anyone can use, change, share or build on it, with no need to ask or give credit.
