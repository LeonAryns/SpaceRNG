---
name: menu-design
description: The SolRNG house style for inventory menus, item lore, chat messages and the sidebar. Load this before writing or restyling ANY GUI, item tooltip, broadcast or scoreboard line in this plugin, and before adding a new currency, icon or colour anywhere.
---

# SolRNG menu & lore design

Every menu in this plugin has to look like it came from the same product.
This file is the house style. Follow it rather than inventing a new
layout, and extend `Lore` / `Currency` rather than hand-rolling colour
codes in a new class.

**Reference implementations** - read one before writing a new menu:
- `gui/PrestigeGui.java` - the card layout (title, state, sections, footer)
- `gui/HoeGui.java` - the grid-of-upgrades layout with a level badge
- `gui/Lore.java` - the shared text vocabulary
- `gui/Currency.java` - currency icons and colours

## The two shared vocabularies

`Lore` owns text shapes. `Currency` owns money. Neither should be
bypassed:

```java
Lore.title(ChatColor.AQUA, "Prestige 4")   // 「 Prestige 4 」
Lore.state("ascend")                        // [ASCEND]
Lore.section(ChatColor.YELLOW, "Requirements")
Lore.line(ChatColor.GREEN, "Each prestige multiplies all Luck.")
Lore.stat(ChatColor.AQUA, "Level", "12")    // ▎ Level: 12
Lore.requirement("Rolls", "4.1K", "5K", false)
Lore.upgrade(ChatColor.GOLD, "Luck", "1.75x", "2x")
Lore.bar(0.62)                              // hue-ramping progress bar
Lore.shorten(12400)                         // "12.4K"

Currency.COINS.amount(717_000_000L)         // 717M Coins ●
Currency.TOKENS.price(5000L, affordable)    // 5K Tokens ■, red if not
```

If a new menu needs a text shape that isn't in `Lore`, add it to `Lore`.
A one-off `ChatColor.GRAY + "▎ " + ...` written inline is how the plugin
drifts back into a dozen private styles.

## Item layout

Every clickable item follows the same shape. Omit a part when it has
nothing to say; never reorder them.

```
1  DISPLAY NAME   Lore.title(...) or a HoeGui-style [3★] badge + name
2  SECTIONS       Lore.section() headers, each followed by ▎ bullet lines
3  (blank)
4  ACTION FOOTER  what a click does, or why it can't
```

The blank lines are load-bearing. A tooltip with no breathing room reads
as a wall and people stop reading it.

### The description block, for anything somebody buys

A price tag is not a description. Anything a player spends Credits,
Gems or real progress on gets the longer block below, which is the shape
every well run store menu converges on, with our own title on top of it:

```
1   NAME          the gradient name, bold, and nothing else on the line
2   SUBTITLE      dark grey, and only when it says something the name
                  does not: "Permanent rank", "Free while linked"
3   (blank)
4   PITCH         one or two lines, second person, naming the thing
5
6   (blank)
7   SECTION       "Perks", "Commands", "What it changes"
8   ▎ one per line, the shortest noun phrase that is still true
...
12  (blank)
13  ▎ Price  7,000 Credits ✪
14  (blank)
15  ACTION FOOTER
```

- **One perk per line, and no sentences.** "Private vaults  7 pages",
  never "This rank gives you seven pages of private vault space." A
  reader scans the marks down the left edge and stops at the one they
  came for.
- **The pitch says why, the perks say what.** If the pitch repeats a
  perk it is a wasted line, and it is the one place a thing being sold
  is allowed a voice of its own.
- **The price is a stat line, not its own section.** A "Price" header
  over a single line costs two rows and says nothing the ✪ does not.
- **Commands get their own section** only when there are three or more,
  and even then they sit on one line under it. The screenshot spends
  nine rows on nine slash commands, which is nine rows saying one thing,
  and it is most of why that tooltip does not fit on the screen.

### Length is a hard limit, not a preference

**Keep a tooltip to about 16 lines, and never past 20.**

Minecraft draws a tooltip at the cursor and does not fit it to the
screen. Past a certain height it is simply cut off at the top and the
bottom, and where that starts depends on the player's resolution and GUI
scale, which the server cannot see. It is an open vanilla bug rather
than something a plugin can work around: MC-26757, MC-161929 and
MC-253053 are all the same report, still unfixed.

The reference screenshot Leon brought for this proves it. That rank
tooltip runs about 28 lines, and in the screenshot it is clipped at both
ends, so the name at the top and the price at the bottom, the two lines
that actually sell the rank, are the two the player cannot read.

When a block gets long, cut content rather than the spacing. The blank
lines are what make it readable. The ninth perk is not.

**No empty category tag under the name.** The subtitle in the
description block above is the exception that proves this rule: it earns
its row by saying something the name cannot. `Lore.state("upgrade")` under an item
called "Luck I", or `[FREE TRACK]` under one called "Free 12", is a line
that costs a row and says nothing. Use `Lore.state` only when it carries
something the title genuinely doesn't - a season name, a mode. The
locked/ready/claimed distinction belongs in the footer, where it already
lives.

### The action footer, verbatim

Only these five. They are bold and **sentence case, never caps** - the
bold and the colour already carry the emphasis, and shouting on top of
both reads as noise. The colour is the whole message:

| Footer | Colour | Means |
|---|---|---|
| `Click to buy` / `Click to claim` / `Click to upgrade` | YELLOW | you can do it now |
| `Not enough Coins` (or Gems/Credits) | RED | you'll be able to |
| `Locked` | RED, plus a grey line naming the way in | something else first |
| `Maxed` / `Unlocked` / `Claimed` | GREEN | done, nothing to do |
| `Sold out` / `Coming soon` | DARK_GRAY | not a thing yet |

