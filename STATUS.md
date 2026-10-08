# Where SpaceRNG is right now

Written for whoever picks this up next, Leon or a fresh Claude session on
another machine. Read this before proposing work. `CLAUDE.md` holds the
rules and the house style; this file holds the state, and it is the one
that goes stale, so update it at the end of a working session.

Last updated at **V360**, 8 October 2026. The newest section is the
first one below; older sections further down are history.

## Start here: V360, 8 October 2026

One jar, **untested in game**. The local JDK 21 compile is clean. It is
the rest of the pets rework: the eggs, the pet index, Pet Luck and the
hovering egg. With V359 under it the whole thing Leon asked for on
8 October is now built.

### Ten eggs, one every ten Prestige

- `prestige_10` through `prestige_100`: Meteor, Comet, Nebula, Pulsar,
  Quasar, Supernova, Eclipse, Singularity, Horizon, Infinity.
- **An egg unlocks on its own** at its Prestige. The number on it is the
  price of ONE opening, never a fee to get in, which is what Leon
  corrected.
- **Paid in Gems**, 10,000 for the first and doubling each egg to
  5,120,000 for the last. Cosmic Dust levels a pet, Gems open an egg, so
  the two currencies are the two halves of the system.
- **Seven pets in each, one per rarity**, Common to Divine. Seventy pets
  in all, every one with its own name, icon, colours and line. That is
  what makes "every rarity found" and "every pet found" the same
  sentence.
- **The chance of each rarity is the same ladder in every egg**: Divine
  0.001%, Mythical 0.02%, Legendary 0.2%, Epic 2%, Rare 10%, Uncommon
  25%, and the rest, about 62.8%, Common.
- What a later egg buys is **pets worth more**, through
  `pets.egg-bonus-growth` (1.15). The Prestige 100 egg's pets are worth
  about 3.5 times the Prestige 10 egg's. **That is the one number to
  move** if the late eggs feel wrong; Leon gave 1.1x to 2.0x for the
  first egg and left the ladder above it open.

### The egg screen

A click on an egg in `/pets` opens its own screen instead of buying on
the spot, which is the only way to see what is in an egg before paying
for it. It is built from Leon's screenshot:

- **The seven pets in a row**, Common on the left. A pet already found
  shows what it is and what it is worth; one never found is a **"???"
  barrier reading "Locked"**. Either way the card prints the chance and
  the one-in.
- Those chances are the LIVE ones, Pet Luck folded in, out of the same
  method the roll uses, so a card can never promise odds the egg does
  not use.
- **Open 1, 3 or 9** (`pets.eggs.bulk`), and the number is the stack
  size on the button.
- **Auto open**, linked accounts only. With it on, the click keeps
  buying until the Gems run out, the storage fills or 256 eggs have gone
  (a tick guard, not a balance number). It always says why it stopped.

### The pet index and Pet Luck

- **Pet Luck tilts every egg toward its rarer pets.** Each chance above
  the egg's Common is multiplied by 1 + Pet Luck and the Common absorbs
  the difference. It cannot run away: if the raised chances would sum
  past a whole egg they are scaled back.
- **5% per pet found**, whatever its rarity, and **100% for every egg
  whose seven are all found**. With everything that is 1,350%, which
  turns the 0.001% Divine into about 0.0145%.
- **Discovery is "have you ever held it"**, kept in `pets-found` on the
  save, so throwing a pet away never costs the Pet Luck it bought.
- **Three Pet Luck nodes** on page 5 of `/skilltree`, 10 levels each at
  +10%, +20% and +40%, hanging off Steady Hands, Cosmic Yield and Pet
  Slot III. Every one sits on the slot next to what it needs, so the
  V358 rule still holds (the checker says 0 non-adjacent of 101).
- **`/index` has a fourth button, Pet Index**, next to Normal, Shiny and
  Secret. It opens the pet index screen `/pets` already had, and the
  button itself prints pets found, eggs complete and the Pet Luck they
  pay. Inside, each egg has a card saying what it has paid and what
  finishing it would pay, which is the "show the boost like the other
  stuff" he asked for.

### The hovering egg

`/rngadmin petegg place <egg>` while **holding the item it should look
like** and looking at a block, exactly the gesture `crate place` uses.
The item floats and turns over an invisible barrier; a click on it opens
that egg. `remove` and `list` as well, with tab completion. It is a new
`EGG` kind in `HoloManager`, so it is saved in `holograms.yml`, swept on
start and never written into a chunk like every other floating thing.

### Reaching the live config

`config-version` is **49**. `pets.eggs` joined `ConfigMigrator.STRUCTURAL`
beside `pets.types`, because a pet names the egg it comes from and an egg
names its seven pets: one arriving without the other is a broken table.

**Worth watching:** egg PRICES live inside that structural section, which
is the one thing the list is normally kept clear of. If Leon starts
tuning egg prices on the server, take `pets.eggs` back out and carry
changes as patches, or the next `config-version` bump wipes his numbers.

`pets.egg-bonus-growth` and `pets.index` are new keys in ADDED_SECTIONS.

### Worth knowing

- **`PetHatch` is dead code now.** `pets.eggs.instant` has been true
  since V313 so the animation never ran, and the egg screen does not
  call it at all. It is still in the tree if the build-up is ever wanted
  back.
- **One pet per rarity per egg was Leon's answer**, so an egg is seven
  pets and nothing in the code assumes more. If he ever wants several
  pets at one rarity, `PetEgg.petAt` is the one place that would have to
  pick between them.

## V359, 8 October 2026

One jar, **untested in game**. The local JDK 21 compile is clean. It is
the first of the pets rework, the subject Leon spelled out the same day;
the full spec and his answers are in the idea list below.

This jar is **the pet itself**. The eggs, the pet index and the hologram
are the jars after it, and they are listed at the end of this section.

### One ladder, not two

A pet used to carry a rarity level and a tier. Both were bought with
Gems, one of them could fail and keep the payment, and a cap existed to
stop the two of them multiplying away from each other.

There is one ladder now, and it is Leon's: **level 1 to 10, every level
10% more multiplier, paid in Cosmic Dust**. It always takes. Dust is the
point of it, because he asked for pets to grow off the Cosmic Dust
skills, so the thing that levels a pet is the thing those skills pay
out.

- `pets.upgrades` is `max-level`, `level-step`, `level-base-cost` and
  `level-cost-growth`. Level 2 costs 250 dust and level 10 costs 32,187,
  about 71,000 dust to take one pet all the way. A find is 1 to 10 dust.
- **Steady Hands was repointed rather than left dead.** It bought the
  chance a tier took, and tiers are gone. It is `PET_LEVEL_DISCOUNT`
  now, 3% off a level per skill level, 15% at its max. Same node, same
  name, same slot on page 5, and it buys the one thing a pet still has.

### The stat is the player's, and it can change at any time

A pet is a multiplier looking for a job. `PetType.stat` is the stat a
freshly hatched copy STARTS on; the owner points it wherever they like
and that choice lives on the owned copy.

- The old rarity and tier buttons in the pet screen are a **Level up**
  button and a **Boosting** button. The stat block prints all six stats
  with the current one ticked and steps forward on a click, wrapping, so
  Bedrock reaches every one of them without a right click.
- It is free and reversible as often as they like, Leon's call.

### What a pet is worth

**1.1x at Common to 2.0x at Divine**, Leon's numbers, in
`pets.multipliers`. That is ten times the old ladder, which ran 1.01x to
1.64x. Level 10 multiplies the bonus by 1.9, so a Divine lands at 2.9x
and a Common at 1.19x, and shiny is 10% on top again.

### Storage, and the wipe

