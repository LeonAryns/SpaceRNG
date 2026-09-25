# Where SpaceRNG is right now

Written for whoever picks this up next, Leon or a fresh Claude session on
another machine. Read this before proposing work. `CLAUDE.md` holds the
rules and the house style; this file holds the state, and it is the one
that goes stale, so update it at the end of a working session.

Last updated at **V210**, 25 September 2026.

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

**V193: Leon's aura feedback, and two bugs it turned up.**

**The ground stars were sinking into the block.** A text display centres
its line box on its position and a star glyph sits low in that box, so a
card laid flat at the feet renders inside the top face of the block it is
lying on. `AuraParts.flat` lifts a card by `4.5 * 0.025 * scale`, the
same figure `pairStars` already works with, which is why the big rings
vanished while small ones did not. Every flat glyph piece is fixed at
once: the star bands, the runes, galaxy, ripple, pulse.

**`planet` showed nothing** for two reasons at once. It hung at 1.45
above the ride point, which is three and a quarter blocks over the feet,
and it had no `clearOfView`, so the default own aura view hid it from the
only person testing it. It sits at 0.85 now and counts as sky. `crown`,
`blades` and `pillars` had the same missing flag and now say so.

**Three looks built only out of what he named:**

- `seraphim` is wings on their own. Seven narrow feathers a side rather
  than four wide ones, and behind where he is LOOKING rather than where
  his body points, which is a new `followsHead()` on the concept: the
  body yaw lags the head and then snaps to it, so wings on the body swing
  late and jump. The crown he liked stays over the head.
- `heartfall` is the heart out of `cradle`, the lanterns out of
  `galaxy-grand` sailing round it, and the star bands from under
  `atom-grand`. Three things he named and no filler.
- `stardust` is `nebula`'s shooting ground stars under two of `aurora`'s
  curtains.

**Epic's lit block** is an amethyst block rather than a pearlescent
froglight, which read as pale pink next to Epic's violet. Every aura
piece is fullbright anyway, so it glows.

**Also in V193:** the drop behind a name in `/firsts` wears its own
gradient, and `Money` lost its Base rate row in /stats along with the
other three.

**Still Leon's call:** which looks to keep, which shiny slots to put them
on, and whether Legendary and Mythical should swap lit blocks (shroomlight
is on Mythical today, ochre froglight on Legendary).

**V194: the aura height is measured instead of guessed.**

`AuraParts.RIDE` was 1.8 because that is how tall a player is. It is
where a passenger actually attaches that matters, Bukkit exposes no way
to ask, and if the real number is lower then EVERY piece of EVERY aura
sits that much too low. That is one cause behind both complaints that
kept coming back: rings meant for the feet end up inside the block they
lie on, and rings meant for the waist end up in the wearer's eyes.

