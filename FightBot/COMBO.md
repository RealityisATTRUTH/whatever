# Elytra combo

Elytra, rocket, chestplate, elytra, chestplate, axe, mace. It runs inside the elytra mace style whenever the bot is gliding high enough near the target. Turn it off with `combo=0` in the profile.

## Steps

| Step | What it does | Moves on when |
|---|---|---|
| Check | Needs a mace and a chestplate in the hotbar, a firework for recovery, the elytra worn, and an open glide. | All are there. Otherwise it logs what is missing and does not start. |
| Drop 1 | Asks for the chestplate swap. | The chest shows a chestplate and the glide has stopped. |
| Fall 1 | Falls and steers. Holds the sword so a swap hit keeps its charge. | The target is in reach with enough fall, so it smashes. Or the drop will miss by over 2 blocks with over 6 blocks of height left, so it re-glides. |
| Re-equip | Asks for the elytra swap. | The chest shows the elytra. |
| Re-glide | Asks to open the elytra. This is its own step, because equipping it never starts a glide. | The glide flag is on. |
| Dive | Dives at 60 degrees toward where the target is heading. Falling faster than half a block per tick, the game keeps adding to the fall distance during the glide. | The drop is lined up, or it is down to 5 blocks above the target. |
| Drop 2 | Asks for the chestplate swap again. | The chest shows a chestplate and the glide has stopped. |
| Fall 2 | Selects the axe if the fall leaves time for it to charge, or if the target is blocking. Otherwise it keeps the sword. | The smash timing is valid. |
| Smash | Switches to the mace inside the hit. Against a raised shield, it hits with the axe and then the mace in the same tick. | Always moves to the hit check. |
| Hit check | Looks for the fall being cancelled or the target losing health. | Logs hit, miss, or blocked. |

## Quick follow-up on a target in the air

When the target is really up in the air, the bot follows right away instead of climbing high. That means the target is shooting up fast, has been off the ground for 8 ticks, or is at least 2 blocks above the bot while the bot stands on the ground. A normal hop does not set it off.

1. If the bot is on the ground, it jumps with the elytra going on in the same tick.
2. In the air, it asks for the elytra, the glide, and one rocket all in the same tick. The server applies them in that order, so the rocket fires into an open glide. Each of the three is still checked before the chase. If the glide only opened a tick late, it fires the rocket again.
3. It chases a point 2.5 blocks above where the target is heading, until it is over the target or within 3 blocks.
4. It swaps to the chestplate. Once that is confirmed, it starts falling and checking for the smash in the same tick.
5. It swings on the first good tick with at least 1.6 blocks of fall: axe and mace in the same tick if the target has a shield, or the mace inside the hit if not.

It still waits until half a second after the last hit, because a player hit that recently only takes the part of a new hit that is bigger than the last one.

## Targets under a roof

The bot measures the open space above the target's head and above its own. When there is less than 6 blocks over the target, a mace drop would hit the roof, so it does not try one:

- **On the ground.** It fights on foot with the sword and the chestplate on. Its sprint hits knock the target out from under the roof. It does not sprint jump there, since jumping under a low roof hits the ceiling.
- **Standing on the roof.** It walks off the edge first, since it cannot hit through blocks.
- **In the air.** It stops rocketing, glides down next to the target, and swaps the chestplate on just before landing.
- **Combos.** A combo or follow-up in progress is abandoned the moment the target goes under cover.

Once the target is back under open sky, the mace styles take over again.

## Less predictable drops

Each elytra launch climbs to a different height, 4 blocks either way on average, so drops are harder to time.

## Safeguards

- **Confirmed swaps.** Every swap and glide is checked against the next observations. It waits 3 ticks, retries once, then gives up and recovers.
- **Smash rules.** It only swings with at least 3 blocks of fall, not gliding, falling, with the target in reach. It waits for the last tick in reach for the most damage, unless the target is blocking. If the target comes into reach with too little fall, it abandons instead of swinging.
- **Abandon and recover.** It abandons when the target is over 14 blocks away, line of sight is lost for 6 ticks, health is 6 or less, a step takes over 3 seconds, or it lands early. To recover it puts the elytra back on and opens it, and the elytra style rockets out.
- **Repeated failures.** If 3 chest swaps in a row fail, it glides down and fights with the sword instead. A swap that worked resets the count.
- **Low health.** It does not start a combo at 6 health or less. It uses plain drops instead.
- **No repeat requests.** It never sends the same swap again while the last one may still be on its way, so a slow server does not cause swaps back and forth.

## Log