- **A hundred pets**, `pets.storage`. A kind counts once however many
  copies sit behind it. **Full stops the opening**, Leon's call: the egg
  refuses and says so before anything is charged, and nothing is ever
  thrown away to make room. The number is on the storage card, in red
  when it is full.
- **Nothing carries over.** `pets.wipe-version` is 1, and a save behind
  it has its pets, its worn pets, its autotrash list and its Cosmic Dust
  cleared once on join, with a line in chat saying why. There is no
  honest way to map a Rarity 7 Tier 4 pet onto a level, and the dust goes
  with the pets because it was earned against the old prices. Raising
  that number does it again, which is the door to use if the eggs are
  ever re-cut.

### Reaching the live config

`pets:` is not a structural section, so none of this merges on its own.
`pets.wipe-version`, `pets.storage` and the four level keys are in
`ConfigMigrator.ADDED_SECTIONS`, and the seven rungs of
`pets.multipliers` come over as seven one-off patches, each of which only
fires while the live value still equals the V324 default it shipped as.
Without the wipe-version key the server would read 0 and clear nobody.

### What is left of the rework

1. **The eggs.** One per ten Prestige from P10, auto unlocked, paid per
   open in **Gems**, buy 1 / 3 / 9, auto open for linked accounts, the
   chance of each rarity on the card with Divine around 0.001%, and each
   egg holding its own seven pets, one per rarity. The egg screen is his
   second screenshot: the pets not yet found drawn as "???  Locked" with
   their chance under them.
2. **The pet index.** 5% Pet Luck per pet found, 100% for an egg whose
   rarities are all found, Pet Luck raising the odds of the rarer pets,
   the Pet Luck nodes in `/skilltree`, and the pet index as a page in
   `/index` with its boost shown the way the other collections show
   theirs.
3. **The hovering pet egg**, placed with a command the way crates are,
   holding the egg in hand when the command is run.

The eggs in config are still the three Cosmic Dust ones. They work, they
are just not the ladder he asked for yet, which is jar 2.

## V358, 8 October 2026

One jar, **untested in game**. The local JDK 21 compile is clean. It is
Dantey's bug report from 8 October: the skill trees looked like they
blocked skills at random, and Bedrock menus named a button Bedrock does
not have.

### The trees are wired to the picture now

A node's `requires` is printed in its tooltip as a "Needs:" line. In both
trees that line very often named a node on the far side of the screen:
**55 of the 150 prerequisites were not touching the node that needed
them**. Gem Greed at the left end of the trunk needed Coin Greed at the
right end, Convert needed Speed I two slots over, and Luck II sat between
two skills that both needed it, which is what Dantey read as Luck II
blocking the skill above and below it.

The cause is that the slots were laid out by theme (a Speed line, a Luck
line, a Money line) while `requires` was written by the order Leon wanted
things bought in, and the two were never reconciled. V357 narrowed the
frame to three lines and a trunk, which made the mismatch impossible to
miss.

The rule now, in both trees, on every page: **a node needs the slot
touching it, one step in toward the root.** Row 6 runs outward from the
middle, then the column climbs, and row 5 hangs off the trunk beneath it.
Every page root keeps its cross-page prerequisite, which is the one
"Needs:" that is allowed to point somewhere you cannot see.

- **64 prerequisites rewired, 25 nodes moved.** No price, no effect and
  no value changed anywhere, so nothing is rebalanced. What changes is
  the ORDER things are bought in inside a line.
- **Nothing sits behind an enchant that cannot be bought.** Gem Greed and
  Gem Cascade are `coming-soon`, so under the new rule they would have
  blocked a whole arm. Gem Greed moved to the spur directly above Coin
  Greed, which is both a dead end and exactly where Dantey expected it.
  Gem Cascade moved to the spur above its own arm, and the farm page 3
  right arm shifted one slot down behind it.
- **Speed Enchant took the slot Gem Greed left**, right next to the Wheat
  root, which is nearer to Leon's V298 request than where it was.
- **Two cycles were in the config and are gone**: Nuke needed Black Hole
  while Black Hole needed Nuke, and the same between Enchant Mastery I
  and II. Both were unbuyable deadlocks. Enchant Mastery I and II also
  swapped slots so I comes before II.
- **`SkillTreeManager` warns in the server log** if an edit ever points a
  prerequisite at a slot that is not touching, so this cannot drift back
  silently.
- `config-version` is **48**. Both trees are in `ConfigMigrator.STRUCTURAL`,
  so the live config is overwritten on first start with this jar.

Nobody loses anything they bought: prerequisites are only checked when a
node is bought, never re-checked afterwards.

### Bedrock stopped naming a button it does not have

Geyser reports every menu click as a left click, so a Bedrock player has
no right click inside a menu. Every ladder and card that said
"Right-click steps back" said it to them too.

- **The ladders in `/options` and `/index`** now print "It wraps round to
  the start." for Bedrock. One footer, `Stepper.footer`, so the two
  screens cannot disagree.
- **The hoe card** said the enchant toggle is a right click. For Bedrock
  it says "open it" instead, and the switch is in the level screen.
- **The pet storage card** says a click opens the pet and that it is worn
  from its own card, which is what already happens there for Bedrock.

Worth saying: every one of these was reachable already, the text was the
lie. If Dantey still cannot change something, the next thing to ask is
WHICH option, because the logic has a Bedrock path everywhere.

## V355 and V356, 7 October 2026

Two jars, both **untested in game**. The local JDK 21 compile is clean
for both; nobody has checked GitHub Actions from this session, so look
at the run for 7b56231 before uploading.

They are the rest of Leon's one-message list from the V353 / V354
session. He asked for everything in it except the pets rework, which is
the one subject still open.

### V355, seven small things

- **A hundred Nova Core tiers, 25x at the top**, in place of thirty
  ending at 120x. Every rung is the same proportional step, about 3.3%.
  Checkpoints grow by `novacore.checkpoint-gap-growth` (2) rather than
  by one, which puts them at 5, 11, 19, 29, 41, 55, 71 and 89.
- **Every account is mapped onto the new ladder once, on join**, by how
  far up the old one it was. `novacore.rescale-version` says which round
  this is and `rescale-from-max-tier` the old length, so a re-cut ladder
  can do it again. Without it, the old tier 30 would have become tier 30
  of 100.
- **/novacore pages.** Prev and next on slots 48 and 50, opening on the
  page the next rung is on. Before this, every tier past twenty existed,
  paid out and could be forged, and simply was not drawn.
- **Realm Time reads as time**, "+1 min" rather than "+60.00".
- **No workbenches and no containers.** The interaction is cancelled, not
  the inventory, so the plugin's own menus are untouched. The list is
  `protection.blocked-blocks`.
- **Perks in the Farm, Cosmic, Vote and Nebula crates at 0.5%.** It pays
  a perk ROLL, not a perk, because a perk is one worn slot and handing
  one over would replace what is being worn.
- **/index sorts inside a rarity by odds**, so the whole index is one
  ladder from the commonest drop to the rarest.

No config-version bump. The four new keys are in `ADDED_SECTIONS`, and
the ladder and the crate tables are two one-off patches that only fire
while the live values still equal the defaults they shipped as.

### V356, the menus

- **/brewer and /potions are two commands.** `/potions` used to be an
  alias on BOTH /potion and /boosters, so which menu it opened was down
  to the order in plugin.yml. Now: `/brewer` (aliases brew, potion) is
  the shelf, `/potions` (alias boosters) is the cupboard. Each carries
  the way to the other in its bottom left corner, slot 45 in the brewer
  and slot 27 in the cupboard, and the cupboard's button reads Locked
  with the way in when Potions is not unlocked.
