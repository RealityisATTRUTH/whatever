# Fight Bot learning and scoring

This version adds transparent post-fight feedback. It does not try to make the bot look human or evade server anti-cheat.

## Scores

Each completed round receives two scores from 0 to 100:

- **CQS — Control Quality Score:** 35 points for validator acceptance, 20 for fresh observations, 25 for aim alignment on attack actions, and 20 for non-conflicting inputs.
- **CIS — Combat Intelligence Score:** 25 points for confirmed hit rate, 20 for damage efficiency, 20 for the round outcome, 15 for opponent-observation coverage, 10 for survival, and 10 for resource-use discipline.

The overall score is `0.40 * CQS + 0.60 * CIS`. A win receives full outcome credit, a loss receives none, and other round endings receive half credit. No damage exchange is treated as neutral damage efficiency rather than a perfect result.

## Penalty ledger

The separate penalty total adds 2 points for each rejected attack, 1 for each rejected use, 1 for each rejected shield action, 0.5 for each rejected jump, 0.25 for each stale observation tick, and 1 for each attack event with no confirmed health drop within three ticks. Some of these also reduce the CQS or CIS component directly; the penalty total is an easy-to-read error counter, not an additional hidden score multiplier.

## Opponent reading

The episode summarizes observable signals: range bands, blocking, item use, sprinting, airborne time, movement speed, and whether the opponent is facing the bot. It assigns a coarse label: `AGGRESSIVE`, `DEFENSIVE`, `MOBILE`, `MIXED`, or `UNKNOWN`. This is an observation summary, not a guaranteed prediction of intent.

## Persistent learning

After each round, the mod updates an exponential moving average using 20% of the newest result and 80% of the previous average. It also keeps aggregate counts of the opponent-pattern labels and writes one CSV history row per round. The latest statistics are shown by:

```text
/fightbot learning
```

Files are written under the game directory at `fightbot/learning/`:

- `profile.properties` — persistent aggregate averages and totals.
- `history.csv` — one detailed score row per completed episode.

This release learns performance trends and provides review suggestions. It does not yet change `StyleBrain` or combat-policy parameters automatically; tactical adaptation should be added only with controlled regression tests so a bad result does not teach the bot a worse behaviour.
