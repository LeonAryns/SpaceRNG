# Where SpaceRNG is right now

Written for whoever picks this up next, Leon or a fresh Claude session on
another machine. Read this before proposing work. `CLAUDE.md` holds the
rules and the house style; this file holds the state, and it is the one
that goes stale, so update it at the end of a working session.

Last updated at **V185**, 23 September 2026.

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

## The backlog Leon gave on 23 September

Three messages in a row, so it is written down here rather than left in a
conversation that does not travel. One subject per jar still holds: this
is the queue, not one jar. Nothing here is started before the jar in
front of it comes back confirmed.

**Done in V184: the auras.** The five aura points and the new `orrery`
look. See the aura section below.

**Done in V185: how a thing being sold is described.** Leon brought two
screenshots of another server's rank menu and asked for its description
next to our title. The shape is now written down in
`.claude/skills/menu-design` as the description block: name, a subtitle
that says what kind of thing it is, two lines of pitch, the perks one
per line, commands on a single line under their own header, the price as
a stat line, and the action footer. `/ranks` is built that way, and it
is the pattern the Nova Core menu should follow when his screenshots
arrive.

The skill also carries a length limit now, which the reference
screenshot is the argument for. Minecraft draws a tooltip at the cursor
and cuts off whatever does not fit rather than fitting it to the screen,
and where that happens depends on the player's resolution and GUI scale
(MC-26757, MC-161929, MC-253053, all open). That screenshot runs about
28 lines and is clipped at both ends, so the name and the price are the
two lines a player cannot read. Ours is 19 at the top rank and 14 at the
bottom. Each rank's pitch is `ranks.tiers.<id>.blurb` in config, two
lines, so Leon can word them himself.

**Next up, in this order unless Leon says otherwise:**

1. **The Server First show.** The star and the beam are good. He wants
   more particles in the opening seconds, so people have time to look
   around and find it, and the whole run-up longer. It is 3 / 4 / 6
   seconds now (`FirstTenBuildUp.length`), shortened in V158 because it
   dragged; he is asking for the opposite now, which is his call.
2. **The crop milestones.** A lot higher than they are. No Coins as a
   reward at all: Credits, 10x roll, 100x roll, potions, a Nova Core,
   Perk Tickets. The first Credit reward is 100 Credits everywhere, but
   not on the very first milestone, so it takes a little work.
3. **The farming numbers.** Drop the 10 percent Coins and Gems per crop
   (he wants something else in that spot and will say what; remind him
   about crop level). Raise the enchant proc boost on the hoe. Enchants
   really to 10,000 levels, and balanced at that ceiling.
4. **The skill tree prices.** Auto Roll 400 to 2000. Auto Convert 2140 to
   10000. Shiny Unlocked 6560 to about 100000. Armor and farming a bit
   more expensive. Tag Luck (`index_luck`) sits under Index Luck I
   (`curator_1`) rather than above it.
5. **The menus.** A Farm Tree button in the hoe enchant screen, bottom
   left where Your Coins is, and Your Coins moved. Clicking a locked
   enchant opens the farm tree. Auto Convert in /convert stops being a
   hopper. The Coins icon becomes a gold ingot, which is bigger. `/tag`
   with no arguments opens the index.
6. **Nova Core.** Tier 1 always succeeds. A chat line when the tier goes
   up ("Nova Core is now Tier 1"). The menu rebuilt properly: gradients,
   better descriptions, in the V185 description block. He is sending
   screenshots of the layout he wants, so the layout waits for those.
7. **The TAB list.** He asked for this a while back and believes it was
   never done. Check what is there before rebuilding it.

**Two bugs he reported, both need answers before they can be fixed:**

- **An enchant potion raised his Coins per crop.** A potion is meant to
  raise the CHANCE only. An enchant that already procs at 100 percent,
  like Coins 1 or Coin Greed, must not pay more because a potion is up.
  Find where the potion multiplier is applied and make sure it only ever
  touches the proc roll.
- **The chat.** "De chat is nog niet geregeld", and he thinks it was
  reported fixed. Nobody has written down what is wrong with it, so ask
  before touching anything.

**Answered, so it does not get asked again: the scoreboard icons cannot
be made bigger.** They are 1.21.9 sprite objects in text
(`Icons.sprite`, `ObjectContents.sprite`). The component carries an atlas
key and a sprite key and nothing else, no scale, verified against
adventure-api 4.26.1. The client draws one at the height of a line of
text and the server cannot change that. The only route to bigger icons is
a server resource pack with its own bitmap font, which is a project of
its own.

