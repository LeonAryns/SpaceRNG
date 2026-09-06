---
name: aura-design
description: Building particle auras, reveal effects and any visual/audio spectacle in SpaceRNG. Load this before writing or changing ANY particle code, aura, enchant proc effect, crate animation or sound cue. Contains the verified Paper 1.21.11 particle catalogue, the geometry cookbook, the choreography rules and the performance budget.
---

# Aura design

The auras are the reason people record clips of this server. Treat them as
the product, not as decoration.

Everything in this file is verified against the exact jar the plugin
builds on: `paper-api-1.21.11-R0.1-SNAPSHOT`. Nothing here is copied from
a wiki or a tutorial, because both drift between versions and inventing a
particle name that does not exist is the single easiest way to ship a
silently broken effect.

## Verify before you invent

Never guess a particle or sound name. Read it out of the jar:

```bash
SCR=<scratchpad>
CP=$(cat "$SCR/cp.txt")
JAVAP="$HOME/.vscode/extensions/redhat.java-1.56.0-win32-x64/jre/21.0.12.1-win32-x86_64/bin/javap.exe"
"$JAVAP" -cp "$CP" org.bukkit.Particle | grep "public static final"
"$JAVAP" -cp "$CP" org.bukkit.Sound | grep -i "beacon\|conduit\|warden"
```

For data types, compile a four-line probe against the same classpath and
print `Particle.getDataType()` for every constant. That is how the table
below was produced, and it caught three particles whose data requirements
changed after 1.21.4 without the public javadocs being updated.

## The API

```java
// Per player. This is what SpaceRNG uses, always.
viewer.spawnParticle(Particle p, Location at, int count,
                     double dx, double dy, double dz, double extra, T data);

// Whole world. Do not use it: it ignores the aura opt-out in /options
// and pushes packets to people who asked not to see them.
world.spawnParticle(...);
```

Per-player is not a style choice. `/options` lets a player switch off the
aura for a rarity, and that promise can only be kept by choosing the
audience. `RollAura.refreshAudience()` is the pattern: filter by distance
AND by `isAuraEnabled(rarity)`, cache the list, re-filter each frame
because people walk.

### count, delta and extra

The four numbers after the location mean two completely different things
depending on `count`, and this is the single most useful thing to know
about the API:

| count | dx / dy / dz | extra |
|---|---|---|
| `>= 1` | Gaussian **spread** in blocks around the point | per-particle speed |
| `0` | a **direction vector** | the speed along it |

So `count = 0` turns the call into "fire one particle THAT way at THAT
speed", which is how you build beams, jets, sparks flying off an impact,
and anything with intent. A cloud with `count = 200` is the lazy option
and it looks like it.

```java
// A jet of soul fire straight up at speed 0.6
viewer.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 0, 0, 1, 0, 0.6);

// A spark thrown along a direction vector
Vector v = target.toVector().subtract(from.toVector()).normalize();
viewer.spawnParticle(Particle.ELECTRIC_SPARK, from, 0, v.getX(), v.getY(), v.getZ(), 0.9);
```

`extra` is overloaded on a few particles: on `NOTE` it picks the colour
(0.0 to 1.0 walks the rainbow), on `EFFECT` and friends it is intensity.

## The catalogue, Paper 1.21.11

115 constants. Grouped by what they look like, because that is how you
pick one.

**Light and magic**
`END_ROD` `GLOW` `ENCHANT` `ENCHANTED_HIT` `PORTAL` `REVERSE_PORTAL`
`WITCH` `EFFECT` `INSTANT_EFFECT` `ENTITY_EFFECT` `TOTEM_OF_UNDYING`
`FIREWORK` `FLASH` `SCULK_SOUL` `SCULK_CHARGE` `SCULK_CHARGE_POP`
`SONIC_BOOM` `SHRIEK` `VAULT_CONNECTION` `TRIAL_SPAWNER_DETECTION`
`TRIAL_SPAWNER_DETECTION_OMINOUS` `OMINOUS_SPAWNING` `RAID_OMEN`
`TRIAL_OMEN` `INFESTED` `FIREFLY`