- **Crate reward cards** are the description block now: a subtitle
  saying what kind of reward it is, a two line pitch bold at the words
  that matter, and a "You get" line. The amount used to live only inside
  the name, so a reward named by hand in config showed its size nowhere.
- **`Lore.key`** is the one shape for Leon's "bold at logical points":
  white and bold for the two or three words that carry a line, grey put
  back after. It is on the crate pitches, the pet card's stat, the
  premium pass panel and the no-perk card. **The rank blurbs are left
  alone on purpose**, because those are Leon's own words in config.
- **The Battle Pass card** trades its "Reward:" header, a row repeating
  the list under it, for a subtitle saying what kind of reward the rung
  holds.

### Worth knowing

- **Three of these came from screenshots this session never saw.** Leon
  sent pictures for the longer descriptions, the crate item cards and
  the Battle Pass, and they were not in the conversation when the work
  was done, so the layout came from `.claude/skills/menu-design` rather
  than from his pictures. If any of the three is not what he meant, the
  screenshot is the thing to ask for again.
- **The money cap is not plugin work.** It is `max-money` in Essentials'
  own config.yml, a hard number rather than an "unlimited" switch, and
  the ceiling Essentials accepts is about 9e15.

### Still open from that list

- **The pets rework is built**, V359 and V360, and untested in game. The
  spec and his answers are still in the idea list below as the record of
  what was asked for.

## V331 and V332, 4 October 2026

Two jars, both green, **neither tested**. No config-version bump: both
reach the live server through Patches, ADDED_SECTIONS and one
hand-rolled crate walk.

- **V331, Nova Cores are virtual.** A balance on PlayerData
  (`nova-cores`), routed through `ConsumableManager.give`, so crates,
  milestones, the pass, the guide gift and /rngadmin consumable all work
  unchanged. Old items still count and are absorbed on join, on a right
  click and whenever /novacore opens. A lot more of them: Farm and Vote
  crates 3, Nebula 5, Cosmic 10, each with a heavier weight, and Nova
  Finder is ten times the rate AND pays a Core instead of forcing a free
  forge. /novacore gained a purse block and a "Your climb" block.
- **V332, keys are a count over the crate.** Every key goes to the
  stored count with no switch; /keys, its menu, its holder and its click
  handler are deleted. A click on a crate opens one, a sneak click opens
  all, a click with no keys shows the table. The floating text over a
  crate says the count and what a click does. `isAutoStoreKeys` stays on
  PlayerData, read by nothing.

### Worth knowing

- **Nova Finder at a fully bought hoe is about 1 in 100 crops.** If that
  is too many Cores, `farming.enchants.NOVA_FINDER.per-level` is the one
  number to move.
- **There is no way left to hold a key or a Core as an item.** Anything
  that hands one over adds to the count, which is what makes the crate
  and /novacore the only places they live.

## V327 to V330, 4 October 2026

The same session as V322 to V326, second half. Four more jars, all green,
**none tested in game**. Config is at version 33.

### What shipped

- **V327, the pet index by rarity.** Leon: "just have the rarities, dont
  divide them with the boost they give." Seven cards, one per rarity,
  each with its fresh and fully grown multiplier and the six pets inside
  it. Autotrash moved with it and is per rarity now; it still writes the
  same per pet set underneath.
- **V328, four things.** Enchant Mastery has bought nothing since V190:
  maxLevelFor is min(max-level, base-cap + mastery), V190 raised base-cap
  to 10,000 and max-level already was 10,000, so the sum clamped straight
  back. max-level is 20,000 now and base-cap stays, so nothing anybody
  holds moves. Speed from /skilltree halved, 8.85 to 4.40 at full. The
  chat hover no longer draws the sidebar's resource pack glyphs, which
  render there as yellow boxes. A shift click in /boosters drinks the
  whole stack of a draught.
- **V329, the Secret Realm.** /rngadmin realm here plus /rngadmin realm
  radius <blocks> IS the area: a point and a distance, both in realm.yml.
  /secretrealm is an alias of /realm. A holograms.panels.realm panel.
  A grey reveal, `realm/SecretFx`, when a secret is found: ash, stone and
  bone, no rarity's colours. Inside the realm nobody wears an aura or a
  drop tag; what floats over a player is their secret and the Index Luck
  it pays, and it goes back on the way out.
- **V330, /index and the store.** Three buttons, Normal, Shiny and
  Secret, in place of the stepping block, entries sorted by rarity, and
  the second row stays black instead of picking up the palette's blue on
  its two ends. A store purchase announces itself: the web store calls
  /rngadmin give credits and /rngadmin rank set, which said nothing to
  anybody, so both now post a line, behind buy.announce and
  buy.console-only (on, so an op testing it stays silent).

### Open, waiting on Leon

- **Everything from the V322 to V326 list below is still open**, plus:
- **max-level 20,000 doubles every enchant's ceiling value**, reachable
  only with both mastery nodes. If that is too much, drop per-level
  instead of max-level, but that one DOES take power off people.
- **The store lines are a guess at what he wants said.** Both messages
  are MiniMessage in config.
- **Duplicate pets still have no use** beyond stacking and being thrown
  away.

## V322 to V326, 4 October 2026

Leon gave a running list over one session and asked for all of it. Five
jars, every one pushed and built green. **None of it has been tested in
game**, on top of V310 to V326 before it, which has not been tested
either. Config went from version 29 to 32, so the next upload rewrites
every structural section, the farm tree and the pet table included.

One decision he made, when asked: "multiple Starforges" means owning
every tier you forge, not several copies of one.

### What shipped

- **V322, three fixes.** `/aura` has thrown on every open since V319:
  Astral joined `SHOWN` and the two slot arrays stayed four wide, so
  `build()` ran off the end of the array. Both rows are five wide now.
  Nova Core got `novacore.flat-chance`, 0.5: while it is above 0 it IS
  the chance at every tier, and the decay curve, Luck, the Nova Touch
  upgrade and the guaranteed first forge are all skipped, so **Luck and
  Nova Touch now buy nothing on the ladder**. A sneak click on a crate
  and a shift click in `/keys` open every key held, with
  `crates.quick-open-max` raised 25 to 500 as the one tick safety
  ceiling. Supercharged Roll and Lucky Streak moved from chat to the
  action bar.
- **V323, the crops ladder.** 50k, 100k, 250k, 500k, 1m, his numbers.
  The sixth step he named, 2.5m, has no crop to open: **it is waiting
  for a sixth crop he has not asked for yet.** Both halves updated, the
  config section and the hardcoded fallback in `unlockAt`, which IS the
  lookup's default since V320, plus five Patches for the live config.
  Crop Yield per level was inverted, 0.10 wheat down to 0.05 fern,
  because a better crop used to climb the crop count faster as well as
  paying more.
- **V324, pets rebuilt.** Forty-two pets, six stats at seven rarities,
  each multiplying one stat. What a pet is worth comes off
  `pets.multipliers` by rarity and doubles every step (1.01x to 1.64x
  fresh, 4.84x fully grown at Divine). Duplicates instead of a silent
  free rarity level, a copy count in the save file's fifth field, a
  shift click in storage to throw one spare away, and autotrash per pet
  in the index. Eggs carry the chance of each rarity written out:
  Mythical was 1 in 4 on the Nebula and 1 in 3.7 on the Supernova, which
  is exactly his "egg 2 is almost the same as egg 3", because the old
  boost multiplied every rarity from Epic up by the same number and
  could never move them against each other. Now 0.2% and 2%. His Divine
  chances are untouched. `pets.types` joined STRUCTURAL, which is only
  safe because no number he tunes lives inside it any more.
