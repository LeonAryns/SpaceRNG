---
name: enchant-design
description: Designing, balancing and wiring hoe enchants in SpaceRNG. Load this before adding, removing, renaming or retuning ANY enchant, before changing max-level, per-level, base-cap or cost-growth, and before writing the code that makes an enchant do something at harvest time. Contains the architecture, the curve maths, the balance traps that have already bitten, and the wiring checklist.
---

# Enchant design

Enchants are the deepest sink in the plugin. A player buys a farm tree
node once and then pours Coins into a single enchant for hours, so an
enchant that is mistuned is not a small mistake: it is either the only
thing anybody buys, or a wall nobody ever climbs.

## The architecture

Three pieces, and the split is deliberate.

| Piece | Where | What it decides |
|---|---|---|
| **Unlock** | an `UNLOCK_ENCHANT` node in `/farmtree` | whether you have it at all |
| **Level** | Coins, right-clicking the hoe | how strong it is |
| **Cap** | `base-cap`, raised by `ENCHANT_CAP` mastery nodes | how far you may take it |

A handful of one-time progression choices sit in the tree; the sink you
return to constantly sits on the tool. Neither menu has to explain the
other.

**The hoe itself stores nothing.** Its power is read back from the tree
and the player's bought levels every single time it is used. A bound,
undroppable hoe with no state cannot be lost, traded or duplicated, and
re-locking an enchant cannot leave a hoe carrying power it is no longer
entitled to. Never add a field to the item.

Definitions live in `farming.enchants` in config. What each one DOES is
applied in `FarmPlotManager` at harvest time, keyed off the id.

## The three kinds

Decide which one an enchant is before writing a line of it, because the
whole shape of its numbers follows.

**Always-on.** The value is added to a number, every harvest, with no
roll. Coin Greed, Speed, Momentum. `isProc()` must return false for
these. `per-level` is a fraction of that number: `0.001` means "+0.1% per
level".

**Proc.** The value IS a probability, rolled once per crop. Gem Greed,
TNT Blast, Key Finder. `per-level` is a chance per level, so
`per-level x level` must stay well under `1.0` at the cap or the enchant
stops being a proc and becomes a guarantee. Anything at or above 1.0 at
max level is a bug.

**Window.** The proc opens a timed buff rather than paying out once.
Coin Booster, Gamba. These need `data.applyBoost(key, multiplier, millis)`
and they must not stack with themselves: a second proc during an open
window extends or replaces, never multiplies. Check the window is closed
before opening a new one.

## The curve, and the trap in it

An enchant is defined by four numbers that are **not independent**:

```
max-level     the hard ceiling, only reachable with full mastery
base-cap      what you start able to buy, before mastery nodes
per-level     what one level is worth
cost-growth   price multiplier per level, compounding
base-cost     price of level 1
```

Two invariants. Break either and the enchant is broken.

### 1. Value at the ceiling is `per-level x max-level`

That product is the enchant's whole identity. If you change `max-level`,
`per-level` must move by the inverse or the enchant silently becomes ten
times stronger or weaker.

```
1,000 levels x 0.01  = +1000% at the cap
10,000 levels x 0.001 = +1000% at the cap     same enchant, longer grind
10,000 levels x 0.01  = +10000%               a completely different enchant
```

### 2. Cost at the ceiling is `base-cost x growth^max-level`

This one compounds, so it punishes carelessness hard. `1.007` is a
sensible growth over a thousand levels and catastrophic over ten
thousand:

```
1.007 ^ 1,000  = about 1,100x       last level costs ~2.7M Coins. Fine.
1.007 ^ 10,000 = about 2 x 10^30    the last level costs more Coins than
                                     will ever exist. Not fine.
```

**Rule: when you multiply `max-level` by ten, divide `per-level` by ten
and take the tenth root of `cost-growth`.** For 1.007 that is about
1.0007. Always compute the value AND the price at the ceiling before
shipping a curve, and write both in a comment next to it.

### 3. Anything that is not a percentage needs its own ceiling

The worst bug this plugin has shipped came from a linear scale on a
radius:

```java
int radius = 1 + level / 3;      // at level 1000 this is 334
```

That is a 669 by 669 block square broken per click. Percentages tolerate
huge level counts because they stay meaningful at any size; **counts,
radii, ranges and durations do not**. Scale those in explicit steps with
a hard `Math.min` cap:

```java
int radius = Math.min(7, 1 + level / 150);
```