The first time an aura is worn after a start, the manager reads the true
offset off a mounted piece (`piece.getLocation().getY()` minus the
player's), corrects `RIDE` and `FEET`, logs the number and rebuilds every
worn look once. If 1.8 was right nothing happens. **The console line is
worth reading after the first jar with this in it: it says what the real
number is.**

**Also in V194:**

- **A `/rngadmin firsts preview` looked instant** because the run-up is
  drawn per viewer and skipped anybody with that rarity's aura switched
  off in /options, which included the one person watching. A preview now
  forces itself on whoever asked for it, star included.
- **The detailed wings have tapered tips**: a narrower plate in the edge
  colour carrying on past each feather, so a feather reads as a feather
  rather than as one rectangle in a fan. That is as far as plates go
  without spending pieces by the hundred.
- **`stardust` has three steps of colour** rather than two, because two
  tints one shade apart read as one colour at any distance.
- **`starfall`** is the combination asked for: heartfall's heart with its
  lanterns sailing round it, standing in stardust's shooting stars under
  one curtain. Nether stars and lanterns, no end rods. 39 pieces, the
  cheapest good look in the plugin.

**V195: the comet, and the odds counter under it.**

Leon asked for the Sol's RNG cutscene: a falling star coming down at you
on a rare pull, with the odds climbing on the screen while it falls. This
is the first of three jars for that blueprint. He picked Epic as the
threshold, so every rarity that already has a reveal aura now has a comet
in front of it.

`roll/RollComet` is owned by `RollAura` the same way `RollCircle` is, and
it is the half of the reveal that is meant to be looked AT. Everything
else in a reveal is drawn ON the player, which reads well from outside
and reads as nothing in first person, because all of it is behind or
below the camera.

- **It is the roller's alone.** The head is spawned hidden and shown to
  one player, and every particle and sound goes to that one player.
  Everybody else sees somebody standing still with the usual aura winding
  up, which is what he asked for twice.
- **It comes down in the direction the roller is already facing**, so it
  is in view from the first frame with nothing touching their camera.
  That is the answer to the third person camera in the blueprint: Paper
  has no camera of its own, `setSpectatorTarget` needs spectator mode,
  which would make the roller invisible to everybody and drop them
  through the world, and the raw camera packet is NMS. There is a
  `roll-item.comet.steer-view` switch that swings the view with
  `Player#setRotation`, off by default, because writing a real rotation
  is echoed back by the client and everyone nearby would see them spin.
- **It flies against the ROLL's length, not the aura's.** An Epic
  build-up is three seconds and the roll around it is five or more, with
  the aura holding at full implosion for the rest. Flown on the aura's
  clock the comet would land two seconds early and hang inside the
  roller's chest until the drop arrived, so `RollAura.start` takes the
  roll length now.
- **The counter climbs through the logarithm of the odds**, from 1 in
  1,000 to the drop's own label. A linear climb to one in five million
  sits under a hundred thousand for nine tenths of the run and then
  jumps, so every rarity would read the same for most of its build-up.
  It is on the action bar, because the reel owns the title and the
  subtitle for the whole roll.
- **Its audio stops where the aura's score stops**, at 0.88. A comet
  screaming through the hush would take the silence that makes the
  detonation land, so the last stretch is a silent plunge.
- One glowing `BlockDisplay` head in the rarity's lit block, seen through
  terrain, with a dust trail laid along the ground it covered. Height,
  reach, size and trail all scale with rarity: Epic starts 40 blocks up,
  Divine 90.
- `roll-item.comet` in config, carried to the live server by
  `ConfigMigrator.ADDED_SECTIONS` as a dotted path.

**Two traps it walked into on the way**, both caught before the jar:
a display takes the rotation of wherever it is put, and the comet is
positioned off `player.getLocation()`, so without zeroing yaw and pitch
it tumbled with every turn of the roller's head. And the flight clock,
above.

**Still to come in the blueprint**, one jar each: the egg and the hatch
animation, then the pet slots and the dust counter in `/aura`.

**V196: the reveal climbs the rarity ladder now.**

Leon's answer to V195, before he had it in game: the text belongs in
front of you rather than over the hotbar, the drop has to stay a question
mark until the counter reaches its odds, and the whole thing should start
small and violet and GROW through the bands, popping with a sound each
time the odds cross into the next rarity.

- **`roll/RollStages` is the ladder.** A band's entry is the shortest
  odds any drop in it actually has, read off the item list rather than
  written down twice: Epic 33,000, Legendary 100,000, Mythical 250,000,
  Divine 10,000,000. The rungs are filtered by the ROLLER's own
  `/options`, so somebody who switched the Epic aura off opens at
  Legendary, which is what he asked for.
- **Every stage is that rarity's own look**, the same `colorFor`,
  `maxRadiusFor`, `strandsFor`, `accentFor` and lit block the auras
  already use, so a rung of the climb cannot drift away from the aura it
  is borrowed from. `RollAura` follows the comet up the ladder by polling
  it, and `RollCircle.restyle` repaints the floor where it stands rather
  than respawning it, which would jump the circle back to its opening
  pose mid turn.
- **`roll/RollCounter` is the number, as a display entity**, not a title.
  A title has exactly one size and nothing in the API can change it, and
  this number has to grow. It is pinned to the screen the way
  `RollShowcase` is, just above the middle, and a promotion throws it out
  to 1.5x for three ticks and pulls it back over seven.
- **`roll/MysteryHead` is the question mark**, a player head carrying the
  texture Leon picked, in `roll-item.comet.mystery-head` as the plain
  base64 every head site hands out. The reel used to land on the real
  drop at 78% of the roll, so a fifteen second Divine had already given
  its answer while the comet was still in the sky. On a comet roll the
  reel now hands the screen over entirely and the head holds until the
  impact.
- **The counter finishes before the end, and that matters.** The top
  band's entry is often the drop's own odds exactly, and the only Divine
  on this server is one in ten million where Divine starts. Finishing on
  the last frame would promote to Divine ON the impact and the whole
  build-up would be Mythical red. The climb is pulled forward until the
  last rung lands by 62%, so a Divine reads Legendary at 3.4s, Mythical
  at 4.9s, Divine at 9.3s, and spends its last 5.7 seconds actually
  looking Divine.
- `roll-item.comet.counter-scale` is the one number that cannot be
  judged outside the game. The table behind it is sized against the
  screen: at 1.5 blocks in front of the camera the view is about 3.7
  blocks wide, and "1 in 10,000,000" at scale 1 is 1.6 blocks of text.

**V197: Leon tested V196 and it was wrong in six places.**

His message is the spec for this one, so it is written down: "ik wil gwn
een rarity van bv 20s eerste 5s epic 10s legend 15 mythical 20s divine
waarbij het dus 15s duurt voor een divine als je epic uit hebt".

**The reveal is acts now, one per rarity band, each the same length.**
Five seconds of Epic, and then either it ENDS there and the drop was an
Epic, or it breaks through into five of Legendary, and so on. A Divine is
four acts and twenty seconds; with the Epic aura switched off it is
three acts and fifteen. `roll-item.comet.stage-seconds` is the whole
timing of a reveal. The per rarity durations `RollAura` carried since it
was written are gone, because a roll whose LENGTH came from the drop told
the player what they had before the first act was over.

Each act is a complete build-up: it gathers, charges, implodes and then
either breaks through or is the ending. It plays its own band's score,
wears its own band's colour and size, and launches its own comet from its
own band's height. That last one is the answer to "je weet nu alsnog
wanneer het een divine is": V196 sized the comet's path from the DROP so
it would not jump mid flight, so a Divine started ninety blocks up on its
first frame and an Epic forty.

**The six things he found:**

- **No player head.** A skull is a BLOCK model, and under transform NONE
  a block model is drawn with its corner on the display's origin while a
  flat item is drawn centred. Pinned a block and a half in front of the
  camera the head hung half a model up and to the side, mostly off the
  edge of the screen. `RollShowcase` pulls a block shaped item back by
  half its own scaled size now. The texture route changed as well, from
  Paper's profile property to the one Bukkit specifies, including the
  `setTextures` call back onto the profile that a copy-returning
  implementation needs. **`/rngadmin head` hands you the item**, so a bad
  texture can be told from a bad effect without another jar.
- **The text ran too fast on Epic.** It was one curve across the whole
  roll; now each act owns one leg of the climb, from its band's entry to
  the next band's entry, over a fixed five seconds.
- **The text was too small and too flat.** The scale table is up by about
  half, and every character is mixed between the band's colour and a warm
  near-white on a wave that walks along the number.
- **No odds climbing on Mythical and Divine.** Same cause as the Epic
  one: the old curve finished at 62% and held, so the last band had
  nothing left to count.
- **The Divine stayed red.** The promotion to the top band landed on the
  impact whenever the drop sat on its own band's entry, which the only
  Divine on this server does at one in ten million. With acts it is a
  band boundary like any other.
- **Particles in the face, at every rarity.** Every cloud in the finale
  was centred on the player's chest, which is the one place a first
  person camera cannot see: from outside a burst, from inside a screen
  full of dust. `RollAura.CLEAR` is 2.6 blocks and the clouds are shells
  around the player now, with the middle left empty. The Divine pillar
  skips the two or three points at the roller's own head, the growing
  sphere starts outside it, and the comet's own burst is a ring rather
  than a cloud.

**Still open from this round:** the endings are per rarity in colour,
radius, strand count and, for Divine, script, but Epic, Legendary and
Mythical still share the shape of one. If he wants four visibly different
endings that is its own jar.

**V198: why none of the screen pieces were ever visible, probably.**

Third report of the same thing. Leon sees the comet, the aura and the
particles, `/rngadmin head` hands him a correct question mark head, and
he has never seen the head, the drop or the odds in front of him during
a roll. Everything missing is a PINNED piece and everything he can see is
not, which is the one thing they have in common.

Pinning mounts a display on the player with billboard CENTER and puts the
offset in the transformation, which the client then reads in its own
camera frame. It has no lag, and the convention behind it (minus Z ahead,
minus Y down the screen) is the one thing in the whole reveal that cannot
be checked by reading the API. A sign error there puts the piece exactly
behind the viewer's head, which is indistinguishable from what he
describes. `roll/ScreenSpot` now owns that decision and the default is
**world**: the same spot worked out in ordinary coordinates and
teleported there every tick. One tick behind a fast head turn, and
impossible to get wrong. `roll-item.comet.screen.mode: pinned` keeps the
old way for whoever can stand in game and compare, with `ahead`,
`item-down` and `counter-up` next to it.

**Particles in the face, third time, and this one was a real miss.** V197
fixed the finale only, and the finale was never the half he was standing
in. The BUILD-UP winds its strands in to under a block, and the IMPLOSION
dragged everything into a point at 1.6 above the feet, which is his eyes,
at the end of every act. Both read beautifully from six blocks away. The
cull is per viewer now, inside `dustAt` and `puff`, against each viewer's
own eyes: the effect is untouched for everybody watching and nobody is
ever inside it. The implosion also gathers at 3.0 rather than 1.6, which
is what its own comment always said it did.

**Two smaller ones from the same message:**

- **No sound on the counter.** There never was one. A tick per counter
  frame now, climbing most of an octave across the act, quiet enough to
  sit under the score.
- **The cutscene text was gated twice.** It followed Rolling Animation in
  /options as well as the rarity's aura switch, which was a guess, and a
  guess in the one place that can silently hide the whole thing. It
  follows the aura switch alone now, which is the one the player used to
  ask for the reveal in the first place.

**`/rngadmin reveal` is the new one to run.** It prints every switch that
can hide a piece of this, per player and from config, works out how many
acts and how many seconds each rarity would run for that player, and then
puts the question mark in front of them for fifteen seconds with no roll
around it. Three jars went on guessing at "ik zie het niet"; this is so
the fourth does not.

**V199: Leon was right and V198 was chasing the wrong suspect.**

"doe nog een versie want 198 is weer gevaarlijk en volgensmij moet die
playerhead niet zo moeilijk te zijn want dat is toch gwn hetzelfde als
alle andere items bij de rolling animation."

That last clause settles it. The reel shows its candidate items through
the SAME pinned display on every ordinary roll, and those are visible. So
pinning works, and V198 rebuilding it as a teleported piece was solving a
problem that was never there. **`screen.mode` is back to `pinned`**, with
a `ConfigMigrator.Patch` to flip a server that already took the V198
file, since a default cannot reach a value written to disk. `world` stays
in config, both as a fallback and because it is the only way to move
these two pieces without a jar.

**What the cutscene path actually did differently** from the one that
works, which is what is fixed here:

- **It set the item once**, at creation, instead of on every reel step.
  The reel hands its display a stack twenty times a roll. One setItemStack
  that does not land leaves an ItemDisplay holding nothing, and a display
  holding nothing is invisible rather than empty. It is fed every step
  now, the same as the branch beside it, at the cost of a clone of a
  cached item.
- **It was gated on Rolling Animation** as well as on the rarity's aura
  switch, which the reel's own showcase is not. Fixed in V198 and kept.

**Kept from V198**, because they answer things he asked for twice: the
per viewer particle cull (the build-up and the implosion were the half
that was in his face, not the finale), the implosion gathering at 3.0
instead of at eye height, the tick under the climbing counter, and
`/rngadmin reveal`.

**Also in V199:** the cull reads each viewer's eyes once per frame rather
than once per particle. Every emitter checks every point against every
viewer, and a Divine frame makes hundreds of those calls, so a
`getEyeLocation()` inside the check was allocating a Location per
particle per viewer.

**V200: a screenshot, and four things measured instead of guessed.**

Leon sent a shot of `/rngadmin reveal`. It is worth more than the four
messages before it, because the question mark head is IN it: a small
black cube, left of and below the middle of the screen. Everything below
was read off that picture rather than reasoned about.

- **Rolling Animation was ON**, so V198's ungating was not the cause and
  the theory behind it was wrong.
- **The head was about 2.5% of the screen's height.** A thing a whole
  roll is about wants ten or more. `roll-item.comet.head-scale` is a
  straight multiplier, default 4.0.
- **It sat off centre by very nearly the offset V197 added** to "centre a
  corner origin model". That measurement says the model was already
  centred and the offset was what pushed it off. Removed.
- **It was showing its back.** A skull's face is on the north side of its
  own model and a billboarded display turns its local +Z at the camera,
  so the question mark was pointing away. Half a turn.

**The odds now climb in every act, which they provably did not.** A
band's entry is the shortest odds in it, so a drop that IS the shortest
odds in its band left its own act with nothing to climb: the act before
it had already reached that number. Both of the server's worst cases are
exactly that, the only Divine at one in ten million where Divine starts
and the cheapest Mythical at 250,000 where Mythical starts, which is
precisely the pair Leon named. `RollStages.boundaries` walks the marks
backwards and pulls any that has caught up with the one in front back to
the geometric midpoint. Simulated across all nine cases, every act climbs
and every one lands exactly on the drop's real odds:

| Drop | acts climb from / to |
|---|---|
| Divine 1/10M | 11k>100k, 100k>250k, 250k>1.58M, 1.58M>10M |
| Mythical 1/250k | 11k>100k, 100k>158k, 158k>250k |
| Legendary 1/100k | 11k>33k, 33k>100k |
| Divine, Epic off | 33k>250k, 250k>1.58M, 1.58M>10M |

**Ground pieces sit 0.30 above the block, not 0.06.** Six centimetres is
not enough for anything with a size: a glyph sits low inside its own line
box, a plate has a thickness, and both grow with the wearer's size and
their rank. It is one number, `AuraParts.GROUND`, because every ground
piece in every look is placed off `FEET`, and it is
`auras.ground-lift` in config. This covers `auratest` and the roll
circle as well, since they all read the same constant.

**`/rngadmin reveal` now shows the counter too**, climbing, beside the
head. The showcase has had a preview since V197 and the counter never
did, which is why three rounds of "ik zie de odds niet" could not be
narrowed down.

**V201: there was no way to run the thing being reported on.**

Leon asked the question that unpicked five jars: "met rngadmin reveal zag
ik het hoofd maar tijdens aura niet, weet je zeker dat dat nu goed is?"

No. And the reason is in the code rather than in the effect.

**`/rngadmin aura` never built a showcase at all.** The showcase lives in
the roll listener's reel loop, and that command calls `RollAura.start`
directly, so it played the aura, the comet and the counter and nothing
else. Anybody testing the reveal with it could not see the question mark
or the drop in ANY version. **`/rngadmin roll` is no better**: it grants
a drop and plays the burst on the spot, with no reel either. So neither
of the two commands used to judge this was ever running the thing being
judged, and four rounds of "ik zie het hoofd niet" were partly a broken
test.

Both are fixed. `/rngadmin aura` is a full dry run now: the question mark
in front of the player, fed the same way the roll feeds it, turning into
the drop on the frame the last act lands.

**And `/rngadmin nextroll <rarity>` is the one that was missing.** It
makes the next real right-click land where you want it, through
`startRoll` and everything after it, so the genuine path can be run on
demand instead of waiting for a one in five thousand Epic. The only thing
held back is the Server First spot, which is scarce and cannot be given
back; everything else fires exactly as it would in play.

**This is the lesson worth keeping:** when a report cannot be reproduced,
check that the command being used to reproduce it runs the same code as
the thing being reported. Three jars went on theories about display
placement that were answered by one screenshot and one command audit.

**V202: the crate reel was a smear, and the numbers say why.**

"the crates are still spinning too fast". Measured rather than guessed:
the old curve pushed **seventeen items past in the first second** and its
last step held for 0.35s. The step time was a cubic curve with a hard
coded floor of ONE tick, so two thirds of every spin ran at a twentieth
of a second an item. The complaint is about the start of the spin, not
the end of it.

`crates.fastest-gap-ticks` is that floor, in config now, default 3.
Alongside it `spin-steps` drops from 32 to 24 and `slowest-gap-ticks`
climbs from 7 to 12, both carried to a live config by a `Patch` since
they are values on disk.

| | first second | last step | last five items | total |
|---|---|---|---|---|
| before | 17 items | 0.35s | 1.6s | 4.2s |
| now | 6 items | 0.70s | 2.8s | 6.9s |

Drop `spin-steps` if seven seconds drags; it shortens the whole thing
without touching how readable any part of it is.

**V203: why the odds died after Legendary, and it was one line.**

Four rounds of "het werkt bij epic en legendary maar niet bij mythical en
divine". The cause:

```java
void tick(long actElapsed, double hushFrom) {
    if (done || headPiece == null || !headPiece.isValid()) return;
```

The counter was updated further down that same method, so **the number
stopped the moment the comet's head stopped existing**. And the head
starts at its band's height: Mythical's at 70 blocks up and 40 out,
Divine's at 90 and 50. That is outside the range a server sends entities
at and far enough out that a non-persistent display has no reason to
survive. Epic at 40 and 22 and Legendary at 55 and 30 stay close and
live. Exactly the split that kept being reported.

Both halves are fixed. The counter is updated before anything can return
early, and it no longer depends on the comet at all. The comet's own
heights come in to 18 to 36 blocks up and 10 to 22 out, so the whole path
stays inside 45 blocks of the player and is always tracked.

**The rest of the same message:**

- **The counter fits the screen now, by construction.** Its size is the
  smaller of what its band wants and what the text measures, at 0.089
  blocks a character (read off the V202 screenshot). Bands get bigger
  exactly as the numbers get longer, which is how "1 in 10,000,000" at
  Divine's size ran off both edges.
- **The head was a third of the screen.** `head-scale` 4.0 to 2.0, which
  is about what a drop takes, and it now **grows a step with every band**
  the roll survives, as asked.
- **The particles came back.** `face-clear` was a flat 2.6 and took too
  much of the aura with it. 1.5 clears about what an arm reaches, so what
  is at eye level still goes and the rest returns. In config, 0 puts
  everything back.
- **The floating heads turn half as fast**, a full circle every eight
  seconds rather than every four. Both the crate heads and the
  leaderboard heads, which V202 never touched: that jar changed the reel
  inside the crate menu, not the heads in the world.
- **A forced roll takes a Server First again.** V201 held it back on the
  grounds that a spot cannot be given back. The spots are Leon's to
  spend and he asked for it.

**V204: the counter moved off the comet, and four more from the same list.**

**The odds now live on the reveal's own clock.** V203 stopped the counter
returning early with the comet, but it still lived INSIDE `RollComet`,
which is a display entity thirty blocks away that can be culled, fail to
spawn or be cleaned up. `RollAura` owns the `RollCounter` now and updates
it every tick from the first act to the last. Nothing about the comet can
reach it any more, which is the only way to be sure rather than to
believe.

**`/rngadmin aura` narrates its acts.** One line per act to whoever ran
it: which band, and what the counter is counting to. If the acts stop
advancing, or an act has no range to climb, it says so in chat rather
than leaving it to be guessed from what is or is not on the screen. This
is the answer to four rounds of "werkt niet bij mythical en divine" with
no way to tell which half was broken.

**`/rngadmin auras`** lists what every rarity wears, plain and shiny,
plus the measured ride height, the ground lift and the feet offset. Asked
for directly, and the three numbers are the first place to look when
something sinks.

**Ground pieces: 0.45.** Still sinking at 0.30. Carried across by a patch.

**Close orbits stay out of the wearer's own view.** `AuraParts.outOfView`
is the one rule now: a ring is out of the way when it hugs the ground or
is past 3.2 blocks out. Divine's sea lanterns ride at 2.8 at chest
height, which counted as wide enough under the old radius-only test and
is exactly where a first person camera points.

**The top rank's name moves, in its own colours.** `Lore.drift` walks a
gradient along the text, one lap every four seconds, off the refresh task
that already existed for this. With nothing picked it is the rank's own
stops rather than the rainbow, which said nothing about which rank it was.

**The head sits higher** (0.55 to 0.38 below the middle).

**Still open from the same message**, and honestly a jar of its own: bold
and italic and underline and strikethrough on drop names by rarity, the
symbols on either side of a drop inside the tab tag, and shorter names
for tab and chat. That is the drop naming pipeline (`RollFormat`,
`RarityStyle`, the tag) rather than the reveal, and mixing it into this
one would have made both harder to judge.

**V205: the comet was never once visible, and that explains most of it.**

Leon, after five jars built around it: "wat bedoel je ook elke keer met
comeet want die is er niet en de player kijkt ook niet naar een comeet".
He saw it fail on the first jar, assumed the idea had been dropped, and
never mentioned it again. Meanwhile every jar since was reasoning about a
cutscene half of which had never rendered.

**Two reasons at once, and both are ranges.**

- **The head is a display entity**, and a server only sends an entity to
  a client inside its tracking range, 32 blocks for this kind by default.
  The comet spends nearly its whole fall further off. The view range set
  on the display is a CLIENT side limit and cannot help with something
  the client was never told about.
- **The trail is particles, and they were not forced.** A client draws an
  ordinary particle only within 32 blocks of itself. `FirstTenBuildUp`
  learned this and says so in its own comment, which is where it should
  have been read four jars ago.

So the comet is drawn in particles now, forced, with the block kept as
the close-up look. Every particle in `RollAura` is forced too: that
effect reaches forty blocks up a Divine's pillar and twenty out through
its shell, so the far half of every big reveal was being drawn for
nobody.

**And the drop names, asked for in the same breath:**

- **Weight climbs with rarity.** `RarityStyle` never carried italic, so
  the top tiers all read the same weight. Leon set the ladder in V206:
  Epic bold, Legendary and Mythical bold and italic, Divine all three
  with underline on top. Strikethrough is supported and left off, because
  a struck out name reads as cancelled.
- **The marks reach tab and chat.** The flair has always been an
  obfuscated character, which flickers, so the plain path threw it away
  and the tab tag had no marks at all. Each rarity has a real glyph now
  (`symbol-char`, Epic and Legendary a star, Mythical a hollow one,
  Divine a filled one) used everywhere the flicker cannot go.
- **`tag.max-name-length`, 18.** Only the shared-line copy is cut. The
  drop, its tooltip and the nametag over somebody's head keep the full
  name.

**V207 and V208, 25 September, untested in game:**

- **V207, the podium.** Heads closer, #1 head 25% bigger, the tags over
  the top three 15% bigger. The distance was never `podium-spacing`
  (6.5): it was the floor that keeps the tags from overlapping, 10.2
  blocks. #1 now stands high enough that its tag clears the other two,
  which brings the floor to about 7.5. Side effect to check: #1 floats
  visibly higher than #2 and #3, most on the farming board (three tag
  rows).
