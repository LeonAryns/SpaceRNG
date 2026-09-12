# SpaceRNG

Paper 1.21.11 plugin for Leon's Minehut server. Java 21, Maven, built by
GitHub Actions on every push. The Java package is still
`com.spacerng.solrng` and the NBT tag namespace stays `solrng` on purpose,
so items already in players' inventories keep working (see
`SolRNGPlugin.key`).

## How Leon works

- He tests every jar in game and reports bugs and requests in long, mixed
  Dutch and English messages. Reply in the language he wrote in.
- For every change: implement, compile, bump `<version>` in `pom.xml` by
  1, commit with a detailed message that says why, push to `main`, wait
  for GitHub Actions to go green, then report concisely with the jar name
  `Space RNG V<n>.jar`.
- Explain why a bug happened, briefly and technically.
- When he asks for commands, give commands. Do not overbuild.

## Hard rules

- **No em dashes or en dashes anywhere**: replies, code, comments,
  config, lore, commit messages, Discord posts. Use a comma, a plain
  hyphen or a full stop. When sweeping them out of a file, replace only
  the dash character and never collapse whitespace; doing that once
  re-indented every YAML list item in config.yml.
- **Credits are Leon's call.** Never change how Credits are earned
  without asking. The daily farming payout of 150 / 75 / 25 Credits is
  intentional. (V58 swapped it to Coins unasked; Leon rejected it and V66
  reverted it.) If a Credits payout looks off, say so in one sentence and
  keep building.
- **No ALL CAPS inside a menu item**: not the name, the footer or the
  state tag. Caps only in deliberate chat headers and the sidebar title.
- **No pure white on drop names**; they render next to player names in
  the tab list.
- Do not rebalance, restrict or rename things he did not ask about. Flag
  the concern in a sentence and do what was asked.

## Skills

Load the matching skill before touching its area:

- `.claude/skills/menu-design` for any GUI, item lore, chat line or
  sidebar.
- `.claude/skills/aura-design` for any particle effect or sound cue.
- `.claude/skills/enchant-design` for anything about hoe enchants.

## Things that have bitten before

- **Derived, not stored.** Stats are computed from node levels at read
  time. `stats/StatSources` is the single definition of Luck, Speed,
  Money, Coins, Enchant Proc and Shiny; the roll payout, the farm payout
  and `/stats` all read it.
- **Odds.** An item's `odds` is its label and what Money pays on.
  Rarities with `true-odds: true` (Epic and up) roll at exactly their
  label. Common, Uncommon and Rare are bands with a `share` of what is
  left, and their labels only rank items inside the band. See
  `RarityManager.assignRollWeights`. Epic as a whole is about 1 in 5,000.
- **YAML numbers.** Never write scientific notation. `3e-06` parses as a
  String and `getDouble` silently returns the default.
- **Farm plots.** The real block is a `STRUCTURE_VOID` marker and the crop
  is a per-player `sendBlockChange`. A cancelled `BlockBreakEvent` resends
  the real block in the same tick, which is why the marker must be
  invisible.
- **Nova Core.** A forge uses up one `nova_core` item. A player's first
  forge ever is guaranteed. The guide gives a Core when a player reaches
  the Nova step, remembered as the completed quest `gift:<id>`.
- **Crates** keep placements in `crates.yml`; types live in config under
  `crates.types`. **Floating leaderboard heads** keep spots in
  `topheads.yml` and use non-persistent display entities.
- **Live config.** A whole new section does not merge into an existing
  `config.yml` on the server; a single missing key falls back to its code
  default.
- **Verify Paper API names against the jar** (particles, sounds,
  materials) instead of guessing. Example: `CHAIN` is `IRON_CHAIN` on
  1.21.11.

## Building

`mvn -B clean package` with JDK 21. CI is `.github/workflows/build.yml`
and uploads the artifact `space-rng-jar`.

## Open

- Perks and pets: Leon said later.
- Automatic config versioning: offered, never decided.
