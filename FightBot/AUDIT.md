# Mechanics audit

This checks the game-mechanics assumptions in the bot's code against Minecraft Java 26.3. Each one is marked:

- **Verified:** a number the game hands the bot at runtime, or a rule the game itself applies.
- **Conditional:** correct only for a given pose, item, or setting.
- **Estimated:** a tuned number that still needs measuring in game.
- **Fixed:** wrong before this version, corrected now.

Nothing here was run in Minecraft. The simulator tests check the bot's logic against a model of the game, not the game itself.

## Read from the game each tick

| Value | Source in the game | Status |
|---|---|---|
| Attack bar charge | `getAttackStrengthScale` | Verified |
| Health, absorption, effects, food | the player | Verified |
| On ground, gliding, in water, in lava, on fire, in rain | the player | Verified |
| Fall distance | `fallDistance` | Verified |
| Line of sight | `hasLineOfSight` | Verified |
| Eye height | `getEyeHeight`, for the current pose | Fixed, was 1.62 always |
| Bot and target hitbox | `getBbHeight` and `getBbWidth`, for the current pose | Fixed, was 1.8 by 0.6 always |
| Entity reach | `ENTITY_INTERACTION_RANGE` attribute | Fixed, was 3.0 always |
| Block reach | `BLOCK_INTERACTION_RANGE` attribute | Fixed, read now and reported |
| Attack speed of the held item | `ATTACK_SPEED` attribute | Fixed, was 0.05 or 0.08 a tick by item name |
| Block hardness, right tool, blast resistance | the block state and tool tags | Verified |
| Item cooldowns | the player's cooldowns | Verified |

## Rules applied by the game, not by the bot

These go through the game's own code, so the game decides the result:

- **Hits and smashes:** `Player.attack` decides damage, crits, sprint knockback, and smash damage.
- **Blocks and right clicks:** `useItemOn` handles block clicks and `useItem` handles throws.
- **Mining:** `destroyBlock` breaks the block, after progress from `getDestroyProgress`.

The bot only decides when to act.

## Conditional

| Assumption | Holds when |
|---|---|
| A crit needs a falling, not sprinting, non-swimming, non-blind player with a nearly full bar | Java 1.9 and later. The bot now skips crit jumps in water or while blind. Climbing is not checked. |
| Instant Health II heals 8 | Vanilla potion values |
| Splash heals everyone within 4 blocks, less with distance | Vanilla splash potions |
| Riptide only works in water or rain | Vanilla. Read from `isInWaterOrRain` |
| Respawn anchors explode outside the Nether | Vanilla |
| Explosions do no damage on Peaceful | Vanilla. The bot warns about it |
| Pearl teleports keep momentum | Java 1.21.2 and later |
| Attribute swapping works | Unknown for 26.3. The bot tests it on the first swap and says which it found |

## Estimated, needs measuring in game

| Value | Where | Why it's a guess |
|---|---|---|
| Pearl and arrow flight: gravity, drag, launch speed | Pearl and arrow aim | Taken from the game's projectile code, but tick order is not confirmed in 26.3 |
| Wind charge launch speed of 1.1 | Simulator only | Not measured |
| Wind Burst bounce speed of 1.2 | Simulator only | Not measured |
| Crossbow load ticks: 25 minus 5 for each Quick Charge level | Profile setting | Wiki value, not confirmed in 26.3 |
| Pearl catch lead of 3 ticks | Profile setting | Depends on server tick order |
| W-tap and S-tap length, strafe timing | Sword style | Tuned in the simulator, not in game |
| Human turn cap of 70 degrees a tick, 16 clicks a second | Human-like layer | Design choices, not game rules |
| Smash damage formula | Simulator only | Wiki values |
| Elytra glide step and firework push | Predictor | Taken from the game's elytra code, not measured in 26.3 |
| Shield raise time of 5 ticks | Flyer shield timing | Vanilla shield delay, not confirmed in 26.3 |

## Version-dependent code

These calls are the ones most likely to differ between versions. Each has a fallback or a clear failure:

- **Player actions:** the spear jab action is found by name, and lunges turn off if it is missing.
- **Slot changes:** if the game ignores a slot change from a fake player, the bot sets the slot directly.
- **Swing animation:** `swing(hand, SwingAnimation, boolean)` is the 26.3 signature, fixed after a build error.
- **Block reach:** `canInteractWithBlock` is gone in 26.3, so reach is measured directly instead.

## Testing

The simulator suites in `tools/tests` check decisions, aim, and timing against a model of the game. `PoseTest` covers pose-dependent reach and aim. None of them can confirm what only the game knows, such as tick order, so the in-game checks are:

1. Reach to a sneaking, gliding, and swimming target, with `/fightbot why`.
2. A crossbow loading and firing with Quick Charge III.
3. A pearl catch, timing the wind charge against the landing.
4. A follow-up after a smash, with the combo log.