**Open, waiting on Leon:**

- "Het geld mag wel op 1k starten." Which money: what a new player starts
  with, or the price of the Money I node (1220 now)?
- "In starforge mag hij +5 speed hebben in plaats van 5%." Which tier?
  `speed-bonus` is a flat add to the Speed pile, so 0.05 is what reads as
  5 percent somewhere. Say which screen shows it wrong.
- Armor and farming "wat duurder": how much, roughly double?
- Icons inside menus as well as the sidebar: he wants to see it before
  deciding, so one screen gets them as a sample.

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
7. (V179) `/rngadmin firsts preview <rarity>`: is the star one clean
   outline, is it visible through terrain, and does the column of light
   reach the ground?

Seen working on 19 September: the boss panel and particles, the podium
payout line (layout fixed in V157).

A "no" on any of them is the next thing to fix, and only that.

## The auras and the visuals, V174 to V183, the current subject

Leon said on 22 September that the auratest looked a lot worse than it
used to, and asked for the best look each rarity can have, using
everything a display entity can do.

**The thing that was missing.** Every aura was drawn out of star glyphs
and item models. A glyph takes any RGB but is always a star; an item
model is any shape but only Mojang's colours. A third piece was never
used: a text display holding one space with its **background** painted.
That is a rectangle in any RGB at any alpha, and a matrix in front of it
makes it a ring segment, a flame, a feather or a column of light.

`AuraParts.plate` draws one. `UNIT_QUAD` maps the painted background of a
single space (0.125 blocks wide, 0.25 high) onto a one by one square;
the constant is confirmed against two unrelated public projects,
TWME-TW/TextDisplayShapes and TheCymaera/minecraft-text-display-
experiments, which derive exactly the same matrix. `AuraParts.block`
adds block displays and `AuraParts.glowing` a coloured outline through
walls. `SignatureConcepts` holds the pieces (PlateRing, Spokes, Petals,
Column, Core, PlateWings) and the eight looks.

| Look | Worn by | Heavy |
|---|---|---|
| sigil | Epic tag | no |
| ember | Legendary tag | no |
| eclipse | Mythical tag | no |
| ascend | Divine tag, and the heavy fallback | no |
| prism | Epic shiny | no |
| pyre | Legendary shiny | no |
| rift | Mythical shiny | yes |
| empyrean | Divine shiny | yes |

**Three bugs under it, all of them making every aura worse:**

- Teleport duration was 3 on every piece. It smooths a display's own
  position over that many ticks, and a piece riding a player is moved by
  the ride every tick, so the aura swam behind the wearer while walking.
  It is 0 now, and only pieces of a look that turns with the body get 3.
- One piece falling off rebuilt the whole look, which put every piece
  back at its spawn pose in the middle of its orbit. Pieces that only
  came off go straight back on, and a real rebuild restarts the frame
  count so pose and count agree.
- `AuraParts.move` threw the wearer's size away, so a /size player's aura
  sprang back to normal on its first move.

**V176: both faces.** A text display is drawn on one side only. The wiki
says it plainly ("the displayed text is only visible from one side"), and
TWME-TW/TextDisplayShapes carries a `doubleSided` option for the same
reason. So every plate that can be walked round is now drawn twice, the
second turned a half turn about its own upright axis and set four
millimetres behind the first: rings that stand or slant, halos over the
head, flames, and every feather of a wing. Rings lying at the feet stay
single, because they are only ever looked down on. Segment counts came
down to pay for it, which costs nothing on a broken ring since the gaps
are the point.

**V177: billboarded pieces read their offset in the camera's frame.**
CLAUDE.md already wrote this down once, for `RollShowcase`, which uses it
on purpose to pin itself to the screen. Under any billboard but FIXED the
client applies the billboard turn first and the transformation inside it,
so a piece given an offset does not sit at that offset from the wearer,
it sits at that offset from the middle of the viewer's screen and follows
them around.

Every nether star in `galaxy-grand`, `nova-grand`, `singularity`,
`singularity-lite` and `titan` was billboarded CENTER at a radius of two
to five blocks, so none of them has ever orbited anything since V118.
They are turned to look outward along their own radius now, with no
billboard, which is also what stops them going thin edge on. Only a piece
standing on the wearer's own axis can be billboarded, and only VERTICAL,
which leaves the upright axis alone.

