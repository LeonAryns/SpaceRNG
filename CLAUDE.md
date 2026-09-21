# SpaceRNG

Paper 1.21.11 plugin for Leon's Minehut server. Java 21, Maven, built by
GitHub Actions on every push. The Java package is still
`com.spacerng.solrng` and the NBT tag namespace stays `solrng` on purpose,
so items already in players' inventories keep working (see
`SolRNGPlugin.key`).

## Start here

`STATUS.md` holds the current state: which version shipped, what has
never been tested in game, the agreed step plan and the questions still
open. Read it before proposing work, and update it at the end of a
session. It is in the repo on purpose, because the conversation and the
memory folder do not travel to another machine and it does.

One subject per jar. Nothing new is started until Leon confirms the last
thing works in game.

## How Leon works

- He tests every jar in game and reports bugs and requests in long, mixed
  Dutch and English messages. Reply in the language he wrote in.
- For every change: implement, compile, bump `<version>` in `pom.xml` by
  1, commit with a detailed message that says why, push to `main`, wait
  for GitHub Actions to go green, then report concisely with the version
  (`SpaceRNG.jar`, V<n>). The jar is always named `SpaceRNG.jar` so an
  upload on Minehut replaces the old file.
- Explain why a bug happened, briefly and technically.
- When he asks for commands, give commands. Do not overbuild.

## Hard rules

- **No em dashes or en dashes anywhere**: replies, code, comments,
  config, lore, commit messages, Discord posts. Use a comma, a plain
  hyphen or a full stop. When sweeping them out of a file, replace only
  the dash character and never collapse whitespace; doing that once
  re-indented every YAML list item in config.yml.
- **Credits are Leon's call.** Never change how Credits are earned
  without asking. The daily farming payout of 150 / 100 / 50 Credits is
  intentional (Leon raised it from 150 / 75 / 25 in V135). (V58 swapped it to Coins unasked; Leon rejected it and V66
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

## Layout

Packages are by feature under `com.spacerng.solrng`. Where new things go:

- **A menu.** `gui/<Name>Gui` builds it and `gui/<Name>Holder` implements
  `MenuHolder`. Its clicks go in the matching class in `listeners/menu/`
  (convert, skill tree, progression, player menus, shops) with a route
  in `GuiListener`. A holder that isn't a `MenuHolder` gets no drag or
  click protection, and a drag into a menu destroys the item.
- **An `/rngadmin` subcommand.** A `do<Name>` method in `PlayerAdmin`,
  `ShowcaseAdmin` or `WorldAdmin` under `commands/admin/`, shared parsing
  in `AdminTools`, and a route, a help line and tab completion in
  `RngAdminCommand`.
- **Floating text in the world.** `holo/HoloManager`: panels over NPCs
  (text in `holograms.panels`), leaderboard walls with player heads drawn
  inside the text, and crates placed with `/rngadmin crate place` (a big
  head over an invisible barrier that is registered as the crate). One
  TextDisplay per piece, spots in `holograms.yml`, never saved to chunks.
  Leon has FancyHolograms and FancyNpcs; NPCs stay in FancyNpcs, the text
  is ours.
- **Discord.** `discord/DiscordWebhook` posts rare drops, shinies, First
  10 and the farming payout to a webhook URL. DiscordSRV only relays chat
  and handles /discord link; our broadcasts never pass through chat.
- **A new top-level config section** reaches the live server only if it
  is listed in `ConfigMigrator.ADDED_SECTIONS`, which copies it across
  once when the server's config lacks it.
- **Saving.** Player data autosaves every five minutes and on quit.
  Anything with its own file (First 10, crates, plots, top heads) saves
  when it changes.

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
- **Worlds that load late.** A Multiverse world loads after the plugin
  enables, so `Bukkit.getWorld` is null while plugin files are read. Keep
  such lines as unresolved, write them back on save, and pick them up in
  `listeners/WorldLoadListener`. Until V133 farm plots were dropped
  instead, and the next save erased the farm on every jar update.
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
- **Backslashes in tool commands.** The command tool turns `\\` into a
  single backslash before bash sees it, so a sed or perl pattern meant to
  match a literal backslash (like the escaped dash code in Java source)
  silently matches nothing. Match it with `.` instead, and always grep
  afterwards that the change happened. V103 claimed a fix that never
  landed this way.
- **Display entities copy their spawn rotation.** A display spawned at
  `player.getLocation()` takes the player's yaw and pitch. Spawn aura
  pieces at a location with both set to 0, or they hang tilted.
- **Worn auras** live in `aura/`. Pieces are display entities mounted on
  the player as passengers. Text pieces orbit by putting a glyph off
  centre with spaces and spinning the display (slerped, at most 120
  degrees per update); item pieces move their translation in small steps.
  The tag rarity picks the look from `auras:` in config; `/rngadmin
  auratest list` shows every concept and accent.
- **Changing a default outside a structural section.** The live config
  keeps its old value, so add a one-off patch in `ConfigMigrator`:
  `Patch` for a fixed path, `EntryPatch` for one field of a list entry
  by id (the guide quests). Both only replace a value that still equals
  the old default. V114 moved skills and left the live guide hints
  pointing at the old spots until V116.
- **Roll showcase** (`roll/RollShowcase`) rides the player with billboard
  CENTER, and its translation is in the camera's frame (minus Z ahead,
  minus Y down the screen), so the client pins it to the screen without
  lag. Teleporting it every two ticks trailed every head turn. It sits
  low because the client pins the title and subtitle to the middle of
  the screen and nothing can move them. The reel lands at
  `REEL_LANDS_AT` (78%) in `RollListener` and holds the rest of the roll.
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
- **Minehut file handling (21 September 2026, a morning lost to it).**
  The file manager only works while the server runs, and the running
  server writes its loaded world back on stop, over anything uploaded
  in between. So a new world goes in under a NEW folder name with
  `level-name` pointed at it, then a real stop and start. Uploads refuse
  a name that already exists ("this file already exists") and unzip
  silently skips existing files, so delete first. Uploads land in the
  folder being viewed: a jar dropped in the root is never loaded. When
  something looks lost, a real stop and start comes before anything else.
- **Java 21 only.** Leon's server runs Paper on Java 21, and a jar
  compiled for a newer release does not load. The VS Code Java upgrade
  tool creates `appmod/java-upgrade-*` branches that switch everything to
  Java 25 and leaves the repo checked out on them. Check that
  `git status -sb` says `main` before committing, and never merge those
  branches.

## Building

`mvn -B clean package` with JDK 21. Maven is not installed locally, so
CI (`.github/workflows/build.yml`) builds the jar and uploads the
artifact `SpaceRNG` (a zip holding `SpaceRNG.jar`). For a local compile check, the VS Code Java
extension ships a JDK 21 at
`~/.vscode/extensions/redhat.java-*/jre/*/bin/javac.exe`; put every jar
in `~/.m2/repository` on the classpath (skip the old adventure 4.13.1
jars) and run `javac --release 21 -encoding UTF-8` over `src/main/java`.
The Paper API jar there can also be read for name checks.

## Open

- Pets: Leon said later. Perks shipped in V90.
