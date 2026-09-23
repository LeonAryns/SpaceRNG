# Where SpaceRNG is right now

Written for whoever picks this up next, Leon or a fresh Claude session on
another machine. Read this before proposing work. `CLAUDE.md` holds the
rules and the house style; this file holds the state, and it is the one
that goes stale, so update it at the end of a working session.

Last updated at **V192**, 23 September 2026.

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

**Done in V186: almost all of it, in one jar, because Leon said "doe
voor v186 alles".**

- **The chat.** The prefix was on `AsyncPlayerChatEvent` while the tag
  expansion was on Paper's own `AsyncChatEvent`. Paper picks one path and
  it picked the modern one, so `setFormat` was never read by anybody and
  no prestige, no tag and no rank colour ever reached the screen. It is
  one listener on `AsyncChatEvent` with a renderer now.
- **An Enchant potion was raising Coins per crop.** `powerOf` multiplied
  every enchant by the proc multiplier, including the always on ones. A
  thing that fires on 100% of crops has no chance to raise. Proc Chance,
  the hoe tier and the potion now all skip `TOKEN_GREED` and `MOMENTUM`.
- **Prices.** Auto Roll 1000, Auto Convert 10000, Shiny Unlocked 100000,
  Armor 10000, Farming 10000. Tag Luck sits under Index Luck I again,
  the two swapped prices with their slots, and Luck II moved behind
  Index Luck I so the spine still climbs.
- **Crop milestones.** Ten times the old thresholds, 1,000 up to
  10,000,000, and not one Coin in the list: 10x and 100x Rolls, potions,
  Nova Cores, Perk Tickets and a permanent Luck at the top. Milestone
  tiers take a `tickets:` key now, and there is a `roll_100x`
  consumable.
- **Credits.** The first Credit reward on every track is 100 and never on
  the track's opening tier. The tiers behind each first one were raised
  only as far as they had to be to keep climbing.
- **Starforge tier 3 pays +5 Speed** rather than 0.05, as asked. Speed
  divides the roll time, so that is a roll six times faster at tier 3,
  quicker than every tier above it. Left as asked.
- **The hoe's enchant proc** climbs at 0.18 of the Coin step rather than
  0.07, so a fully walked ladder is about 1.50x rather than 1.20x.
- **Enchants really reach 10,000 levels.** They always did in the jar;
  `farming.enchants` was not a structural section, so a live config
  predating V159 kept its 1,000. It is structural now, the enchants only.
- **Menus.** A Farm Tree button in the middle of the hoe screen's bottom
  row with Your Coins moved to its far left; clicking a locked enchant
  opens the farm tree instead of refusing; Auto Convert is a chest
  minecart; the Coins sprite is a gold ingot; `/tag` with no arguments
  opens the index.
- **Nova Core.** Tier 1 cannot fail, ever, not only the first forge, and
  the menu says "certain" rather than a number the forge will not use. A
  climb says "Your Nova Core is now Tier N". The forge button wears the
  Core's own gradient and explains itself before it quotes odds.
- **The Server First run-up** is five, seven and ten seconds, and the
  opening is three rings going outward at three heights with a column of
  light standing in the middle of them, so there is something to turn
  round for before the banner lands.
- **The tab list** has a header and a footer at last, in `tab:` in
  config, with per player tokens. Switch `tab.enabled` off if the TAB
  plugin is ever installed, because it owns the same two blocks.

**V187 undid two things V186 should never have done.**

- **The whole `skilltree` section is back to exactly what it was.** Leon
  asked for five prices and for Tag Luck to move, then looked at the
  result and said the tree should stay as it was: some nodes to level 10,
  some a plain unlock. Every price, every slot and the requires chain are
  byte for byte the V185 file again. The five prices he originally asked
  for went back with them, so if he wants those they go in on their own.
- **`farming.enchants` is not a structural section and must not become
  one.** V186 put it there to carry the 10,000 level ceiling across, but
  `ENCHANT_PATCHES` has carried that one `max-level` at a time since
  V159, and a structural rewrite takes `base-cost`, `cost-linear`,
  `cost-step` and `base-cap` with it. Those are Leon's numbers. The
  ceiling works out to exactly 10,000: `base-cap` 100 plus two Enchant
  Mastery nodes worth 990 x 5 each, and `farmtree` is structural so those
  arrive on their own.

**V187 also added the rank badge.** One letter in brackets in front of a
name in tab and in chat, the letter in the rank's own colours: `[L]`,
`[C]`, `[N]`, `[S]`. It is `ranks.tiers.<id>.letter` in config, and a
rank with nothing set wears the first letter of its own name, so a staff
rank added later needs no extra key. It replaced the symbol tab used to
carry, because a symbol says somebody has a rank and a letter says which.

Handing ranks out, which was the other question:

```
/rngadmin rank set <linked|comet|nova|supernova> [player]
/rngadmin rank clear [player]
```

**And a V185 mistake found on the way:** the Nova rank's blurb was
written into `perks.types.nova` instead, because the perks section has a
`nova:` of its own and it comes first in the file. Both are correct now.

**V188: /cosmetics.** One menu for everything a player wears that
changes nothing, which is the only kind of thing a rank should sell.

- **The hub** says what you are wearing and opens three pickers. Locked
  things are drawn rather than hidden, so somebody with no rank can open
  it and see what a rank would give them, and the bottom right panel
  lists every rank's aura scale next to its name.
