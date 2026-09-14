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
- **Config migration.** `ConfigMigrator` overwrites every section in its
  `STRUCTURAL` list when the jar's `config-version` is higher than the
  one on disk. Bump `config-version` whenever one of those sections
  changes. Never put a value Leon tunes by hand inside a structural
  section, or the next bump wipes his tuning.
- **File encoding on Windows.** Never round-trip a text file through
  Windows PowerShell 5.1 `Get-Content` / `Set-Content`. It reads UTF-8 as
  ANSI and writes a BOM; in V94 that mangled every `✦` and the small-caps
  scoreboard title in config.yml. Use the Edit tool, or git-bash
  `sed` / `awk` with `LC_ALL=C`. Git-bash `sed -i` also turns CRLF into
  LF, which is harmless because git normalises line endings.
- **Inserting a config section.** Check you are not landing inside
  another section. V93 put `linked-account:` between `shiny.chance` and
  the rest of `shiny:`, which silently moved `node`, `marker` and
  `broadcast` to the wrong parent until V95.
- **Verify Paper API names against the jar** (particles, sounds,
  materials) instead of guessing. Example: `CHAIN` is `IRON_CHAIN` on
  1.21.11.
- **Pushing.** Push yourself with `git push origin main`; Leon expects
  it and Credential Manager now holds his LeonAryns login. If a push
  fails with a 403 naming another account (ArianceAI), that stale
  credential is back: Leon removes it under Referentiebeheer, Windows
  referenties, then pushes once himself. Never put a token in a command,
  the auto-mode classifier blocks it.
- **Java 21 only.** Leon's server runs Paper on Java 21, and a jar
  compiled for a newer release does not load. The VS Code Java upgrade
  tool creates `appmod/java-upgrade-*` branches that switch everything to
  Java 25 and leaves the repo checked out on them. Check that
  `git status -sb` says `main` before committing, and never merge those
  branches.

## Building

`mvn -B clean package` with JDK 21. Maven is not installed locally, so
CI (`.github/workflows/build.yml`) builds the jar and uploads the
artifact `space-rng-jar`. For a local compile check, the VS Code Java
extension ships a JDK 21 at
`~/.vscode/extensions/redhat.java-*/jre/*/bin/javac.exe`; put every jar
in `~/.m2/repository` on the classpath (skip the old adventure 4.13.1
jars) and run `javac --release 21 -encoding UTF-8` over `src/main/java`.
The Paper API jar there can also be read for name checks.

## Open

- Pets: Leon said later. Perks shipped in V90.