- **V325, multiple Starforges.** Forging a tier hands over the new item
  and leaves the old ones alone, and the Starforge IN YOUR HAND is the
  Luck, Speed and ability you get. An owned tier in `/starforge` says
  "Click to take one out", which is how anybody who climbed before this
  jar gets the lower tiers back.
- **V326, Alchemy removed.** The enchant, its two knobs and its harvest
  proc, with three REMOVALS entries, because farming is not structural
  and an enchant taken out of the jar otherwise keeps loading from the
  live config. The farm tree node goes with the structural rewrite and
  leaves a blank slot at page 2, 3-3. Levels anybody bought in it are
  not refunded.

### Open, waiting on Leon

- **The sixth crop.** He named a 2.5m step with no crop to put on it.
- **Nova Core at a flat 50/50** leaves `luck-weight`, `decay`,
  `base-chance`, min and max chance and the Nova Touch prestige upgrade
  doing nothing. Nova Touch in particular is a prestige upgrade players
  may have paid for.
- **Duplicate pets have no use yet.** They stack and can be thrown away;
  they do not feed rarity or refund dust. Say the word if they should.
- **Astral has no pets.** The index ladder skips it on purpose.
- **"Allow myself to give me cosmic dust" already exists**:
  `/rngadmin dust cosmic <amount> [player]`, and `farm` for the other.

### Test first, in this order

1. Upload V326, check `/version SpaceRNG` says 326, and check the
   console for the config migration lines (version 32, the structural
   rewrites, the Alchemy removals).
2. `/aura`. It should open at all, with five looks per row.
3. `/novacore`: the forge button should read 50% at every tier.
4. `/keys` shift click, and sneak right click on a crate.
5. `/pets`: hatch Stardust eggs with `/rngadmin dust cosmic 100000`,
   check duplicates land, switch autotrash on a pet in the index and
   hatch it again, read one index tooltip top to bottom.
6. `/starforge`: take out a lower tier and check the sidebar Luck
   changes with which one is in your hand.
7. `/crops`: Carrots should want 50,000.
8. The hoe: no Alchemy in the rack, nothing broken around it.

## V310 to V320, 3 October 2026

Leon gave a fourteen item list and then said "bouw alles neem je tijd
deel het wel op in taken maar ik wil dat alles wordt gemaakt", so the
one-subject-per-jar rule was set aside at his word. Eleven jars, every
one pushed and built green. **None of it has been tested in game.**

His three decisions, for the record: egg one hatches anything but a
Divine, new crates simply stop giving the old permanent Luck and what
players already hold stays theirs, and the tier above Divine is Astral.

### What shipped

- **V310, the stepping block.** /options drew sixteen per-rarity
  switches; it is four settings and three ladders. Left click steps the
  floor up, right click back, the whole ladder is printed in the
  tooltip. The roll animation is one of them, so a player can ask for
  the reveal only from Legendary up. `gui/Stepper`.
- **V311, /index on the same pattern.** Index Mode steps normal, shiny
  and secret; Index Tier steps all tiers then one at a time and carries
  what the old per-rarity tab carried. Secret hands over to
  `SecretIndexGui`, which now wears the same mode block, so the three
  collections are one screen. The Prestige floor is printed on it.
- **V312, Tag Luck to the secret index.** `secret-realm.secret-luck`
  true and `min-prestige` 10, both as patches. The V291 system, finally
  switched on. Leon keeps the realm itself closed, so Tag Luck is gone
  with nothing in its place until he opens it; that is deliberate.
- **V313, the egg ladder.** Divine is a flat chance per egg
  (`divine-chance`) rather than a weight, because the boost dragged it
  along and "ten times" could not be expressed. Stardust 100 dust from
  P0 with no Divine, Nebula 1,500 at P10 at 1 in 1,000, Supernova
  12,000 at P25 at 1 in 100. Eggs hatch on the spot
  (`pets.eggs.instant`, false brings the V238 build-up back). Every
  upgrade is Gems, growth 2.0.
- **V314, Cosmic Dust as a range.** `pets.dust.cosmic-min` to
  `cosmic-max`, and the new Cosmic Yield node (page 5, ten levels) adds
  to the top. config-version 29 for it. Also fixed a real bug: the
  level-up hint wrote the action bar blind every three seconds and
  painted over every dust find under Auto Roll. `gui/ActionBar` owns the
  line now.
- **V315, permanent Luck in small pieces.** luck_5, luck_10, luck_25.
  Farm and Vote pay 5, Cosmic 10, Nebula 25, the pass 5 / 10 / 25 at
  levels 20, 35 and 40. The old luck_50 and luck_250 stay DEFINED and
  are given out nowhere. Two one-off migrations, because a crate's
  rewards are a list of maps with no id and neither crates nor pass may
  join STRUCTURAL.
- **V316, /crates.** Teleports to the crate placements in
  `holograms.yml` and your own key count floats over each crate, one
  private TextDisplay per reader within 12 blocks, the podium's trick.
  Keys were already digital since V282.
- **V317, potions digital.** A bought potion goes to /boosters like
  Potion Finder's finds, so it stacks. Luck reads `1.5x` not `+50%`.
  Charges, Nova Cores, vouchers and permanent grants stay items.
- **V318, the trees.** Three nodes drew on the dark border with no frame
  round them, which is what read as random: enchant_speed at (4,6),
  auto_convert at (3,4), cosmic_root at (6,1). All 175 nodes checked.
  The frame grew with mirrors rather than the nodes moving, so every
  placement Leon asked for is intact.
- **V319, Astral.** 11 Legendary, 8 Mythical, 10 Divine, 3 Astral, every
  material verified against the jar. Astral's floor is 5x Divine's
  ceiling, 1 in 3.75 billion. It has its own reveal scale, violet
  instead of brighter, FIREFLY accent, armillary and zenith auras.
- **V320, four of his asks.** Prestige reads P4. /fixhoe, for a
  sender, a named player or every player online. The crops-farmed gate
  really fixed this time, see below. And the chat hover was already done
  in V309, he just had not seen it.

### Two things worth reading

**The crops-farmed gate.** Leon has reported this several times and it
kept coming back because TWO things had to be wrong at once.
`farming.crop-unlock-at` is a sub-section added to a `farming:` block
every older config already had, so it never merged and is absent on the
live server. Then `unlockAt` asked plain `contains()`, which consults
the jar as defaults and answered true anyway, so the hardcoded ladder
below it was unreachable; and `getLong(path, 0L)` answered 0, because an
EXPLICIT default beats the configured ones (`MemorySection.get(path,
def)` never looks at the defaults, unlike `get(path)`). Every crop read
as "no crops needed" and fell through to the old skill node. The ladder
is the lookup's default now, and the section is in ADDED_SECTIONS.

**A correction.** Earlier in the session Leon was told the drop table
held 15 Epic, 11 Legendary, 8 Mythical and 5 Divine. That came from a
grep that swept the whole file past `items:` and counted perks and pets
too. The real table was 10 Epic, 5 Legendary, 3 Mythical and ONE Divine.

### Open, waiting on Leon

- **"Veel permanente Luck nodig om bepaalde stages te halen"** is half
  answered: small grants mean a pile of them is needed. WHICH stages
  should require permanent Luck, and how much, is not specified.
- **`index_luck`, the 28,700 Coin Tag Luck node**, buys nothing now that
  V312 moved the Luck to the secret index. Same question as Enchant
  Mastery I and II, which have bought nothing since V190.
- **The index completion value for Astral (2.25) is a guess**, like the
  middle three. He only ever gave Common, Uncommon, Rare and Divine.
- **Astral has no armor set**, since the V308 ladder is one rarity per
  set and stops at Mythical. Fine unless he wants one.

### Test first, in this order

