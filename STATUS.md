# Where SpaceRNG is right now

Written for whoever picks this up next, Leon or a fresh Claude session on
another machine. Read this before proposing work. `CLAUDE.md` holds the
rules and the house style; this file holds the state, and it is the one
that goes stale, so update it at the end of a working session.

Last updated at **V159**, 19 September 2026.

## The agreed way of working

Leon said it plainly: too many things were half done at once. So:

> **One subject per jar. Nothing new is started until the last thing is
> confirmed working in game.**

He tests every jar himself and reports back. Build what the current step
asks for, then stop and wait for his answer rather than moving on.

## The step plan

**Step 0, waiting on Leon: does the base work?** Ranks came back
working. The rest of the checklist below is still unanswered, and the
pets half of it now has a whole system on top of it (V152), so a "no" on
pets is worth more than it was.

**Step 0b, new in V152: does the dust loop work?** `/rngadmin dust
cosmic 100`, then `/pets` and the star in the top right. Make a pet,
right click it, buy a rarity and try a tier. Then check the real drop
rate by rolling with `cosmic_root` bought.

**Step 1: make the Server First 10 show good.** One subject. He looks at
a preview, says what is wrong, it gets changed, repeat. Nothing else in
the same jar.

**Step 2: finish pets.** Everything is built except how a pet is earned,
which is his decision. See the open questions.

**Step 3: switch the Discord bot on for real.** `/rngadmin discord setup`,
check the roles, try the slash commands. Testing, not building.

**After that:** his own hub world (decided: only cosmetic, it cannot pull
players back after a restart on Minehut), and a build for the spawn and
farm area.

## Step 0 checklist, to run on a fresh restart with V150

1. `/rngadmin boss here`, then `/rngadmin boss start`. Does the boss
   appear, is there a bar at the top of the screen, does it go down while
   harvesting?
2. `/rngadmin pet give all`. Does it say 9 rather than 0, are they in
   `/pets`, do they circle the player?
3. Look at the farming podium hologram. Big enough now?
4. Roll something and read the hotbar line. Are the gradient colours right?

5. (V153) Is the podium's "Payout locked x/10" line right after a
   restart? It counts the day's peak, sampled every 30 seconds.
6. (V156) Link a Discord account that never linked before: does the
   broadcast show and does the tag turn blurple?

Seen working on 19 September: the boss panel and particles, the podium
payout line (layout fixed in V157).

A "no" on any of them is the next thing to fix, and only that.

## What shipped but has never been tested in game

Everything from V140 to V152 was built and compiled but never seen
running, except ranks, which Leon confirmed working on 18 September. In rough order of risk:

- **The Discord bot (V145 to V147).** Slash commands and role sync ride
  on DiscordSRV's JDA. It needs the bot invited with the
  `applications.commands` scope and the bot's own role above the roles it
  hands out. Without DiscordSRV installed the whole thing is never built.
- **The boss event (V140, V141, V144).** Per player, health counted in
  crops, pays Boss Boxes.
- **Pets (V142).** Three slots in the aura, a percentage each.
- **The First 10 star (V148, V150).** Block displays, the drop held in
  the middle, two titles, blindness.
- **The Luck curve (V148).** Each rarity takes its own power of
  (1 + Luck), so Luck never saturates. `luck-curve: linear` restores the
  old behaviour.

## What was added, V140 to V150

One or two lines each. The commit message for a version is the real
record and says WHY, not only what: `git log` for the list,
`git show <hash>` for one of them. The code and the comments in
`config.yml` are the next layer down.

- **V151** plugin.yml reads `${project.version}`, so `/version SpaceRNG`
  reports the real build. Since V134 the jar is always SpaceRNG.jar and
  the version had disappeared from everywhere a person could see it.
- **V152** Pets grow. Cosmic Dust falls while rolling (1 in 100 at the
  `cosmic_root` skill), ten make a pet, and more buy rarity. Farm Dust
  falls while harvesting (1 in 2000 at `farm_dust_root` in /farmtree) and
  buys tiers, which can fail. Shiny needs a shiny of the pet's own
  rarity. Slots start at 1 and climb to 3 in the tree. New pages:
  skilltree 5 "Cosmic", farmtree 4 "Cosmic Soil". Bosses can now turn up
  on their own (`boss.natural`) and the Boss Summoner consumable forces
  one for everybody.
- **V153** Free 2x Luck when the server fills up (`boost.crowd`): 20
  players to start, then 30 percent more (at least 5) after each one,
  2.5 hours cooldown. The farming payout only pays when the day's peak
  hit 10 players (`leaderboard.farming.min-players`). Rank prices
  1200 / 3200 / 7000 Credits.
- **V154, V155** The podium shows "Payout locked 1/10 players today" or
  "Payout unlocked".
- **V156** A chat broadcast the first time somebody links their Discord,
  and the Linked tag in Discord blurple.
- **V157** Podium heads bigger (6.0) and spread so the tags never
  overlap; the payout line no longer runs into the title. Boss panel in
  white with new wording. Roll chat says [Common] rather than [COMMON].