**V178: the same one sided rule caught the old star looks.** Three of
them draw their glyph cards on a plane that is not flat: `orbit` and
`helix` stand theirs upright, `atom` slants its three. Every one of those
cards was missing from behind. `atom` has claimed to be "solid from any
side" since V107 and was not: two crossed planes still leave a quarter of
the directions you can stand in with nothing facing you, which reads as
the atom fading in and out as you walk round it. All three draw both
faces now, so `celestial`, `seraph`, `cosmos` and `atom-hybrid` come
right with them. Every other star look lies flat at the feet and was
fine. The back of a `pair` card is the same card turned about its own
upright axis; the back of a `single` card also needs `singleBack`, which
moves the glyph to the other side of its own text so the turn puts it
back where it was.

**Found and deliberately not changed:** `TagManager.spawnLine` has the
same pattern. The floating tag rides the player with billboard CENTER and
its line spacing in the transformation's Y, so the lines shear away from
the head when a viewer looks steeply up or down at somebody. The fix is
one word, `CENTER` to `VERTICAL`, at the cost of the tag no longer
tilting toward a viewer who is above or below. Leon's call, and it is the
tag rather than the auras.

**V179: the star, the pets and six more looks.**

The Server First star was ninety glass cubes strung along the edges of a
five pointed star. It is ten stretched beams now, one per edge, which is a
clean unbroken outline for a ninth of the pieces, and each beam carries a
glow override in the rarity's exact colour so the star is seen through the
terrain from any corner of the map. A Server First happens ten times per
rarity for the life of the server and then never again, so nobody should
miss one for standing in the wrong place. A column of light runs from the
star down to the ground, breathing, so the spot can be walked to rather
than only looked at.

Pets were three item models and nothing else, so a Common pet and a Divine
one were the same sight unless you already knew the item. Each relic now
rides on a painted card in its own rarity's colour, drawn on both faces,
and the three bob a third of a cycle apart so the orbit reads as alive
rather than as a turntable. A shiny pet carries an outline in the same
colour, through walls.

Six more looks to pick from, all built out of the plate pieces:
`armillary` (three rings at three angles, each swinging its own plane),
`vortex` (five circles narrowing up the body), `cage` (eight bars between
two solid circles), `shield` (six wide faint panels at the hips), `beacon`
(a column of light nine blocks up) and `portal` (a standing ring with a
second inside it and an eye). `Bars` is the new piece: narrow and tall it
is a cage, wide and faint it is a shield.

**V180: a circle under every roll.** The reveal was particles and
nothing else, and past twenty blocks a few hundred bright specks read as
weather rather than as a shape. `RollCircle` paints a summoning circle on
the ground under the roller in the rarity's colour: it opens over the
first third, holds while the strands wind up, is dragged inward and spun
faster with the implosion, and is thrown out to twice its width on the
bang. The pieces ride the player as passengers like a worn aura, carry
the aura tag so the startup sweep clears them, and honour the same per
rarity switch in /options. Only Epic and up roll an aura, so it is built
a few times a day rather than constantly.

`AuraManager.parts()` is public now, so anything outside the aura package
that draws display entities on a player gets the same tagged, swept,
size aware pieces instead of its own copy of the spawn code.

**V181: the boss stands in an arena.** A boss was one item model turning
over a spot with a ring of dust running out of it, which reads as a
floating icon rather than as a server event, and the dust is gone past
twenty blocks. `BossCircle` paints the ground: a solid circle five blocks
out marking where it is, a second circle inside it that loses a segment at
a time as the clock runs down, and a slanted ring turning round the body.

The clock ring is the point. Every fighter has their own health, so the
panel can only say what the event is and never how anybody is doing. The
one number the whole server shares is the time left, and a circle that
visibly opens up says it from across the map without anybody reading a
word. Only the segments that change are rewritten.

`AuraParts` gained `plate(Location, ...)` and `block(Location, ...)`, so
anything that marks a place rather than a player draws with the same
tagged, swept pieces. View range is given in blocks there and divided by
64 inside, which is the unit a display actually wants.

**V182: the crate and the shiny get a shape too, and the ring code stops
being written a fourth time.** `AuraParts.ringSegment` is the one place a
flat ring segment is worked out now; `RollCircle`, `BossCircle` and both
of the new ones call it. A crate throws a ring outward from its top in its
own colour when it opens, and a jackpot throws a wider one and stands a
column of light over the block, reaching 96 blocks rather than 24, because
a jackpot is worth somebody across the spawn turning round for. The shiny
pre-roll closes a pale aqua ring in round the roller while the helix
climbs and throws it out on the flash, with a timed clear so an abandoned
roll cannot leave one standing.