Each fight with combos writes `fightbot/combos/EPISODE.jsonl`. Every step gets its own line: combo number, tick, step, action, hotbar slot, chest item, velocity, fall distance, target distance, line of sight, attack bar, and result. Lines are written as they happen, so a fight that ends mid-combo keeps every step up to that point. At the end of a fight the bot says how many combos landed and how many were abandoned.

## Settings

All in the profile file: `combo`, `followUp`, `followRange`, `minDropHeadroom`, `followMinFall`, `followAbove`, `hurtCooldownTicks`, `heightJitter`, `elytraGroundFight`, `comboConfirmTicks`, `comboRetries`, `comboMinFall`, `comboRegripMiss`, `comboRegripHeight`, `comboFirstDropTicks`, `comboDivePitch`, `comboFinalDropHeight`, `comboAbortDistance`, `comboAbortHealth`, and `axeChargeTicks`.

## Testing in a private world

1. Make a superflat world with mobs, weather, and the daylight cycle off.
2. Spawn the bot and a target, give the bot the kit, and record its flight:
   ```
   /player Bot spawn in survival
   /fightbot kit Bot elytra_mace
   /fightbot flight start Bot combo_test
   /fightbot fight Bot YOUR_NAME
   ```
3. Stand still for the first try, then run sideways while it drops, then hold a shield up.
4. After the fight, compare `fightbot/combos` with `fightbot/flight`. The combo log shows what the bot asked for and what it saw. The flight log shows what the server did each tick.

## What has been verified

- **Verified in the simulator and tests.** The requirement check, the step order, waiting for each swap, retries on slow swaps, abandoning on swaps that never happen, catching a glide that never opens, the fall-distance rule, abandoning when the target gets away, the axe decision, the log fields, and falling back to the sword when swaps keep failing.
- **Not verified.** Nothing has run in Minecraft. The simulator uses vanilla's rules as understood, not measured in 26.3. Whether the fall distance really keeps building during a steep dive needs checking in game, because the re-glide step depends on it. The flight recorder's fall distance column will show this.


## Right after a smash

- **Instant follow-up.** When its own smash knocks the target off the ground, the follow-up starts that tick: elytra on, glide open and rocket all at once, then chestplate, then the smash. With a shield in the target's hand, the axe goes in first in the same tick as the mace. It doesn't wait for the target to fly up fast, since a hit only knocks a player up about 0.4 blocks a tick.
- **Wind Burst.** A Wind Burst mace throws the bot back up after each smash. It rides that bounce straight back down for another smash instead of opening the elytra. In the simulator this chain ended fights fastest.
- **No missed swings.** Hits on the target go to the game in the same tick. Through Carpet's left click they would land a tick later along the crosshair, after a knocked-up player had moved.
- **Roofs.** A roof seen over the target in the last 5 seconds still counts while they stay within 6 blocks of that spot, so a hit that knocks them to the edge doesn't start a takeoff.


## Fast elytra mace

This is the default. Set fastElytra to 0 in a profile for the old high climb.

1. It rockets up about 9 blocks over the target, give or take a block.
2. It swaps to the chestplate, which stops the glide.
3. It puts the elytra back on and opens it, on the same tick the chestplate shows.
4. It fires a rocket aimed straight at the target and dives. Diving faster than half a block a tick keeps adding to the fall distance.
5. About 3 ticks out at the dive's speed, it swaps to the chestplate.
6. It smashes with the mace.

When the target is really launched into the air and the bot is above them, it rockets straight down at them. When it's below them, it climbs 5 blocks over them first.

In the simulator, fast mode and the high climb both won all 400 fights, with fast mode taking about 6% longer. The simulated players never react to a climb they can see coming, which is what the low dive is for.

## Riptide → Lunge → Mace (`oreo` kit)

The `oreo` kit is a separate ground-to-air style for Minecraft 26.3. It uses the existing server-side angle helpers (`Angles.approach` / `Angles.diff`) and the project's normal action/humanization pipeline rather than writing custom client packets for look movement. It places water only when the block directly under the bot is solid and the placement cell is open, charges a Riptide III trident aimed upward, and requires an observed upward displacement before continuing. If it placed the water, it attempts to collect that source with the empty bucket while still in reach. It then predicts the target's position from the current target velocity, rotates down toward the target around the apex, performs the existing one-tick Lunge spear action, and lets `MacePolicy` handle the descent and smash.

Use `/fightbot kit BOT oreo` in a private test world. The bot requires auto-style selection to be enabled, Lunge to be enabled in the profile, a Riptide trident, a Lunge spear, a Wind Burst mace, and water or rain. The mod cannot guarantee that a given server preserves any particular animation or velocity across the item transition; test the sequence on the exact server build. If the Riptide launch does not produce upward movement, the policy abandons this sequence and falls back to the existing mace behavior.