A currency's name keeps its capital - Coins, Gems, Credits, Money are
proper nouns here. Everything else in a footer is lower case after the
first word.

A `Locked` footer must always be followed by where to go:
`▎ Unlock it in /farmtree`. Telling someone no without telling them how
is the single most common way a menu becomes frustrating.

## Colour means one thing

Colour carries the state. It is never decoration:

- **GREEN** - done, owned, met, good
- **YELLOW** - actionable right now, a price you can pay
- **RED** - blocked, unaffordable, unmet
- **GRAY** - ordinary body text
- **DARK_GRAY** - labels, tags, footnotes, "not found" placeholders
- **AQUA** - informational, counts, secondary stats
- **WHITE** - a value inside a `Lore.stat` line, and section headers on
  the sidebar

Floating text in the world (holograms, the boss panel, the podium) is the
exception: no grey description lines there, they are white. Grey on a
text display against the sky reads as washed out, and Leon asked for
white more than once (V157).

The `▎` bullet carries the line's colour and the text after it stays
grey. That way a lore block scans as a column of coloured marks and you
can read its state without reading the words.

### Never

- **Pure white on a drop name.** Drop names render next to player names
  in the tab list, where white-on-white is unreadable. `RollFormat`
  enforces this; don't reintroduce it.
- **ALL CAPS anywhere inside a menu item** - not the name, not the
  footer, not a state tag. The user has rejected caps three times now.
  Caps survive only as deliberate headers in *chat* output (the guide
  banner, the `/top` header) and on the sidebar title.
- Gold for anything that isn't Coins, on the sidebar.
- A **different icon for the same family of skill**. Luck I through Luck
  VIII are all a rabbit's foot; if Curator I is a bookshelf then Curator
  II is a bookshelf too. Recognising a skill you already understand is
  most of what makes a deep tree readable.

## Icons

Only glyphs from Minecraft's built-in unicode font - no resource pack is
assumed. These are proven in this plugin:

```
▎ bullet     ✔ yes        ✘ no         ➜ becomes
★ level      ✦ shiny      ◇ hollow     ⚡ event
◀ ▶ pages    「 」 title    ⎯ rule       ▬ bar segment
● Coins      ■ Tokens     ◆ Gems       ✪ Credits
```

Currency glyphs come from `Currency`, never typed inline. They are four
different **shapes**, not four decorations - colour alone stops working
the moment two currencies sit on adjacent lines.

The glyph **trails** the amount (`717M Coins ●`, not `● 717M Coins`).
Minecraft's font is proportional, so four different leading glyphs are
four different widths and every amount starts in a slightly different
column. Trailing it, a stacked list lines up.

## Menu layout

- **54 slots** for a grid or a tree; **45** for a card layout. Don't use
  a size the content doesn't fill - empty rows read as unfinished.
- **Filler** is `BLACK_STAINED_GLASS_PANE` with a single-space name.
  Section breaks use the same pane so the eye reads them as structure.
- **Locked / undefined slots** are `STONE_BUTTON` named `???` with a
  dark-grey reason. Showing a locked thing exists beats hiding it. Leon
  replaced `GRAY_DYE` with stone buttons in V94; don't bring the dye back.
- **Navigation**: `SPECTRAL_ARROW` = back, `ARROW` = forward, and both
  say `Page N/M`. Keep them in the same slots across menus. In a menu
  that is climbed from a root at the bottom - the skill trees - they read
  **▲ Page Up** and **▼ Page Down**, not next/previous: calling a higher
  page "next" fights what the layout is saying.
- **The player's own panel** goes top-right or bottom-right and uses
  their head (`PLAYER_HEAD` + `SkullMeta.setOwningPlayer`).
- **Glint** (`meta.setEnchantmentGlintOverride`) marks *done* or
  *claimable*. Never use a colour-coded dye to say "done" - that throws
  away the icon, and the icon is what makes the slot recognisable.

## Titles

`ChatColor.<accent> + ChatColor.BOLD + "Name"`, optionally
`+ ChatColor.GRAY + " - " + subtitle`. Each menu keeps one accent for
life: Skill Tree DARK_PURPLE, Farming DARK_GREEN, Index DARK_AQUA,
Prestige DARK_PURPLE, Battle Pass GOLD, Nova Core LIGHT_PURPLE.

Inventory titles can't take hex colours - legacy codes only. Display
names and lore lines can, which is where gradients belong.

### Gradients

`Lore.gradient(text, bold, stops...)` paints per character in hex, and
`Lore.header`, `Lore.banner` and `Lore.rainbow` are the three the plugin
already speaks in. Reach for one of those before inventing stops.

A gradient is for a **name**: a display name, a rank, a sidebar header.
Never a body line. Colour carries state everywhere else in this file,
and a gradient says nothing about state while taking the colour away
from the thing that did. A gradient across a whole paragraph is also
unreadable at Minecraft's font size, and it costs six characters per
character against the line length limit.

## Writing the words

- Second person, present tense: "Rolling is what levels you."
- Say the effect, not the mechanism: "+5% Luck per level", not
  "adds 0.05 to bonusLuck".
- A leveled thing shows **both** the per-level value and the running
  total, so a half-bought skill answers "what am I getting *now*"
  without arithmetic.
- Numbers use `Lore.shorten` / `RollFormat.abbreviate` in tight lines and
  `String.format("%,d", n)` where the exact figure matters.
- Two lines of description beat five. Wrap at roughly 40 characters.

## Before you finish

- Would a player who has never seen this menu know what to click?
- Does every locked thing say how to unlock it?
- Does every price say whether they can afford it?
- Is any colour being used decoratively? Remove it.
