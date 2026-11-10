# Auto-Drink Ominous Bottles

Client-side Fabric mod that automatically drinks an **Ominous Bottle** when a raid ends.

## How it works (Boss Bar trigger)

This mod watches the **raid boss bar** at the top of the screen.

- When the boss bar changes to **Raid - Victory**, the mod triggers once.
- It then attempts to drink **one** Ominous Bottle.
- It includes safeguards to prevent repeatedly triggering from brief boss bar flickers.

## Keybind (Enable / Disable)

- Go to: **Options → Controls → Key Binds**
- Find: **Auto Drink Ominous Bottles**
- Default key: **R**

Toggling shows a small on-screen message:
- `Auto Drink Ominous Bottles: Enabled`
- `Auto Drink Ominous Bottles: Disabled`

## Bottle selection behavior

When a raid ends, the mod looks for an Ominous Bottle to drink:

1. **Hotbar scan**
   - It scans the hotbar and selects the **highest Omen level** bottle available.
2. **Pull from inventory (if none in hotbar)**
   - If no bottles are found in the hotbar, it searches the main inventory.
   - If found, it swaps one into the hotbar and drinks it.

If no Ominous Bottles exist anywhere:
- It prints a message indicating none were found.

## Consumption behavior

- The mod switches to the chosen bottle slot.
- It holds “use” long enough for the bottle to be consumed.
- It releases “use” in a way that prevents accidental double-consumption (even under lag).
- It then swaps back to your previous selected hotbar slot.

## Notes / Limitations

- This is **client-side only**. It does not require server-side installation.
- The trigger is based on the **visible boss bar text**, so you must have raid boss bars enabled in your HUD.