- **V208, the aura beam.** The `Column` piece is gone from ascend (Divine
  tag, also the heavy fallback), prism, pyre and empyrean (the Epic,
  Legendary and Divine shinies). beacon and zenith still have theirs, no
  rarity wears them.

**V209, the drop names, untested in game.** Leon, 25 September: V206's
bold and italic never showed, because a drop's name is drawn in its
OWN style (its colours, or its block's) and only the rarity label read
the rarity's weight. Now every drop wears its rarity's weight, and from
Epic up the gradient has the rarity colour woven through it with more
stops (3 / 4 / 5 / 5). Tab and chat carry the same flickering flair as
the nametag, his call. The Divine is "First Star" (was "Halo of the
First Star"); `RarityManager.RENAMED` maps the old name so discoveries,
tags, found counts and items in inventories keep working, and a
`renamed-drops` patch renames it in the live config.

**The rank gradient in tab needs one line in TAB's config.** TAB writes
the tab list itself and throws away the name the plugin sets.
`%spacerng_name%` is the whole name (badge, rank colours, title),
`%spacerng_name_colored%` the name alone, `%spacerng_rank_badge%` the
letter. `/rngadmin placeholders` shows what they resolve to.

**V210, untested in game, three small things from one message:**

- **No more flicker.** The obfuscated flair shoved the whole name about
  in tab and looked the same on every rarity. Every name now wears its
  rarity's own still mark, everywhere: Epic ◆, Legendary ✦, Mythical ❖,
  Divine ★ (`symbol-char`, patched on the live config).
- **The reveal.** The falling comet is off (`roll-item.comet.falling`)
  and the question mark head is off (`roll-item.comet.question-mark`),
  both kept in the code. The odds counter sits in the middle of the
  screen. The drop still appears the moment the counter lands; with no
  head, `finishRoll` starts the showcase itself.
- **Divine's sea lanterns.** `AuraConcepts.OwnView` spawns the tilted atom
  for everybody else and for the wearer in "everything", and a level ring
  at the knees that only the wearer sees in "out of your way".
  `AuraConcept.audienceAt` says which piece is for whom.
- **Rank gradient in tab** is still waiting on TAB's config:
  `customtabname: "%spacerng_name%"` in TAB's groups.yml.

**Queue from the same message, one jar each, in this order:**

1. **The roll reveal.** Mythical is barely audible, the rest is good.
   Particles on the ground at every rarity instead of the block and item
   displays on the ground (those are the auras' look). The question mark
   is gone since V210, so its small to big growth is moot.
2. **Auras by rank.** Every rarity from Epic up wears what Divine wears
   now (ascend), in the rarity's colour, but only at Supernova. Smaller
   looks for lower ranks, and Linked wears what Epic wears now (sigil),
   at every rarity from Epic, only the colour changing. Nova and Comet
   are in between: ask whether ember and eclipse are the middle steps.
3. **Divine's ground stars.** The stars that shot outward over the
   ground are missed. Bring them back inside the first ring.
4. **The sea lanterns.** Done in V210. At knee height in "out of your way"; they sat
   in faces now. "Everything" keeps them where they are.

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

1. **How is a pet earned?** Answered on 24 September: an egg, bought with
   100 Cosmic Dust, hatched with an animation. Cosmic Dust comes from
   rolling once `cosmic_root` is bought and its levels raise the chance,
   which is what the jar already does; the cost in config is still 10 and
   has to become 100. Farm Dust keeps paying for tiers.

   **The egg ladder is answered too:** "gewoon 1 ei voor alles". One egg
   at 100 Cosmic Dust, and the hatch rolls which rarity of pet comes out
   of it. No tiers to buy, so no per egg odds tables and no second
   currency sink. The egg's own look can still change with what it is
   about to hatch, which is where the shake and the break get their
   build-up.

   **Also open:** whether Cosmic Dust should also fall from scrapping in
   /convert, as a second source or instead of the roll source. And
   whether rarity levels should cost Farm Dust rather than Cosmic Dust,
   since he described Farm Dust as the upgrade currency.
2. **Boss Box contents.** Credits, Perk Tickets, timed boosts and two
   permanents (+10% Luck, +10 Speed) are in. He tunes the weights.
3. **Milestone Credits.** Top rank is now 7000. Either lower the top
   milestone reward to 100 and add more paying tiers, or scale every
   milestone by 0.4. Not changed until Leon picks.
4. **Custom heads for crates and items.** Not a blocker after all.
   `TopHeadManager` already builds heads through `PlayerProfile`, so a
   `head:` key in config is `Bukkit.createProfile` plus
   `getTextures().setSkin(url)` on a random UUID. It is waiting on
   somebody asking for it, not on research. The egg jar will need it
   first.
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