**V183: the Nova Core forge is no longer one sound.** Forging is the
biggest single gamble in the plugin, it eats a Core and it can send a
player back to their last checkpoint, and the whole of it was a sound and
a line of chat. `NovaForgeFx` gives the three outcomes three shapes that
can be told apart with your eyes shut: climbed throws a ring outward and
sends sparks up, twice as far and with a beacon on a checkpoint; anchored
pulls the ring in and holds it on one flat thud, nothing thrown because
nothing was gained; shattered breaks the ring apart and drops the pieces.
A forge happens with a menu open, so it is drawn round the feet and over
the head where the inventory panel is not.

**V184: what Leon said back about them, and the answers.** He looked at
V174 to V183 and gave five things:

- The `/options` own aura setting did nothing. It did nothing because a
  test aura ignored it outright (V172), and every look he has judged
  since was judged through `/rngadmin auratest`. A test obeys the setting
  now, and the command prints which view is on so nothing is silently
  missing.
- He wants the middle setting to mean ground and sky rather than feet
  only. `AuraConcept.lowToGround` became `clearOfView`, and it is about
  where a piece sits rather than how big it is: floor rings, halos over
  the head, wings off the back and anything past `CLEAR_RADIUS` (1.25
  blocks) are kept, and only what would sit on the wearer's nose goes.
  The three modes are Out of your way, Everything and Hidden.
- The aura is now the thing a rank buys. `auras.rank-scale` multiplies
  every piece's pose through `AuraParts.withPlayerSize`, the one place
  both the spawn and every move already pass through: Linked 0.70, Comet
  0.85, Nova 1.0, Supernova 1.30. Same look, same piece count, same cost,
  drawn bigger. Nova is the middle, so it wears exactly what every aura
  looked like before.
- `archon` and `ascendant` are gone. The standalone `wings` look has the
  same trailing problem archon had and was left alone, because he did not
  ask for it: a piece that turns with the body is told its new yaw every
  two ticks and the client slerps it over three more, so about a quarter
  of a second of lag is the floor for anything hanging off a back.
- `ascend` was good but its beam was not. A `Column` used to be one slab
  from the feet straight through the wearer's eyes. It starts over the
  head now and its sheath is cut into sections that narrow and fade as
  they rise, so it reads as light rather than as a plank. Divine and
  Divine shiny only; the Epic and Legendary shiny columns were not
  mentioned and were left.

**V184 also adds `orrery`,** which is the combination he asked for: the
lantern atom out of `atom-grand` and the three swinging rings out of
`armillary`, over star bands at the feet, with the end rod atom replaced
by nether stars because an end rod is Mojang's white whatever colour the
rest of the aura is painted. About seventy pieces, so the body rings run
seven segments each to pay for both of their faces.

**Still not verifiable from outside the game, so check these first:**

1. Is the plate the size the maths says? A ring that comes out far too
   small or far too wide means `UNIT_QUAD` is wrong for 1.21.11.
2. Do the `empyrean` and `pyre` wings sit on the back rather than in the
   chest? The pivot came from the old stained glass wings.
3. Does the `eclipse` standing ring z-fight with itself? If the two faces
   flicker against each other, `AuraParts.BACK_GAP` needs raising.
4. (V184) Is Supernova's aura obviously bigger than Linked's? Put a tag
   on, `/rngadmin auratest orrery divine`, and compare against a rank
   change. If the difference reads as nothing, raise the spread in
   `auras.rank-scale`.

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
- **V160** A golden crop has 5% for a Farm Dust (needs Farm Dust unlocked).
- **V161** Enchant level screen (+1, +10, +100, max) from the hoe and /farmtree.
- **V162** New player broadcast "(!) WELCOME Name TO SpaceRNG! [#N]". XP bar
  = rolls this prestige, fills toward the next level. Chat tags [item],
  [luck], [speed], [money], [coins], [shiny], [enchant], [prestige],
  [index], [rolls], [stats]. Placeholders %solrng_luck/speed/money/coins/
  shiny/boosts/server_boost%.
- **V163** Sprite icons on the sidebar (scoreboard.icons, /rngadmin icon).
- **V164** Welcome panel (`holo panel welcome`), `/rngadmin farmwheat`.
- **V165-V167** Icon fixes (Luck from the gui atlas, Speed a clock),
  boss bar and sidebar address in white, `/rngadmin farmland`, farmland
  that never dries or tramples.
- **21 Sept:** the server moved to a bought map in the world folder
  `map`. Everything placed in the old `world` (holograms, crates, heads,
  boss spot, farm) has to be placed again there.
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
