# Flight analysis module

Records elytra flight and movement for one player at a time, works out movement states and action results, and compares what happened with what the mechanics file predicts. It runs on the server inside the fight bot mod. Use it in a private world.

## Parts

| Part | Class | Job |
|---|---|---|
| Observation | `FlightWatchers` | Reads the player on the server each tick into a sample. Missing values stay missing. |
| State estimation | `StateEstimator` | Turns samples into GROUNDED, AIRBORNE, GLIDING, DESCENDING, LANDING, RECOVERY, or UNKNOWN. |
| Action handling | `ActionTracker` | Records each action's timing, states before and after, result, speed change, interruptions, and confidence. |
| Prediction | `Predictor` | Predicts next tick's velocity from the mechanics file. |
| Analysis | `FlightAnalyzer` | Displacement, distance, speed changes, time per state, glides, prediction error, failed and interrupted action rates. |
| Logging | `FlightSession` | Feeds the parts in order and saves each episode. |
| Version layer | `MechanicsConfig` | Every game number, in one file per game version. |

Only `FlightWatchers` and `FlightPractice` touch Minecraft. Everything else runs without the game.

## What a sample holds

Tick, time, position, yaw and pitch, velocity and where it came from, ground contact, elytra open, fall distance, wall and floor collision flags, selected hotbar slot, held item, chest item, fireworks and wind charges left, elytra durability, clearance to the ground below, solid blocks around the player, and health.

Fake players move on the server, so their velocity is the server's own. Real players move on their own client, so their velocity is worked out from positions and marked `position`. Predictions are only scored on the server's own velocity, because the two are different quantities.

## States

- **GROUNDED.** On the ground.
- **AIRBORNE.** In the air, not gliding, not falling fast.
- **GLIDING.** Elytra open.
- **DESCENDING.** In the air, not gliding, falling faster than the threshold in the mechanics file.
- **LANDING.** The first ticks after touching down from a real fall or glide.
- **RECOVERY.** After a crash into a wall, a landing that hurt, or a crash landing.
- **UNKNOWN.** The player is missing, or the ground or elytra flag was not reported, or the player is in the air with no velocity.

## Actions

Glide start and end, fireworks, wind charges, chest swaps, hotbar changes, and jumps. Actions the bot sent itself have high confidence. Actions seen only from the samples have medium confidence, like a firework count dropping, or low confidence, like a jump seen only from upward speed.

An action is accepted when its effect shows up within the confirm window, rejected when it does not, and unknown when the samples needed to tell are missing. A firework's burn time is random, so ticks where it may or may not still be pushing are left out of the prediction score.

## Commands

```
/fightbot flight start PLAYER [LABEL]     record a player until stopped or disconnected
/fightbot flight stop PLAYER              stop and save
/fightbot flight list                     what is being recorded
/fightbot flight practice BOT [TRIALS]    scripted elytra trials on a fake player
/fightbot flight reload                   reread the mechanics file
```

## Files

- `fightbot/mechanics/VERSION.properties` holds the numbers for that game version. It is created with defaults the first time and marked `verified=false`.
- `fightbot/flight/EPISODE/samples.jsonl` has one sample per tick with its state.
- `fightbot/flight/EPISODE/actions.jsonl` has one line per action.
- `fightbot/flight/EPISODE/summary.json` has the game version, the full mechanics config, the analysis, and every state change with its reason.

## Reproducible test steps

1. Make a new superflat world in creative, then switch to survival. Turn off mobs, daylight, and weather.
2. Install the mod and Carpet, then start the world.
3. Spawn a fake player and give it a kit:
   ```
   /player Flyer spawn in survival
   /fightbot kit Flyer elytra_mace
   ```
4. Run the trials. They cycle through 5 pitches, first with a rocket, then without.
   ```
   /fightbot flight practice Flyer 10
   ```
5. Open the summaries in `fightbot/flight`. Check the state changes look right. Then check `predictionError`.
6. If the gliding error stays well above zero on the ticks it scores, a number in the mechanics file is off for this version. Change it, run `/fightbot flight reload`, and repeat. When the errors are near zero, set `verified=true` and note what you measured.
7. To record yourself, use `/fightbot flight start YOUR_NAME`, fly, then `/fightbot flight stop YOUR_NAME`.

## What has been verified

- **Verified by automated tests.** State changes for takeoff, glide, landing, hard landings, and crashes. Missing players and missing fields give UNKNOWN without crashing. Disconnects end the episode and close open actions with the reason. Fireworks and wind charges are accepted or rejected from their effect. A wrong gravity number shows up as prediction error. Config files are created, edited, and read back. Episodes save with the game version and config. The test samples come from a scripted flight that follows the default numbers, so these tests check the module's logic, not the game.
- **Not verified.** Nothing has run in Minecraft yet. The mechanics numbers for 26.3 are vanilla's rules as understood, not measured. The server code was not compiled here. The scripted practice flights, ground clearance, collision flags, and the spear and shulker parts of the bot all need checking in game.

Run the tests:

```
javac -d out src/main/java/dev/reality/fightbot/core/*.java src/main/java/dev/reality/fightbot/flight/*.java tools/tests/FlightTest.java
java -cp out FlightTest
```