1. Upload V320 and check `/version SpaceRNG` says 320. The server was on
   something older than V287 on 3 October.
2. `/options` and `/index`: step both blocks forward and back.
3. `/crops`: a crop that should be locked must be locked. This is the
   one that has been reported most.
4. `/fixhoe`, then `/fixhoe all` as staff.
5. `/pets`: buy a Stardust egg, check it hatches at once and that an
   upgrade asks for Gems.
6. A roll with the animation ladder on Mythical: an Epic should land at
   normal speed with no cutscene.
## V242 to V309, 2 and 3 October 2026 (the beta launch)

The beta opened on 2 October. Leon tested live with players and sent a
stream of requests; almost every one became its own jar. Everything
below is pushed and built green. **Very little of it has been confirmed
in game**, see the last list in this section.

**Read this first: the server was running an old jar.** On 3 October
Leon's /crops still showed the label "Per harvest", text that has not
existed since V287. So whatever was on Minehut then was older than V287,
and he kept reporting things as unfixed that were fixed. Before treating
any report as a new bug, ask him for `/version SpaceRNG` and compare it
with `pom.xml`. Upload: delete the old jar in `plugins`, upload the new
one into `plugins` (not the root), real stop and start.

### What changed, by subject

**Rolling and drops**
- Odds (V304): from Rare up each tier's lowest is 5x the tier below's
  highest. Epic 75,000 to 187,500, Legendary 937,500 to 1,875,000,
  Mythical 9,375,000 to 37,500,000, Divine 187,500,000. Patched by
  rarity and old odds (`rarity-gaps-5x`). Money pays on odds, so these
  pay that much more; Leon was told, did not ask to compensate.
- Reveals by real chance (V299, V303): the cutscene, aura and Auto Roll
  stop only happen when the real chance at the player's Luck was 1 in
  `roll-item.animate-from-one-in` (1,000) or rarer.
  `RarityManager.actualChance`. A player's suggestion; the "multi roll
  animation" half of it is not built.
- A rarity rolled 10 times plays its reveal at half size and half length
  (V298, `roll-item.veteran.*`, counted per rarity in `rolled-by-rarity`
  from V298 on).
- Announcements (V293, V298): a digest every `broadcast.digest-seconds`
  (120). Only the highest tier of that window (a shiny just above its
  rarity), ONE drop, plus a count line of the rest. Firsts keep their own
  banner, Discord still gets every drop.
- Index completion (V304): by tier, `index.completion.by-rarity`
  Common 1.1, Uncommon 1.25, Rare 1.5, Epic 1.6, Legendary 1.75,
  Mythical 1.9, Divine 2.0; a shiny tier 3x. Leon gave only Common,
  Uncommon, Rare and Divine; the middle three are a guess.
- Tag Luck stays as it was (V292). The V291 "secret is your index Luck"
  system is in the jar behind `secret-realm.secret-luck: false`.
- /index counts every rolled drop the player holds, inventory and /pv,
  each time it opens (V308). Players had Divines missing from the index;
  the root cause was not found. Ask who and how if it comes back.
- Crates give no rolled drops (V306, `crates.drop-rewards`).
- Shiny Firsts top 10, the /firsts card shows place, name and date (V300).

**Progression**
- Prestige: each one needs 1 level more than the last, not 5 (V298).
  Levels after a prestige count only rolls since it (V290); the "more to
  go" line was fixed to match in V305.
- Battle Pass XP per roll (V298): 10 / 20 / 50 / 250 / 1,000 / 2,500 /
  5,000, Common to Divine. A Mythical used to be 100,000 of the pass's
  250,000.
- Respec spends unconverted shinies too, Convert all banks shinies (V298).
- Locked skill tree nodes show their name and the page of each missing
  requirement (V299; players could not find Convergence on page 3).
- Speed enchant (WALK_SPEED) is unlocked by `enchant_speed`, a farmtree
  node next to the Wheat root, and is the second enchant on the hoe
  (V298, V300). config-version went to 28 for it.
- Armor (V306, V308): one rarity per set, costs are a chestplate's and
  the pieces take their crafting share (5/8/7/4 of 8, rounded up):
  Leather 40 Common, Chainmail 16 Uncommon, Iron 8 Rare, Gold 2 Epic,
  Diamond 1 Legendary, Netherite 1 Mythical. Old armor is taken back
  once per player on join (`armor.reset-version`, `armor-version` in the
  save) and refunded at the pre-V306 prices into the drop bank. Players
  wiped by V306 before V308 got no refund.
- Rolled drops can not be worn (V298, `DropEquipListener`).

**Social, tab and chat**
- Chat hover over a name shows that player's sidebar lines and "Click
  for their /stats" (V294, V309).
- Tab: prestige after the name in the chat format, `[IV]` (V300).
- Owner: red only, "Owner" floats just above the nameplate (V298, V300).
- Rank bought: big chat banner, title for the buyer (V298).
- Luck reads in K and M everywhere (V299).

**Server and staff**
- Crowd boost (V301, V307, V308): 2x Luck for 15 minutes at 20 online;
  within 5 of the goal chat says how many more; the banner links to
  /store; after a boost the next goal (rounded to 5) is announced and
  holds `reset-hours` (2), then back to 20. Cooldown 60 minutes. Leon
  wrote "dont make it 20 players online"; read as "only shorten it", so
  20 stayed. Confirm with him.
- `/cropwatch <player|list|off>` (V298, staff): rate per second, alerts
  at 30/s, and the watched player's breaks are drawn for the watcher
  (V297).
- `/rngadmin farmboard remove|add|list <player>` keeps someone off the
  farming boards and the daily payout (V298).
- `/rngadmin realm on|off` (V293).
- `/pv` dupe fixed (V298): reopening a page while one was still open
  server side (UI Utils mod) built the new page before the old one was
  saved. `Menus.open` now closes an open vault page first.
- Discord: `/online [player]` (V306). Cards post with
  `/rngadmin discord post <card> <channel id or part of its name>`
  (V295, V306). Cards: `changelog` (posted), `changelog2` (V296 to V308,
  maybe not posted yet).

### Open for Leon

- **AxVaults instead of /pv?** He asked. Advice given: only if the dupe
  still works on V298+. Switching needs a config switch to free /pv,
  /pv contents moved to /stash once, and LuckPerms vault counts per rank.
- **Essentials second welcome:** in `plugins/Essentials/config.yml` set
  `newbies: announce-format: ''` (and `kit: ''` for the starter kit),
  then `/essentials reload`. Not ours to fix.
- The middle index completion values, the armor prices and the crowd
  threshold are guesses awaiting his word.
- Lag: told to use `/spark profiler start`, then `stop`, and send the
  link.

### Never confirmed in game

Nearly everything from V242 on. The ones most worth a check: the /pv dupe
with UI Utils, the armor wipe and refund on join, the reveal threshold
and Auto Roll not stopping, the digest announcements, the crowd boost
messages, crop watch drawing breaks, the farmtree Speed node, and the
Divine index sync.

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

**V211, untested: hot reload.** Leon is installing PlugManX so a jar
update is `/plugman reload SpaceRNG` instead of a restart. The plugin was
not safe for that; see CLAUDE.md for what changed. The first reload is
the test: sidebar, tag, tab name and %spacerng_% placeholders in TAB
should all be there without anybody relogging.

**V212 to V215, 25 September, untested in game:**

- **V212.** The rarity marks take colour only (Divine's underline ran
  under its star). The odds counter sits 0.28 above the crosshair.
- **V213.** The Server First run-up is five seconds for every rarity,
  the finder sees the title too (they saw almost nothing, which read as
  "no build-up"), and real fireworks in the rarity colour go off over
  the spot; `FirstTenFireworks` cancels their damage. A drop that takes a
  First spot skips the plain "just found" chat line
  (`FirstTenManager.wouldTake`). Discord still posts both.