If a value has a unit that is not "percent", cap it in code, not just in
config.

## Balance

- **Every enchant runs on the same scale.** All of them are percentages
  to the same ceiling, so "level 400 of 10,000" means the same thing
  whichever one you are looking at. A player comparing two enchants
  should be comparing what they DO, not decoding two different number
  systems.
- **Order is difficulty.** An enchant unlocked later in the tree should
  be worth more per level, cost more per level, or both. If a late
  enchant is weaker than an early one, nobody will ever buy it and you
  have wasted a node.
- **A proc that fires more often than about once a second is noise.**
  Farming breaks blocks fast. Anything with a visual or a sound needs its
  chance tuned so the effect is a moment, not a texture.
- **Nothing may pay Credits.** Credits are the store's currency;
  gameplay-earned Credits are Leon's decision to make, not a designer's.
  Check with him before wiring one.
- **Nothing may multiply into itself.** An enchant that raises the chance
  of the enchant that raises its own chance is an exponent waiting to be
  found. `ENCHANT_PROC` already multiplies every enchant at once, so no
  individual enchant may also scale procs.

## Wiring

Effects are applied in `FarmPlotManager`, in the harvest path, keyed by
id. The existing hook points, in the order they run:

| Where | What belongs there |
|---|---|
| the multiplier block | anything adding to the Coin payout |
| after `tokens` is computed | doubling, Gems, bonus currency |
| the proc block | chance-rolled one-offs |
| `regrowTicksFor` | anything changing how fast a plot comes back |
| the chain block | multi-block breaks, explosions, lightning |

Read the enchant's power with `hoe.powerOf(data, "ID")`, which already
folds in the level, the mastery cap and the global `ENCHANT_PROC`
multiplier. Never read levels directly.

`HoeEnchantManager.isProc(id)` decides how the number is presented, and
`describePower` is what the menus quote. Both need a case for any enchant
whose number is not a plain percentage.

## Safety, which is not optional

Farming happens on a shared field on a live server.

- **Explosions must never break blocks.** Use
  `world.createExplosion(loc, power, false, false)`: no fire, no block
  damage. The four-argument form defaults to breaking terrain and will
  eat the farm.
- **Lightning is `strikeLightningEffect`, never `strikeLightning`.** The
  second one is real: it sets fires and kills players.
- **Damage, if an enchant ever deals any, goes through
  `entity.damage(amount, player)`** so protection plugins can veto it.
  See `aura-design` for the rest of that.
- **A multi-block enchant harvests plots, not blocks.** Break only real
  farm plots, from the plot registry, or somebody's build is gone.
- **Cap what a single click can do.** A Nuke that credits ten thousand
  crops is fine. A Nuke that iterates ten thousand real blocks in one
  tick is a server freeze.

## Feedback

An enchant nobody notices firing might as well not exist.

- Every proc needs a sound, and the loud ones need a particle. Load
  `aura-design` before writing either.
- Randomise the pitch on anything that fires more than twice a second.
- Big procs get a message; small ones get an action bar at most. Chat
  spam from a proc is worse than silence.
- The hoe's own lore lists every owned enchant with its level, so a new
  enchant appears there for free. Check it still fits: a hoe with twelve
  enchants and twelve description lines is unreadable.

## Naming and presentation

`menu-design` governs the text. The short version:

- Sentence case, never caps. Currencies keep their capital.
- The name says the effect, not the fantasy. "Coin Greed" beats
  "Avarice".
- One colour per enchant, and it should match what it does: Coins gold,
  Gems aqua, destruction red, luck green.
- The description is one line and says what it does to a number, not how
  it feels.

## Checklist for adding one

1. Pick the kind: always-on, proc, or window.
2. Write the ceiling value and the ceiling price in a comment, computed,
   not guessed.
3. Check `per-level x max-level` is under 1.0 for a proc.
4. Cap anything with a unit other than percent, in code.
5. Add the config block under `farming.enchants`.
6. Add an `UNLOCK_ENCHANT` node in `/farmtree`, deeper than any enchant
   it should follow, plus `ENCHANT_CAP` mastery if it needs the full
   range.
7. Wire the effect at the right hook in `FarmPlotManager`.
8. Add a case to `isProc` and, if the number is unusual, `describePower`.
9. Give it a sound, and a particle if it is loud.
10. Rebuild the hoe lore mentally: does the tooltip still fit on screen?
