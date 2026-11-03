# Crystal and anchor PvP

The bot picks this style when it carries end crystals and obsidian, or respawn anchors and glowstone. Every tick it does at most one block click and one hit.

## What it does, in order

1. **Hit a crystal.** Any end crystal in reach and in sight whose blast would hurt the target more than the bot.
2. **Work its anchor.** If it placed an anchor near the target: charge it with glowstone, then set it off with any other item, if the blast is worth it.
3. **Place a crystal.** On obsidian or bedrock within reach, where the blast would hurt the target most for the least self damage.
4. **Place obsidian.** Next to the target, to make a spot for a crystal. The spot is scored as if the obsidian were already there, since a new block can shield the target.
5. **Place an anchor.** Next to the target, when crystals are not worth it.
6. **Otherwise.** Swing the sword if the target is in reach, or keep about 3.6 blocks away and strafe. After a second with nothing worth placing, it closes in and fights with the sword instead of circling.

If it goes 5 seconds without a click, it says why in chat, for example "12 too little damage to the target" or "8 obsidian spots: out of reach". It also gives one example spot with its coordinates, the bot's eye position, and the blocks there, so a placement problem can be checked by hand. `/fightbot why BOT` shows the same at any time.

Clicks run the game's own placement code for the bot, the same call Carpet makes for a fake player's right click, after the server's usual reach check.

Anchors only explode outside the Nether. In the Nether they set a spawn point, so the bot never uses them there.

## Difficulty

On Peaceful, explosions do not hurt players at all, so crystals and anchors do nothing and the bot fights with the sword. It says so in chat once. Use `/difficulty normal` or `/difficulty hard` for crystal fights. Hard makes blasts hit 1.5 times as hard, for both sides.

## Choosing

Each option is scored as damage to the target minus damage to the bot. The target's position is predicted 2 ticks ahead. Damage is estimated the way the game does it: distance falloff, how much of the player's box the blast can see, difficulty, armor, armor toughness, and Protection and Blast Protection. The server reads both players' real armor and enchantments.

An option is used only when all of these hold:

- **Enough damage.** At least 4 damage to the target.
- **Margin.** At least 3 more than the bot takes.
- **Self damage cap.** At most 16 to the bot.
- **Survival.** The bot keeps at least 2 health, unless it holds a totem and the blast pops or kills the target too.

## Block placement

The placement layer is separate from the fighting. The fighting code decides what to place and where. The placement layer handles:

- **can place.** Free space, no entity in the way, and a solid neighbor face to click for blocks. For crystals: obsidian or bedrock, air above, and nothing standing in the 1 by 2 space above.
- **finding candidates.** Spots around where the target will be.
- **aiming.** The face to click and the exact point on it.
- **reach and sight.** The clicked point within 4.5 blocks of the eyes, with no block in the way.
- **the click.** The bot selects the slot and sends the click through the server's handler for a player's right click on a block, so the server applies its normal reach and placement rules.
- **checking.** It waits for the block, crystal, charge, or blast to show up in the world. It never sends a new click while one is unresolved. After 3 ticks without a result it records why, such as "something is standing there", and skips that spot for a second.

## Log

Each fight writes `fightbot/crystals/EPISODE.jsonl`, one line per action:

| Field | What it holds |
|---|---|
| tick | When the action happened |
| action | break, crystal, obsidian, anchor, charge, detonate, or melee |
| activeItem | The item used |
| targetX, targetY, targetZ | Where the target was |
| targetHealth, selfHealth | Health plus absorption at the time |
| predictedTargetDamage, predictedSelfDamage | What the bot expected |
| placementResult | confirmed, or failed and why |
| damageDealt, damageTaken | Measured from both players' health over the next 3 ticks, for blasts |
| nextAction | What the bot did next |

At the end of a fight the bot reports crystals hit, clicks, failed clicks, and blast damage dealt and taken.

## Simulator results

A grid world with the same placement rules, power 6 crystals and power 5 anchors, netherite with Protection IV, and totems. Knockback and blocks broken by blasts are left out.

| Arena | Against | Wins | Blast damage dealt | Taken |
|---|---|---|---|---|
| Bedrock floor | Sword player | 30 of 30 | 1230 | 681 |
| Grass floor | Sword player | 30 of 30 | 1234 | 664 |

On bedrock it mostly hits crystals placed on the floor. On grass, obsidian at feet height shields the target from its own crystal, so it mostly uses anchors. With clicks that take 2 ticks to land, it still won 20 of 20, with 3 of 399 clicks failing because the target walked into the spot.

## Not verified

Nothing here has run in Minecraft yet. Check these first in a private world:

- **Clicks.** Placing obsidian, crystals, and anchors through the click handler.
- **Charge reading.** Reading an anchor's charge.
- **Crystal hits.** Hitting crystals.
- **Damage.** Whether the measured blast damage matches the predicted damage in the log.

Test with:

```
/fightbot kit Bot crystal
/fightbot fight Bot YOUR_NAME
```