- **V214.** Divine's ground stars are back (WideRipple, inside the first
  ring).
- **V215, pets.** /pets is three screens: main (storage top left, index
  next to it, head top right, the three slots, Make a pet in the middle
  and five blocks under it, green per fifth of the price held), Pet
  Storage (owned pets, wear and upgrade) and Pet Index (every pet, new
  and at its best). A pet costs 100 Cosmic Dust (was 10). `cosmic_root`
  moved to page 1 slot 6,1 over Shiny Unlocked, 100,000 Money; it was on
  page 5 behind the page 4 capstones at 50B. config-version 25 carries
  the skilltree across. The cost is a guess; at 1 in 100 a first pet is
  now about 10,000 rolls on the root alone.

**V216 to V218, 25 September, untested in game:**

- **V216.** Every tracked First rarity gets the run-up (Leon's server
  tracks Epic, which went straight to the banner). The fireworks fail on
  their own instead of ending the run-up. First banners and titles use
  `styleHeading` (colour and bold only), so Divine's underline and
  italic no longer crowd them.
- **V217.** Your own aura on "hidden" also hides its particles. In "out
  of your way" your own copy of the particles skips your face. Divine's
  slanted ring (`PlateRing.behind()`) turns with your head, raised side
  behind.
- **V218, auras by rank.** `auras.by-rank.looks`: linked sigil, comet
  ember, nova eclipse, supernova ascend. The tag rarity colours the look
  and keeps its accent; shiny looks are unchanged. /aura lists which
  rank wears which look. Ember and eclipse as the middle steps were my
  pick, Leon never answered; he can change them in config.

**V219 to V221, 25 September, untested in game:**

- **V219.** Credits keep the sidebar's rainbow everywhere, also as an
  unaffordable price (`Currency.price`); the red footer says it instead.
  Perk confirmation is on for every level of Fortune, Nova and Universe,
  once per player (`default:perk-confirm-legendary` in completed
  quests). Roll Perk lost its level chances line.
- **V220.** /prestige rebuilt to the house style: 45 slots, overview,
  your head, Level up · Prestige · Upgrades, a seven pane road to the
  next prestige, no state tags, standard footers, shift click to level
  as far as the rolls reach. Upgrades show now, next and max.
- **V221.** /buy rebuilt: Ranks · Global Luck · Premium Pass · Perk
  Tickets, your Credits with where they come from, and a web store
  button once `buy.store-url` is set (Leon has not given the link).
  config-version 26 for the store panel text.

**V222 to V224, 26 September, untested in game:**

- **V222.** Cosmetic titles stay out of tab.
- **V223, Bedrock.** Leon has no Bedrock device, so nothing here has
  been seen through Geyser. `platform/Bedrock` recognises a Bedrock
  player (Floodgate UUID, Geyser brand, or the Floodgate/Geyser API) and
  `/rngadmin bedrock on|off [player]` forces the mode on a Java account
  to walk the logic. What changed for Bedrock players only:
  - the big-drop odds counter is a title instead of a text display; the
    item showcase is not spawned (the reel was already titles);
  - auras, the roll circle and the First 10 star are hidden from them
    (Geyser draws text displays as name tags and item or block displays
    not at all), particle accents still reach them;
  - sprites (sidebar icons, the Coins line on the hoe) are left out,
    because Geyser prints the object's description in their place;
  - each leaderboard wall has a twin without player heads, tagged
    `solrng_edition` and shown only to Bedrock (`platform/BedrockSupport`);
  - a crate shows as an ender chest on its barrier, resent every 2 s;
  - pets: a click in storage opens the pet, which has a Wear button;
    perk index: a click asks from one level lower (no number keys);
    Starforge menu: an Auto Roll switch (left click in air is unsure on
    touch);
  - the first tap on a Credits purchase (rank, pass, boost, tickets)
    only arms it, a second tap buys, because on touch reading a tooltip
    is a tap;
  - chat hover texts ([stats]) are written out behind the tag;
  - the Discord server card gives `spacerng.bedrock.minehut.gg` port
    19132 (one-off patch `bedrock-address-card`).
  Known and not fixable server side: red score numbers on the Bedrock
  sidebar, no tab header or footer, hex colours rounded to the 16 legacy
  ones, textured heads in menus shown as plain heads, the boss body,
  floating leaderboard heads and decor items invisible.

- **V224, /help.** Asked for while V223 was still untested. A read only
  54 slot menu (`gui/HelpGui`, aliases /rnghelp and /features): an
  overview, four framed rows (Rolling, Progression, Farming, Rewards) of
  seven features each, and the handy commands. Nothing in it is
  clickable, on purpose: on Bedrock reading a tooltip is a click. The
  end of /guide and the guide's completion message point to it. If
  another plugin (Essentials) takes /help, /rnghelp still works.

**V225 to V229, 27 September, untested in game.** One message, one jar
per subject:

- **V225, the Discord bot.** `discord.bot.link-channel`: a code posted
  there links the account through DiscordSRV, the message is deleted,
  the answer vanishes after 15 s, and the Linked rank and roles follow
  at once. `/rngadmin discord post <card> [channel]` posts a card as the
  bot and edits it in place the next time. The link card is rewritten
  in the layout of the MoneyMC screenshot. Roles sync by UUID, so an
  offline link gets its role too.
- **V226, eggs.** Stardust 100 (P10), Nebula 1,000 (P20, 20x weight on
  Epic and rarer pets), Supernova 10,000 (P20, 400x). Each egg shows its
  live odds per pet rarity. Pets as a whole need Prestige 10
  (`pets.min-prestige`). Because the weights are normalised, 20x the
  weight is not 20x the chance: Divine goes 0.44% to 3.3% to 4.3%.
- **V227, Supernova auto tag.** Rank key `auto-tag`: the best tag (highest
  Luck multiplier) goes on by itself, only ever as an upgrade.
- **V228, /buy** without Perk Tickets (still sold in /perks).
- **V229, the Secret Realm.** Opens every 90 to 240 minutes for 10, P25+,
  `/realm`, `/realm leave`, `/secretindex`, 1 in 40 rolls inside is a
  secret, +2% Luck per secret. Needs `/rngadmin realm here` once before
  it can ever open.

**Answered in the same message:** always day has been in since V171
(`world-time.lock: 6000`, pinned every second). By hand on 1.21.11 the
rule is `/gamerule advance_time false`. The "custom crate" is the Nebula
Crate, for rank key alls and events (`/rngadmin crate keyall nebula 1`).

**V230 to V237, 27 September to 1 October, untested in game except that
linking worked on V230:**

- **V230, our own bot.** JDA 5 shaded in the jar, token in
  `discord.bot.token` (never in the repo). /link gives a code, the Link
  account button in #linking opens a box for it. Links in `links.yml`.
  DiscordSRV only for chat relay, if Leon keeps it.
- **V231** `/rngadmin discord status` and `restart`.
- **V232** Linking pays +100% Luck and nothing else (Leon's call, no
  Credits or Coins), all link texts rewritten. config-version 27.
- **V233** `discord.roles`: Owner, Staff, Supernova, Nova, Comet, Linked,
  OG, Member with emoji, colours, order; `setup` builds them,
  `cleanup` lists and `cleanup confirm` deletes every other role.
- **V234** The sun is pinned with `GameRules.ADVANCE_TIME`, and
  `world-time.clear-weather` stops rain.
- **V235** Hidden ranks Owner ([O], red bold) and Member ([M], grey),
  given with `/rngadmin rank set`. Ranks sort by `priority`.