**Fire and heat**
`FLAME` `SMALL_FLAME` `SOUL_FIRE_FLAME` `SOUL` `LAVA` `DRIPPING_LAVA`
`FALLING_LAVA` `LANDING_LAVA` `DRIPPING_DRIPSTONE_LAVA`
`FALLING_DRIPSTONE_LAVA` `CAMPFIRE_COSY_SMOKE` `CAMPFIRE_SIGNAL_SMOKE`
`COPPER_FIRE_FLAME` `DRAGON_BREATH`

**Smoke, dust and air**
`SMOKE` `LARGE_SMOKE` `WHITE_SMOKE` `ASH` `WHITE_ASH` `DUST_PLUME`
`CLOUD` `POOF` `GUST` `SMALL_GUST` `GUST_EMITTER_LARGE`
`GUST_EMITTER_SMALL` `SNOWFLAKE` `MYCELIUM`

**Impact and combat**
`CRIT` `DAMAGE_INDICATOR` `SWEEP_ATTACK` `EXPLOSION` `EXPLOSION_EMITTER`
`ELECTRIC_SPARK` `WAX_ON` `WAX_OFF` `SCRAPE` `EGG_CRACK` `ITEM_COBWEB`

**Water**
`BUBBLE` `BUBBLE_POP` `BUBBLE_COLUMN_UP` `CURRENT_DOWN` `SPLASH`
`UNDERWATER` `RAIN` `FISHING` `DOLPHIN` `NAUTILUS` `SQUID_INK`
`GLOW_SQUID_INK` `DRIPPING_WATER` `FALLING_WATER`
`DRIPPING_DRIPSTONE_WATER` `FALLING_DRIPSTONE_WATER` `ELDER_GUARDIAN`

**Nature**
`CHERRY_LEAVES` `PALE_OAK_LEAVES` `TINTED_LEAVES` `FALLING_SPORE_BLOSSOM`
`SPORE_BLOSSOM_AIR` `CRIMSON_SPORE` `WARPED_SPORE` `DRIPPING_HONEY`
`FALLING_HONEY` `LANDING_HONEY` `FALLING_NECTAR` `COMPOSTER`
`HAPPY_VILLAGER` `ANGRY_VILLAGER` `HEART` `NOTE` `SNEEZE` `SPIT`
`DRIPPING_OBSIDIAN_TEAR` `FALLING_OBSIDIAN_TEAR` `LANDING_OBSIDIAN_TEAR`

**Blocks and items**
`BLOCK` `BLOCK_MARKER` `BLOCK_CRUMBLE` `DUST_PILLAR` `FALLING_DUST`
`ITEM` `ITEM_SNOWBALL` `ITEM_SLIME`

**The controllable ones**
`DUST` `DUST_COLOR_TRANSITION` `TRAIL` `VIBRATION`

### The 18 that take data

Verified by probing `Particle.getDataType()` on 1.21.11. Three of these
changed since 1.21.4 and the published javadocs are wrong about them.

| Particle | Data | Notes |
|---|---|---|
| `DUST` | `Particle.DustOptions(Color, float size)` | the workhorse. Any colour, size 0.01 to ~4 |
| `DUST_COLOR_TRANSITION` | `Particle.DustTransition(Color from, Color to, float size)` | fades across its lifetime, free animation |
| `TRAIL` | `Particle.Trail(Location target, Color, int duration)` | **travels to a point**. Beams, homing sparks, links |
| `ENTITY_EFFECT` | `Color` | supports alpha via `Color.fromARGB` |
| `TINTED_LEAVES` | `Color` | new, falling coloured leaves |
| `FLASH` | `Color` | **was data-free before 1.21.11**. A coloured screen-filling pop |
| `DRAGON_BREATH` | `Float` | **was data-free before 1.21.11** |
| `EFFECT` | `Particle.Spell(Color, float power)` | **was data-free before 1.21.11** |
| `INSTANT_EFFECT` | `Particle.Spell(Color, float power)` | same |
| `SCULK_CHARGE` | `Float` (roll angle, radians) | |
| `SHRIEK` | `Integer` (delay in ticks) | |
| `VIBRATION` | `Vibration(Destination, int arrivalTicks)` | travels, like TRAIL but vanilla-styled |
| `BLOCK` | `BlockData` | |
| `BLOCK_MARKER` | `BlockData` | |
| `BLOCK_CRUMBLE` | `BlockData` | |
| `DUST_PILLAR` | `BlockData` | |
| `FALLING_DUST` | `BlockData` | |
| `ITEM` | `ItemStack` | |