- **Auras** is the existing /aura screen, reached from here too.
- **Titles** are words in front of a name, in chat and in tab, given out
  and never bought. `Beta` is the first, with `auto-grant: true` so
  everybody who logs in picks it up; switch that off when beta ends and
  the people who have it keep it. `/rngadmin cosmetic give|take|list`.
- **Name colours** are six gradients, and picking one needs the rank
  perk that already existed, `rgb-name`, which today is Supernova.
  Nothing picked is the drifting rainbow that rank always had.

Everything is `cosmetics:` in config, so the titles, the colours and the
wording are Leon's. Player data gained `cosmetic-tags`, `cosmetic-tag`
and `name-colour`.

**V190: why the farm enchants were never at 10,000.**

`maxLevelFor` returns the SMALLER of `max-level` and
`base-cap + Enchant Mastery`. Every enchant shipped with `base-cap: 100`,
so the rack read "x / 100" however many times the max-level was pushed to
10,000, and the only way past it was two Enchant Mastery nodes costing
4.7 and 7.7 billion Coins. `base-cap` is 10,000 now (1,000 on Credit
Finder, which pays Credits), carried across by a patch per enchant the
same way the max-level was. The price curve is the balance, which is
what it was always for.

**That leaves Enchant Mastery I and II buying nothing.** They are still
in the farm tree at page 3, still cost billions, and now close a gap that
is already closed. Leon has to say what they should do instead, or
whether they come out.

**V190 also:**

- **Golden Touch and Harvest Echo are gone**, enchant and node both. Gem
  Rush moved into Golden Touch's slot at the top of that column with its
  way in, Alchemy comes off `hoe_tier_2` and `crop_beetroot` directly,
  Nether Wart no longer needs Harvest Echo, and nothing is left pointing
  at a node that does not exist.
- **Gem Greed, Gem Rush and Gem Cascade say Coming soon.** They are drawn
  on the rack and in the tree, refuse every click, and pay nothing.
  Momentum moved off Gem Greed and Coin Storm off Gem Cascade, so nothing
  sits behind an enchant nobody can buy. An enchant taken out of the jar
  is still in a live config, so `ConfigMigrator.REMOVALS` deletes a path
  once and remembers it, which is new.
- **The cosmetic title sits after the name** rather than in front of it,
  and a Supernova who paints their name gets the rank letter painted to
  match.
- **The join and quit lines are config**, under `join:`, with `{name}`
  and `{plain}`. Empty means no line.
- **The Nova Core is an ender pearl.**
- **Sprites work in a description.** `Lore.lore(plugin, meta, lines)`
  renders `Icons` markers into component lore, with italic turned off per
  line because component lore tilts by default and legacy lore does not.
  Your Coins in the hoe screen is the one sample; say where else.

**Still open from the same message:** /store and /buy rebuilt in the
description block style, and better descriptions on the Nova Core tier
items.

**V191: the rank multiplier finally reaches Speed.**

There were two Speeds. `StatSources.speed` had the rank, the perks, the
pets, permanent Speed and Autopilot in it and drove the roll timer;
`PlayerData.getEffectiveRollSpeedMultiplier` had none of them and drove
the sidebar, the skill tree panel and `/rngadmin stats`. So a Supernova
rolled 1.5x faster and every number they could read said otherwise. The
second one is deleted and all three read `StatSources.speed` now, which
is the one definition CLAUDE.md asks for.

**V191 also:**

- **The Base row is gone from /stats**, on every stat that had one:
  Speed, Enchant Proc and Shiny. `Part` carries a `shown` flag and the
  base is the only thing that sets it false, so it still folds into every
  total and the running column down the breakdown is unchanged. It is not
  a source anybody can go and get, so it does not belong next to the ones
  they can.
- **The Battle Pass costs 50,000** rather than 3,750.
- **Money II and Vault Space swapped places** on the right column of page
  1, with the requires chain rewired and every price left where it was.
- **Five Server First spots per rarity**, not ten.
- **/firsts** is a board of who holds them: your own spots on your head at
  the top, then one card per tracked rarity listing every holder with
  what they found, and how many spots are left. Nothing in it is
  clickable.

**V192: five more combined aura looks.**

`orrery`, the atom and armillary combination Leon asked for, has been in
since V184 and is in `/rngadmin auratest list`. Five more stand beside it
now, each built by putting existing pieces together rather than drawing
new ones, and each leaning on a different axis so no two read alike:

| Look | What it is | Leans |
|---|---|---|
| `zenith` | the armillary rings, a crown over the head, a column nine blocks up | vertical |
| `lattice` | a cage of bars between two solid circles, stars turning under it | boxy |
| `aurora` | three wide faint curtains sweeping round you over a floor of stars | wide and slow |
| `tempest` | a funnel of five circles with the rarity's gems round the waist | fast and narrow |
| `cradle` | two crossed standing rings holding a lantern atom and an outlined star | close in |

Try them with `/rngadmin auratest <name> divine`, and remember the test
obeys the own aura view in /options since V184, which the command prints
when it starts.

**Deliberately not done in V186, and why:** the +10% Coins and Gems per
crop. Those are the `CROP_YIELD` nodes, and they are the spine of the
farm tree: `golden_crop` and the crops behind them require
`yield_wheat`, `yield_carrots` and `yield_potatoes` by name. Taking the
effect away leaves nodes that cost 6,500 Coins and pay nothing; taking
the nodes away breaks the chain. **Leon said he wants to put something
else there, so the reminder is here: crop level.** Tell me what the node
should do and it is a ten minute change.

**Next up, in this order unless Leon says otherwise:**

(Most of the list below shipped in V186; what is left is called out
above.)

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