- **V158** Luck fixed: Epic and up are exactly (1 + Luck) times likelier;
  Common is the remainder. Before, a negative Common exponent shrank the
  total and inflated everything else about twice over. 10x Roll and
  Supercharge multiply the chance, not the Luck number. Nerfs: armor Luck
  per piece, Index Luck, index completion, Explorer. Ranks 1.1/1.25/1.5.
  Nova Core Luck is a full multiplier. Index milestones from 80. Free
  pass Credits. No hunger. /link links. One draught kind at a time.
  `%solrng_draught%` for TAB. `/rngadmin odds [rarity] [luck%|me]`.
- **V159** Enchants to level 10,000 (Credit Finder stays 1,000), chance
  enchants bend toward `farming.proc-cap` (50%). Tool tiers give more
  proc. Shift-click buys max in both trees. /convert rebuilt with Convert
  all. Respec is its own confirmation screen. Page 1 skill prices
  smoothed.
- **V140** The boss event: a server event, not a mob, standing over a
  spot and losing health to harvests and rolls. `/boss`.
- **V141** Rebuilt per player. Everyone fights their own copy, health
  counted in crops, and the timer became a command that survives a
  restart because it lives in `boss.yml`.
- **V142** Pets. Three slots in the aura at 120 degrees, a percentage on
  one stat each, `/pets`, and `/rngadmin pet give` until there is a way
  to earn them.
- **V143** The Discord server card, posted to the webhook on command.
- **V144** The Boss Box: the boss pays a box rather than loot, and
  crates gained two reward types, `tickets:` and `boost:`. A key whose
  crate was never placed now opens in the hand.
- **V145** The Discord bot on DiscordSRV's own JDA: slash commands, and
  roles that follow a rank or a link. `discord.info` became
  `discord.cards`.
- **V146** Slash commands registered one by one, so the guild's other
  commands are not wiped.
- **V147** `/rngadmin discord setup` makes the rank roles itself and
  remembers the ids in `discord.yml`. The box can pay permanently.
- **V148** Luck stopped saturating: each rarity takes its own power of
  (1 + Luck). Action bars send real components, so gradients are not
  read as six old colour codes. `Lore.shorten` carries one decimal. The
  First 10 star. A bigger farming podium.
  `/rngadmin advancements off`.
- **V149** A test aura is no longer paused near the farm, which is why
  every `auratest` looked like it did nothing.
- **V150** The config migrator asked the jar's defaults instead of the
  disk, so no added section was ever written and `boss.types` and
  `pets.types` loaded empty. Boss Box pays Money and real permanents.
  The First 10 holds the drop itself and lands on two titles.

## Open questions for Leon

1. **How is a pet earned?** Eggs from rolls that hatch, straight from
   crates, or an index page completed. Everything else about pets is done.
2. **Boss Box contents.** Credits, Perk Tickets, timed boosts and two
   permanents (+10% Luck, +10 Speed) are in. He tunes the weights.
3. **Milestone Credits.** Top rank is now 7000. Either lower the top
   milestone reward to 100 and add more paying tiers, or scale every
   milestone by 0.4. Not changed until Leon picks.
4. **Custom heads for crates and items.** The plugin cannot take a head
   texture from config yet; a `head:` key taking the minecraft-heads.com
   Value is the proposed way.
5. **A spawn build.** Advice given: buy or download a schematic for the
   spawn, and have the farm area and podiums generated by code.

## Decisions already made, do not reopen

- **Credits are Leon's call.** They are in the Boss Box because he asked
  for them there.
- **Pets live in the aura's three slots**, not as mobs walking behind the
  player, and they pay a percentage on one stat rather than a multiplier.
- **The boss is per player**, not one shared health pool.
- **No second Discord bot.** It rides on DiscordSRV.
- **The plugin cannot pull players back after an update on Minehut.**
  That needs control of the proxy, which means self hosting.
- **The jar is always `SpaceRNG.jar`**, one file, uploaded over the old
  one. Never two.

## The commands added in V140 to V150

```
/rngadmin boss here                  where the boss stands
/rngadmin boss process start|stop    the two hourly timer, kept in boss.yml
/rngadmin boss start [type]          one now; no type means a random one
/rngadmin boss stop                  end the one that is up
/boss                                a player's own crops, time and standing
/pets                                the three slots and every pet
/rngadmin pet give|take <id|all> [player]
/rngadmin discord setup              make the rank roles in Discord
/rngadmin discord card <server|link> post a card to the webhook
/rngadmin advancements off|on        hide every vanilla advancement
```

In Discord, once the bot is up: `/stats`, `/top`, `/online`, `/boss`,
`/link`.

## Files a new machine will not have

The git repo carries everything that matters: the code, `CLAUDE.md`, the
skills under `.claude/skills/`, and this file. What does **not** travel:

- the conversation history and any `--resume` session,
- the memory folder under `~/.claude/projects/...`,
- `config.yml`, `boss.yml`, `discord.yml` and the player data, which live
  on the Minehut server and not in the repo.

That is the reason this file exists. Keep it current.