Passing `null` data still works on all of them (the server falls back to
a default), which is why the existing `DRAGON_BREATH` and `FLASH` calls
did not break on the version bump. Passing data of the **wrong** class
throws `IllegalArgumentException`, so never guess.

### Colour is only available through five of them

If an effect has to be a specific colour, it is built out of `DUST`,
`DUST_COLOR_TRANSITION`, `TRAIL`, `ENTITY_EFFECT` or `FLASH`. Everything
else is whatever texture Mojang shipped. That is why every rarity aura in
this plugin is a dust skeleton with a fixed-colour particle scattered
through it as an accent, and not the other way round.

```java
Color red = Color.fromRGB(255, 60, 60);
new Particle.DustOptions(red, 1.3f);                       // solid
new Particle.DustTransition(red, Color.WHITE, 2.4f);       // fades red to white
new Particle.Trail(target, red, 20);                       // flies to target over 20 ticks
```

Never use pure `255,255,255`. A white dust cloud reads as a rendering
glitch rather than as light. Warm near-white, `255,252,224`, is the
brightest thing that still looks deliberate.

## Geometry cookbook

All of these take a centre `Location` and emit into the audience. The
existing helpers in `RollAura` are `ring`, `sphere`, `dustAt`, `puff` and
`lightningRing`. Add to them rather than re-deriving the trigonometry.

**Ring** - the base shape everything else is built from.
```java
for (int i = 0; i < points; i++) {
    double a = (Math.PI * 2 / points) * i;
    at(centre.clone().add(Math.cos(a) * r, y, Math.sin(a) * r));
}
```

**Rotating ring** - add `elapsed * speed` to the angle. Costs nothing and
turns a static shape into a living one. Do this by default.

**Sphere** - rings stacked with the radius following a sine.
```java
for (int ringIndex = 1; ringIndex <= rings; ringIndex++) {
    double phi = Math.PI * ringIndex / (rings + 1);
    double y = Math.cos(phi) * radius;
    double r = Math.sin(phi) * radius;
    // ring at (y, r)
}
```

**Helix / DNA** - a ring whose y climbs with the angle. Two of them offset
by PI is the double helix.
```java
double a = t * turns * Math.PI * 2;
add(Math.cos(a) * r, t * height, Math.sin(a) * r);
```

**Vortex** - a helix whose radius shrinks as y rises: `r * (1 - t)`.
Reads as "being pulled in", which is the implosion.

**Shockwave** - a ring whose radius is `(elapsed % period) / period * max`.
Resets on a loop, so it reads as a pulse rather than a one-off.

**Beam / laser** - step along a direction vector.
```java
Vector dir = to.toVector().subtract(from.toVector()).normalize().multiply(0.25);
Location cursor = from.clone();
for (int i = 0; i < steps; i++) { at(cursor); cursor.add(dir); }
```

**Local coordinates** - the Java equivalent of `^ ^ ^` in the command. Use
this for anything that should come out of where the player is *looking*
rather than where they are standing.
```java
Location eye = player.getEyeLocation();
Vector forward = eye.getDirection();                          // ^ ^ ^1
Vector up      = new Vector(0, 1, 0);
Vector left    = up.clone().crossProduct(forward).normalize(); // ^1 ^ ^
// 2 blocks ahead, half a block left:
Location p = eye.clone().add(forward.clone().multiply(2)).add(left.clone().multiply(0.5));
```

**Star polygon** - connect every k-th point of an n-point ring. A 5-point
star is n=5, k=2. Cheap and instantly reads as "special".

**Orbit** - one or two particles at a large radius moving fast. Two
orbiting motes read as more magical than two hundred static ones, and
cost a thousandth as much.

**Lissajous** - `x = sin(a*t), z = sin(b*t + phase)`. Different a:b ratios
give completely different signatures. Good for making two auras that use
the same colour still feel unmistakably different.

## Choreography

