# SolRNG — SpaceRNG plugin (v1)

A Paper plugin implementing the core roll → item → skill tree loop.

## What's in this v1

- **Right-click roll item** ("RNG Core", a renamed Nether Star by default) triggers a weighted
  random roll from the rarity table in `config.yml`, on a cooldown.
- **6 rarity tiers**: Common, Uncommon, Rare, Epic, Legendary, Mythical — fully configurable,
  add as many items per tier as you want.
- **Luck stat** biases rolls toward rarer tiers (doesn't touch common odds directly — it scales
  each tier's effective weight based on a `luck-factor` you set per rarity).
- **Rolled items go into your inventory** as real items, tagged internally with their rarity.
- **`/convert`** — a GUI to turn rolled items into skill points (drop items in the top row, hit Convert).
- **`/skilltree`** — spend points to unlock Luck upgrades, "Auto-Convert" (per-rarity auto points
  instead of inventory items), and "Autoroll" (rolls automatically on an interval).
- **`/tag equip`** (while holding a rolled item) sets that item as your name tag — shows above
  your head, in the tab list, and before your name in chat. `/tag clear` removes it.
- **Server-wide broadcast** when someone rolls Epic or better (configurable).
- Per-player data is saved to `plugins/SolRNG/playerdata/<uuid>.yml` automatically.

## Building the jar

You'll need **Java 21** and **Maven** installed locally (Minehut doesn't compile for you).

```bash
cd sol-rng
mvn clean package
```

This produces `target/sol-rng.jar`. That's the file you upload.

## Installing on Minehut

1. Open your server dashboard → **File Manager**
2. Navigate to the `plugins` folder
3. Upload `sol-rng.jar`
4. Restart the server from the panel
5. Config generates at `plugins/SolRNG/config.yml` — edit it there and run `/rngadmin reload`
   to apply changes without restarting

## Quick test

```
/rngcore give <yourname> 5
```
Then right-click the item you receive to roll.

## Tuning odds & balance

Every item in `config.yml` has an `odds:` field meaning "1 in X" at zero luck. Increase X to make
something rarer. `luck-factor` per rarity controls how much the Luck stat helps that tier — set
it to `0` for a tier you never want luck to affect (e.g. keep Common truly random noise).

## Roadmap (not built yet, per our plan)

- Space-tier rarities above Mythical (Planet / Star / Solar System / etc.)
- Farming integration
- More skill tree branches beyond Luck / Auto-Convert / Autoroll