- **V236** A rank upgrade costs the difference with the rank you have.
- **V237** Nova's aura is heartfall, so the rank looks climb: sigil,
  ember, heartfall, ascend.

**V238 to V241, 1 October, untested in game:**

- **V238** The egg hatch animation (`pet/PetHatch`): wobble, three cracks,
  a hatch burst in the pet's colours, the pet settles. Only the hatcher
  sees it.
- **V239** `gui/MenuStyle`, the look from Leon's reference screenshots:
  "» [NAME] «" gradient title, a three-pane edge pattern, grey inside, a
  close barrier bottom centre. On Ranks (dyed chestplates), Store and the
  crate preview (rewards read name, Reward, two lines, chance).
- **V240** `MenuStyle.apply` puts it on 22 more menus by replacing only
  filler panes. Trees, convert, hoe, vaults, stash, perk roller and the
  crate reel are left alone.
- **V241** The bot's status shows "{online} on spacerng.minehut.gg";
  every role becomes a gradient, but only on a boosted server with
  Discord's enhanced role colours.

- **V242, untested.** New look `solstice` (ascend without the sea
  lanterns: a second slanted orbit turning the other way, six flames at
  the waist), not on any rank yet. `/rngadmin auratest rank [rank]
  [rarity]` wears exactly what a rank gets, to judge the ladder.

- **V243, untested.** Tab: `&` codes now translated (they showed as
  text), and a two column layout from Leon's Solar Skies screenshot
  (`||` in a tab line splits it, cells padded per column). Nova
  (heartfall) a fifth smaller, lanterns at the waist, fewer floor stars.
  Comet's flames (ember, so also Legendary and pyre) half as tall, out of
  the face. Owner red into gold. Linked and Comet otherwise approved.

- **V244** Sidebar Luck rounded to a whole percent.
- **V245, Leon's numbers.** Intermediate Starforge +50 Speed (0.50, was
  5.0); forge Speed reads "+50 Speed", not a percent. A finished rarity
  2x Luck (was 1.25x), a finished shiny rarity 5x (was 2x). Money in
  /stats and the [stats] chat tag shows the 10x base as 1x
  (`Stat.shown()`); payouts still read `total()`.
- **V246** /buy store button: https://spacerng.tebex.store/
- **V247, season launch.** `/rngadmin season reset confirm` wipes every
  player (online now, offline save files now and inventories on next
  join via `season-wipe.yml`), Money through Vault, leaderboards, First
  10, found counts. Owner and Member (hidden ranks) survive. Linked
  wears a check mark again in a very light blue (the letter L was there
  since V187, hidden while TAB drew the list).

- **V248** `/discord` (invite in `discord.invite`, clickable and written
  out for Bedrock). Tab footer says /discord; chat tips follow the real
  invite. Leon is removing DiscordSRV: our bot does links, roles and
  slash commands; only the game/Discord chat relay goes, and links made
  through DiscordSRV before V230 need a new /link.

- **V249** Unranked players are drawn as Member (mint, [M]) in tab and
  chat via `RankManager.shownRankOf`; `rankOf` stays null so nothing
  gated on a rank or link opens up. `ranks.starter` in config.
  Advancements: `/rngadmin advancements off` already existed.

**The tab (2 October).** In game the tab still shows "Server name",
"Used memory", "www.domain.com" and "Ping" behind names. None of that is
in our code. It was the TAB plugin after all, still in `plugins`; Leon
removed it with V242. Check our header, footer and rank names show.

**Waiting on Leon:** the tab he wants. His screenshot arrived a few pixels
wide; ask for it again. TAB plugin defaults were the ugly tab; advice
given to remove TAB so ours draws it.

## Leon's idea list: what is still to do

When Leon asks what is left, answer from this list and from the open
questions further down. Keep it current.

### The pets rework, Leon's full spec (8 October 2026)

He gave this in one message with three screenshots while V358 was being
built, and asked whether it is enough to work from. It is the next
subject after V358 is confirmed in game. It is big enough to be several
jars; the split at the bottom is a proposal, not his words.

**The eggs**

- **One egg per ten Prestige**: an egg at P10, then P20, P30 and up.
- **An egg auto unlocks** when the Prestige is reached. The price on the
  card is **per egg opened, not a price to unlock the egg**. (His
  screenshot shows a card reading "CACTUS [EGG] / Price: $50k /
  Prestige Required: [3]", which is the layout he wants, not the
  numbers.)
- **Eggs cost Gems to open.**
- **Buy 1, 3 or 9 eggs** at a time, and with auto open on it keeps
  buying.
- **Auto open is a toggle, for linked accounts only.**
- **Every egg holds all rarities, Common up to Divine**, and each rarity
  **shows its chance on the card**. Divine is around **0.001%**.
- The egg screen looks like his second screenshot: a grid of pets with
  the ones not yet found drawn as "???  Locked" and a "Chance: 31.86%"
  line on each.

**The pets**

- A pet gives a **stat multiplier**, and in `/pets` a player **picks
  which stat it boosts**, so a player can specialise.
- **Storage holds 100 pets.**
- `/pets` shows the pets owned and the pets worn, like his first
  screenshot: egg tabs along the top, the grid under it, and a bottom
  rail of buttons.
- **Auto delete by rarity**: clicking a rarity marks it to be thrown away
  on sight.
- **Pets level up from Cosmic Dust**, so the further the Cosmic Dust
  skills are levelled, the faster pets level.

**The pet index**

- **Every pet discovered raises Pet Luck**, about **5% each**, and
  **100% more with every pet discovered**.
- **Pet Luck skills go in the skill tree.**
- The pet index also appears **in `/index`**, with its boost shown the
  way the other collections show theirs.

**His answers, same day**

- **5% per pet, whatever its rarity.** "5% if a common gets discovered
  etc". **All the rarities of one egg found is 100%** for that egg. Each
  egg holds seven pets, one per rarity, so "every rarity found" and
  "every pet found" are the same sentence. Say the word if an egg should
  hold more than one pet per rarity.
- **Pet Luck raises the odds of the rarer pets**, and the rarer the
  rarity the lower its chance to begin with.
- **Gems pay.** The $50k on the screenshot was the layout, not the price.
- **The stat is chosen, at any time, in `/pets`**, so a pet is a
  multiplier looking for a stat rather than a fixed one.
- **Storage full stops the opening.** No silent overwrite.
- **The P10 egg runs 1.1x to 2.0x**, Common to Divine. A pet levels to
  **level 10** and **every level is 10% more multiplier**.
- **Nothing carries over.** The pets owned today and the Cosmic Dust
  balance are both wiped with the rework.
- **It may be split over several jars**, as long as it looks right, and
  he wants to **place a hovering pet egg with a command** the way crates
  are placed, holding the egg in his hand when he runs it.

**Still his call, flagged rather than guessed**

- **What the eggs above P10 are worth.** He gave 1.1x to 2.0x for the
  P10 egg and nothing for P20 up, so the ladder uses one growth knob per
  egg and that knob is the single number to move.

**What was built, 8 October**

1. **V359, the pet**: the level ladder, the stat choice, storage of 100,
   the wipe.
2. **V360, the rest**: the ten eggs, the egg screen, 1 / 3 / 9, auto
   open, the pet index, Pet Luck, the skill tree nodes, the page in
   `/index` and the hovering egg placed with a command.

Both are untested in game. The one number left for Leon is
`pets.egg-bonus-growth`, which decides what the eggs above Prestige 10
are worth.

### The list Leon gave on 3 October, after V309, with the audit beside it

One message, twelve subjects. He asked explicitly to check first what
already exists, so each line says what is in the jar today. One jar per
numbered item, in this order unless he says otherwise.