An aura is a piece of music with pictures. The structure that works here,
already implemented in `RollAura`:

1. **Build-up** - `0.0` to `IMPLODE_FROM`. Strands wind outward, a ring
   marks the footprint, a note ladder climbs underneath the score.
2. **Implosion** - everything thrown outward gets dragged into one point
   above the player's head, and **the score goes silent**. The silence is
   what makes the next beat land. Nothing is scheduled here on purpose.
3. **Detonation** - one frame, everything at once.
4. **Aftermath** - expanding rings settling out, and the ding.

Cue positions are **fractions of the run**, not tick numbers. That is what
lets the same score stretch from a 10 second Mythical to a 15 second
Divine and land with more air between the hits instead of playing faster.

```java
cues.add(new Cue(0.34, Sound.BLOCK_CONDUIT_ACTIVATE, 4.0f, 0.6f));
```

Rules that came out of tuning these:

- **The title is held back.** Dropping a full-screen item name over the
  burst hides the thing the player waited fifteen seconds for. See
  `titleDelayTicks`.
- **The ding lands with the visual, not after it.** If the burst is on
  frame 1, the chord belongs around frame 5, not frame 26.
- **Volume is range.** Roughly 16 blocks per 1.0 volume. Set the aura's
  `viewRange` to match the loudest cue, so anyone who can hear it can also
  see what is making the noise.
- **Every rarity needs a distinct ending, not just a distinct build-up.**
  A shared generic ending is the fastest way to make a rare drop feel
  cheap, and it is exactly the complaint that produced this skill.

## Performance budget

Particles are packets. Every one is sent to every viewer.

- Budget roughly **1,500 particles per frame per viewer** as an absolute
  ceiling, and stay far below it outside the detonation frame. The
  build-up in this plugin sits in the low hundreds.
- Thin shapes out as they grow. A sphere at radius 20 with the same ring
  and point counts as at radius 2 becomes an opaque wall and eats frames.
  `RollAura.beatFinale` drops from 7 rings to 5 past the halfway point for
  exactly this reason.
- Prefer **one bright particle over ten dim ones**. `DustOptions` size
  scales visually for free; count does not.
- Particle calls must be on the **main thread**. Anything computed
  asynchronously has to hop back with `runTask` before it emits.
- Cull by distance every frame, not once at the start. People walk away.

## House rules for SpaceRNG

**Scale belongs to rarity, identity belongs to design.** Divine is always
the longest, widest, loudest thing in the plugin. Which *performance* sits
on which rarity is a design choice and can be swapped; the scale table
never inverts.

Current table, from `RollAura`:

| Rarity | Duration | Finale | Radius | Strands | View range |
|---|---|---|---|---|---|
| Divine | 300t / 15s | 60t | 9.0 | 9 | 72 |
| Mythical | 200t / 10s | 46t | 7.0 | 7 | 64 |
| Legendary | 100t / 5s | 28t | 5.0 | 5 | 48 |
| Epic | 60t / 3s | 20t | 3.5 | 4 | 32 |

**One identity per rarity.** A colour, an accent particle, a score and an
ending. If two rarities share three of the four, one of them is wasted.

**Colour meanings already in use:** Divine warm near-white `255,252,224`,
Mythical red `255,60,60`, Legendary orange `255,170,0`, Epic violet
`168,85,247`.

**Respect the opt-out.** Every emitter goes through the audience list.
A new effect that calls `world.spawnParticle` is a bug even if it looks
right.

## Adding a new aura

Everything for a rarity lives in five switches in `RollAura`:
`durationFor`, `finaleTicks`, `maxRadiusFor`, `strandsFor`, `colorFor`,
`accentFor`, `scoreFor`, plus any `rarity ==` branches in `drawBuildUp`,
`drawImplosion`, `detonate` and `aftermath`.

For anything that is not a roll reveal (crate opens, enchant procs,
Starforge abilities), build it as its own class following the same shape:
a cached audience, a frame counter, a fractional score, and a `safely()`
wrapper so one bad frame cancels the effect instead of spamming the
console every tick forever.

Test with `/rngadmin aura <rarity>`. Watch it from 40 blocks away as well
as from on top of it, because the two look nothing alike and players will
mostly see the far one.