1. **Roll animation as a stepper, not a switch.** `/options` has
   `Rolling Animation` as one on/off (`roll-animation-enabled` in
   PlayerData, V-old) and the global threshold
   `roll-item.animate-from-one-in` (1,000). What he wants is a per
   player ladder on one block: click steps the threshold up (off, then
   Legendary and up, then Mythical and up, ...), right click steps back,
   and the current step is readable on the item. Everything below the
   step rolls at full speed like any other roll.
2. **The same stepper everywhere, because `/options` is too busy.** It
   now draws 16 per rarity buttons: 4 reveal auras, 4 shouts, 7 own drop
   messages. Those three groups become three stepper blocks. His words:
   "dan is het niet zo druk".
3. **`/index` on the same pattern.** Today: 7 rarity tabs in the top row
   (`IndexGui.buildTab`, one per rarity), a shiny toggle, progress, page
   buttons. What he wants: one mode block with three positions, normal,
   shiny index and secret index, plus one rarity block that cycles and
   whose label follows the mode (so "Common shinies" in shiny mode).
   Right click is one back. `/secretindex` already exists as its own
   menu (`SecretIndexGui`, V229) and gets folded in as the third mode.
   The Prestige 10 requirement has to be printed on the secret mode.
4. **Tag Luck moves to the secret index.** Already built, V291, sitting
   behind `secret-realm.secret-luck: false`. Turning it true is the
   change: the secret picked in `/secretindex` replaces the equipped
   drop's Tag Luck. `secret-realm.min-prestige` goes 0 to 10. He keeps
   the realm itself off for now (`/rngadmin realm off`).
5. **An announcement when the Secret Realm opens: yes, it exists.**
   `RealmManager.open` prints a gradient chat banner, a title, a sound
   and a boss bar countdown, and tells players under the Prestige floor
   that they cannot enter. Nothing to build.
6. **Pet eggs.** Three eggs exist (`pets.eggs.tiers`: stardust 100,
   nebula 1,000, supernova 10,000 Cosmic Dust) with rarity bands and
   min-prestige 10 / 20 / 20. What changes: egg 1 never gives Divine,
   egg 2 unlocks at Prestige 10 with a very small Divine chance, egg 3
   at Prestige 25 with ten times that chance, and the costs reworked so
   the dearest is clearly the one to save for.
7. **Eggs hatch on the spot.** He dropped the hatch animation: buying is
   hatching. `PetHatch` exists and gets skipped or shortened.
8. **Pet upgrades cost Gems.** Today rarity costs Cosmic Dust
   (`rarity-base-cost` 8, growth 1.35) and tier costs Farm Dust
   (`tier-base-cost` 25, growth 1.40). Both become Gems, and the growth
   stays exponential but gets rescaled for a Gem budget.
9. **Cosmic Dust as a range, raised by skills.** `DustManager` pays
   exactly 1 per hit and the only skill is `COSMIC_DUST_CHANCE`. He
   wants a second effect that raises the AMOUNT, the amount rolled
   inside a range rather than fixed, and the action bar line to show up
   during Auto Roll too (`pets.dust.announce` already writes one).
10. **Permanent Luck down to small numbers.** `luck_250` is a permanent
    +250% and `luck_50` a +50%, handed out by crates and the Battle
    Pass. He wants 5% from the cheapest crate, 10% from the middle one
    and 25% from the best, and the later Battle Pass stages to need a
    lot of permanent Luck to reach. That means new consumables and a
    pass of every reward table that names the old two.
11. **Crates reached only with `/crates`.** Keys are ALREADY digital
    since V282 (`storedKeys` in PlayerData, `/keys`, auto-store on). What
    is missing: a `/crates` command that teleports to the crate area,
    and the hologram over each crate showing how many of that crate's
    keys you hold.
12. **Potions digital through `/boosters`.** `/boosters` already holds
    potions that Potion Finder found (V264, alias `/potions`). What is
    missing: a potion BOUGHT goes to `/boosters` instead of the
    inventory, the stack counts rather than filling slots, and the names
    read `1.2x Luck` instead of `20% Luck`.
13. **The farm tree has a node hanging off the start.** Confirmed:
    `enchant_speed` sits at page 1 slot "4,6", the only node on row 6
    besides the Wheat root at "5,6". Every other branch leaves the spine
    at row 5. It moves onto a branch.
14. **More drops at Legendary and up, and a tier above Divine.** Counted
    in config today: 76 Common, 72 Uncommon, 40 Rare, 15 Epic, 11
    Legendary, 8 Mythical, **5 Divine**. He wants at least 10 Divine and
    more at every tier from Legendary. Above Divine comes one new
    rarity with a space name, and **not** Galaxy, which he is saving for
    later tiers. A new rarity touches the `Rarity` enum and everything
    that loops it: odds bands, index, options, auras, Battle Pass XP,
    armor, index completion, the scoreboard and Discord. Its own jar.

**Decisions still needed from him before the matching jar:**

- Item 6: what is the highest rarity egg 1 may hatch, Legendary or
  Mythical? And does egg 1 need any Prestige at all (`pets.min-prestige`
  is 10 today, which would block a Prestige 0 player from every egg)?
- Item 10: do the old `luck_50` and `luck_250` stay in players' hands,
  or are they converted down like the V306 armor was?
- Item 14: the name above Divine. Proposed, in order: **Astral**,
  **Celestial**, **Quasar**. Astral leaves Galactic and Universal free
  for the tiers after it.

1. **The Discord bot, testing.** Everything in V225 needs his setup and a
   try in game and in Discord.
2. **/buy and /store better** (idea, 27 September). Not specified yet.
3. **The season safe item** (on hold, his word). An item that survives a
   season reset and is extremely hard to get. There is no season reset
   in the plugin yet, only the Battle Pass season number, so the reset
   itself has to be defined first.
4. **The egg hatch animation** from the pets blueprint, still not built.
5. **The Nova Core menu** in the description block, waiting on his
   screenshots.
6. **Enchant Mastery I and II** buy nothing since V190; what should they do?
7. **The crop yield nodes**: he wants something else there (crop level?).
8. **Secret Realm follow ups**: what a secret should do beyond Luck, and
   whether the realm needs its own build.
9. **More realms, later** (Leon, 3 October). Realms as a family: the
   Secret Realm with its own secret index (that part exists since V229:
   /realm, /secretindex), and a **Shiny Realm** that opens at random times
   through the day. Not specified yet: what rolling inside it does
   (presumably a far higher shiny chance), who may enter, how long it
   stays open, and whether it gets its own index too. The realm code in
   `realm/RealmManager` is built for one realm and would need to hold
   several.

**Queue from the same message, one jar each, in this order:**

1. **The roll reveal.** Mythical is barely audible, the rest is good.
   Particles on the ground at every rarity instead of the block and item
   displays on the ground (those are the auras' look). The question mark
   is gone since V210, so its small to big growth is moot.
2. **Auras by rank.** Done in V218. Every rarity from Epic up wears what Divine wears
   now (ascend), in the rarity's colour, but only at Supernova. Smaller
   looks for lower ranks, and Linked wears what Epic wears now (sigil),
   at every rarity from Epic, only the colour changing. Nova and Comet
   are in between: ask whether ember and eclipse are the middle steps.
3. **Divine's ground stars.** Done in V214, on every rarity for Supernova since V218. The stars that shot outward over the
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

1. **How is a pet earned?** (Eggs since V226, three tiers, see above.) Answered on 24 September: an egg, bought with
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
- **The bot runs inside the plugin (V230)**, on its own JDA 5, because
  Leon wanted a button that opens a box for the link code and
  DiscordSRV's JDA cannot do modals. DiscordSRV only relays chat.
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
