package com.spacerng.solrng;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Keeps a server's config.yml in step with the jar without anyone deleting
 * the file.
 *
 * Structural sections are rewritten whenever the on-disk
 * {@code config-version} is behind the jar. Everything else on disk is
 * left alone, so a server owner's item table, prices and tweaks are kept
 * but the structural sections catch up automatically. A "structural"
 * section is one the owner has no reason to have touched by hand: the
 * skill tree layout, the shiny base, the perks roll table. If that ever
 * changes, list it here and bump {@code config-version} in config.yml.
 *
 * Patches are for a single value whose default changed in a section that
 * isn't structural. Each is applied once, and only while the server still
 * holds the old default, so a value somebody picked by hand is never
 * touched. Applied patches are remembered under applied-patches, so a
 * one-line change needs no version bump and rewrites no structural
 * section.
 */
public final class ConfigMigrator {

    /**
     * Sections that will be overwritten when the on-disk config version
     * is behind the jar's. Any section not listed here is preserved
     * even across a version bump.
     */
    private static final List<String> STRUCTURAL = List.of(
            "skilltree", "farmtree", "shiny", "perks", "linked-account",
            // NOT farming.enchants. V186 put it here to carry the 10,000
            // level ceiling across and that was wrong twice over: the
            // ENCHANT_PATCHES below already carry it one max-level at a
            // time, and a structural rewrite takes the base-cost and the
            // cost curve with it, which are Leon's numbers.
            // V186: the milestone ladders. Leon asks for these to be
            // changed rather than editing them on the server, and a whole
            // rewritten tier list cannot be delivered one Patch at a time.
            // If he ever does tune them by hand, take this back out.
            "milestones",
            // V324: the pet table. It is a catalogue now, not a set of
            // numbers: forty-two entries carrying a name, an icon, a
            // rarity and a stat, with what a pet is WORTH living in
            // pets.multipliers and what it costs in pets.upgrades, both
            // outside this list. So nothing Leon tunes by hand is inside
            // it, and a table that grew from nine to forty-two cannot
            // reach the server any other way.
            "pets.types",
            // V345: the secret ladder. Thirty entries with their odds and
            // multipliers spread evenly from 1 in 1,000 to 1 in 1,000,000,
            // which cannot be delivered one Patch at a time, and nothing
            // in it is tuned by hand.
            "secret-realm.secrets",
            // V346: the prestige board is the Secret Realm now. Five
            // upgrades replacing six, with a requires chain, which no
            // Patch can deliver, and nothing in it is tuned by hand.
            "prestige.upgrades");

    /**
     * Sections copied from the jar whenever the server's config has none
     * yet. A dotted path works too, for a new entry inside a section the
     * server already has, like one more hologram panel.
     */
    private static final List<String> ADDED_SECTIONS = List.of(
            // V313: brand new keys, so a Patch cannot carry them - there
            // is no old default to match. Without these the live server
            // falls back to the code default of 0.0 and no egg would ever
            // hatch a Divine.
            "pets.eggs.instant",
            // V314: the Cosmic Dust range. New keys, so ADDED_SECTIONS
            // rather than a patch.
            "pets.dust.cosmic-min", "pets.dust.cosmic-max",
            // V315: the three small permanent Luck grants. The crates and
            // the pass are rewritten to hand these out further down, so
            // the definitions have to arrive first or those rewards point
            // at a consumable the server has never heard of.
            "consumables.luck_5", "consumables.luck_10", "consumables.luck_25",
            // V319: Astral. The rarity definition first, then every ladder
            // that is keyed by rarity name. Each of these is a new key, so
            // a Patch has nothing to match on, and a missing one does not
            // error - it silently falls back to a code default, which for
            // a top rarity means it would quietly rank below Divine.
            // V320: the crops farmed ladder. It is a sub-section added to a
            // farming: block every older config already has, so it never
            // merged and Leon could not tune it on the server.
            "farming.crop-unlock-at",
            // V324: the pet multiplier ladder, and the per-rarity chances
            // on each egg. All new keys, so a Patch has nothing to match
            // on; without them the live server runs the code defaults and
            // Leon cannot tune either by hand.
            "pets.multipliers",
            "pets.eggs.tiers.stardust.chances",
            "pets.eggs.tiers.nebula.chances",
            "pets.eggs.tiers.supernova.chances",
            // V329: the Secret Realm panel, for /rngadmin holo panel realm.
            "holograms.panels.realm",
            // V335: the two stops on the Bonus Roll chain. New keys, so a
            // Patch has nothing to match on, and without them the live
            // server runs the code defaults.
            "roll-item.bonus-roll.max-chance", "roll-item.bonus-roll.max-chain",
            // V331: what one Nova Finder proc pays.
            "farming.procs.nova-finder-amount",
            // V330: the store lines. New keys inside a buy: section every
            // server already has, so each one comes across on its own.
            "buy.announce", "buy.console-only", "buy.announce-credits",
            "buy.announce-rank", "buy.thanks",
            // V322: every forge is one flat chance. A new key, so a Patch
            // has nothing to match; without this the server runs on the
            // code default and Leon cannot tune it by hand.
            "novacore.flat-chance",
            // V321: the two new heavy-aura limits.
            "auras.heavy.piece-threshold", "auras.heavy.swaps-per-check",
            "rarities.ASTRAL",
            "index.luck-multipliers.ASTRAL",
            "index.completion.by-rarity.ASTRAL",
            "index.points-per-rarity.ASTRAL",
            "pass.xp.per-roll.ASTRAL",
            "boss.roll-damage.ASTRAL",
            "auras.shiny.ASTRAL",
            "auras.tag.ASTRAL",
            "pets.eggs.tiers.stardust.divine-chance",
            "pets.eggs.tiers.nebula.divine-chance",
            "pets.eggs.tiers.supernova.divine-chance",
            // V195: the comet on an Epic or better roll. A dotted path,
            // because roll-item is in every config there has ever been.
            "roll-item.comet",
            // V197: the keys added INSIDE that section afterwards. A
            // section only arrives once, when it is missing entirely, so
            // anything added to it later has to come across on its own or
            // a server that took the V195 version never sees it and Leon
            // cannot tune the one timing that matters.
            "roll-item.comet.stage-seconds", "roll-item.comet.counter-scale",
            "roll-item.comet.mystery-head",
            // V198: where the two pieces in front of the roller are put.
            "roll-item.comet.screen",
            // V200: how big the question mark head is drawn.
            "roll-item.comet.head-scale",
            // V210: the falling comet and the question mark, both off.
            "roll-item.comet.falling", "roll-item.comet.question-mark",
            // V218: the aura look by rank.
            "auras.by-rank",
            // V220: the web store link for /buy.
            "buy",
            // V225: the channel the bot reads link codes in, and where it
            // posts cards.
            "discord.bot.link-channel", "discord.bot.cards-channel",
            // V230: the bot logs in on its own.
            "discord.bot.token", "discord.bot.guild-id", "discord.bot.link-button",
            // V233: the server's roles with emoji and colours.
            "discord.roles",
            // V234: no rain.
            "world-time.clear-weather",
            // V241: the player count under the bot's name.
            "discord.bot.status",
            // V291: the Secret Seeker prestige upgrade.
            "prestige.upgrades.secret_seeker",
            // V265: the Speed enchant on the hoe.
            "farming.enchants.WALK_SPEED",
            // V235: the Owner and Member ranks, and Owner's aura.
            "ranks.tiers.member", "ranks.tiers.owner",
            "auras.by-rank.looks.owner", "auras.rank-scale.owner",
            "discord.roles.owner.rank",
            // V226: three eggs, and the Prestige pets open at.
            "pets.eggs", "pets.min-prestige",
            // V227: Supernova wears its best tag on its own.
            "ranks.tiers.supernova.auto-tag",
            // V229: the Secret Realm.
            "secret-realm",
            // V200: how far a ground piece clears the block under it.
            "auras.ground-lift",
            // V202: the floor under the crate reel's step time.
            "crates.fastest-gap-ticks",
            // V203: how much room the reveal keeps clear of the eyes.
            "roll-item.comet.face-clear",
            // V205: the drop name's length where it shares a line, and the
            // weight and the mark each rarity wears on its name.
            "tag.max-name-length",
            "rarities.EPIC.symbol-char", "rarities.EPIC.bold",
            "rarities.LEGENDARY.symbol-char", "rarities.LEGENDARY.bold",
            "rarities.LEGENDARY.italic",
            "rarities.MYTHICAL.symbol-char", "rarities.MYTHICAL.bold", "rarities.MYTHICAL.italic",
            "rarities.DIVINE.symbol-char", "rarities.DIVINE.italic", "rarities.DIVINE.underline",
            "discord", "holograms",
            "holograms.panels.armor", "holograms.panels.starforge", "holograms.panels.potion",
            "holograms.panels.convert", "holograms.panels.pass", "holograms.panels.store",
            "holograms.panels.novacore", "holograms.panels.perks", "holograms.panels.index",
            "holograms.panels.farmtree", "holograms.panels.daily", "holograms.panels.leaderboards",
            "holograms.panels.stash", "holograms.panels.welcome",
            // V132: a Vote and a Nebula crate, with their keys.
            "crates.types.vote", "crates.types.nebula", "consumables.vote_key", "consumables.nebula_key",
            // V135: Credits on some milestone tiers.
            "milestones.credit-rewards",
            // V158: Credits on the free pass track.
            "pass.credit-rewards",
            // V163: game textures on the sidebar.
            "scoreboard.icons", "world-time",
            // V184: how far each rank grows the aura it wears.
            "auras.rank-scale",
            // V190: the join and quit lines.
            "join",
            // V188: titles and name colours.
            "cosmetics",
            // V187: the one letter rank badge in tab and chat.
            "ranks.tiers.linked.letter", "ranks.tiers.comet.letter",
            "ranks.tiers.nova.letter", "ranks.tiers.supernova.letter",
            // V186: the tab list header and footer.
            "tab",
            // V186: a 100x roll, for the milestones that pay one.
            "consumables.roll_100x",
            // V185: the two line pitch under each rank's name in /ranks.
            "ranks.tiers.linked.blurb", "ranks.tiers.comet.blurb",
            "ranks.tiers.nova.blurb", "ranks.tiers.supernova.blurb",
            // V137: ranks.
            "ranks",
            // V138: what one Credit Finder proc pays.
            "farming.procs.credit-finder-amount",
            // V140: the boss event.
            "boss",
            // V142: pets in the aura slots.
            "pets",
            // V143: the Discord cards, and V145 the bot. Dotted paths,
            // because every server already has a discord section.
            "discord.cards", "discord.bot",
            // V295: the changelog card for 3 October.
            "discord.cards.changelog",
            // V302: the second changelog card.
            "discord.cards.changelog2",
            // V304: index completion by rarity.
            "index.completion.by-rarity",
            // V148: the podium's own text size.
            "holograms.podium-text-scale",
            // V351: a full set of one armour tier is worth half again.
            "armor.set-bonus",
            // V336: the realm panel hides the shared "Click Here" line.
            "holograms.panels.realm.click",
            // V348: the per rarity luck exponents. New keys, so a Patch has
            // nothing to match on, and without them a live config runs the
            // code defaults, which are these same numbers.
            "rarities.EPIC.luck-exponent", "rarities.LEGENDARY.luck-exponent",
            "rarities.MYTHICAL.luck-exponent", "rarities.DIVINE.luck-exponent",
            "rarities.ASTRAL.luck-exponent",
            // V343: the second rarity every armour tier now asks for.
            "armor.tiers.LEATHER.costs.UNCOMMON", "armor.tiers.CHAINMAIL.costs.RARE",
            "armor.tiers.IRON.costs.EPIC", "armor.tiers.GOLD.costs.LEGENDARY",
            "armor.tiers.DIAMOND.costs.MYTHICAL", "armor.tiers.NETHERITE.costs.DIVINE",
            // V341: five more secrets, Leon's call ("also add more"). One
            // dotted path each, because every server already has a
            // secret-realm.secrets section and a whole-section copy would
            // never fire. A server that has retuned one of the eight it
            // already had keeps it.
            "secret-realm.secrets.tidelocked_twin", "secret-realm.secrets.ashen_orbit",
            "secret-realm.secrets.glass_horizon", "secret-realm.secrets.last_light",
            "secret-realm.secrets.unnamed_sky",
            // V144: the Boss Box and the item that opens it.
            "crates.types.boss", "consumables.boss_box",
            // V152: pets grow with dust. Dotted paths, because every server
            // already has a pets section from V142 and a whole-section copy
            // would never fire.
            "pets.base-slots", "pets.dust", "pets.upgrades",
            // V152: a boss that turns up on its own, and the item that
            // forces one for the whole server.
            "boss.natural", "consumables.boss_summoner",
            // V153: free 2x Luck when the server fills up, and the player
            // count the farming payout now needs.
            "boost.crowd", "leaderboard.farming.min-players");

    private record Patch(String id, String path, Object oldDefault, Object newDefault) {
    }

    /** An amount out of a reward map, whatever number type YAML gave it. */
    private static int toInt(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static final List<Patch> PATCHES = List.of(
            // V352: Luck has to keep mattering. Epic, Legendary and
            // Mythical climb hard with it now; Divine and Astral stay
            // pinned to 1 in 1,000 and 1 in 20,000 at three billion
            // percent, which is why Divine's exponent sits below
            // Mythical's.
            new Patch("luck-exp-legendary-v352", "rarities.LEGENDARY.luck-exponent", 1.04, 1.15),
            new Patch("luck-exp-mythical-v352", "rarities.MYTHICAL.luck-exponent", 1.08, 1.3),
            new Patch("luck-exp-divine-v352", "rarities.DIVINE.luck-exponent", 1.12, 1.16),
            new Patch("luck-exp-astral-v352", "rarities.ASTRAL.luck-exponent", 1.19, 1.24),
            // V351, from the end game feedback: Cosmic Dust at a rate that
            // reaches a pet (eight hours of rolling bought a thirtieth of
            // one Divine), ten more Nova Core tiers for the people sitting
            // on thousands of Cores, and a prestige ladder that stops
            // doubling every four levels (level 43 wanted 118,000 rolls).
            new Patch("dust-find-v351", "pets.dust.cosmic-max", 2, 10),
            new Patch("egg-cost-v351", "pets.eggs.tiers.supernova.cost", 12000, 6000),
            new Patch("egg-divine-v351", "pets.eggs.tiers.supernova.divine-chance", 0.01, 0.03),
            new Patch("nova-tiers-v351", "novacore.max-tier", 20, 30),
            new Patch("prestige-growth-v351", "prestige.level-cost-growth", 1.17, 1.13),
            // V350, Leon's numbers at the top: at three billion percent
            // Luck a Divine is one roll in a thousand and an Astral one in
            // twenty thousand. V349 left an Astral every 2,800 rolls,
            // which at 800 Speed is a few minutes of rolling.
            new Patch("luck-exp-legendary-v350", "rarities.LEGENDARY.luck-exponent", 1.08, 1.04),
            new Patch("luck-exp-mythical-v350", "rarities.MYTHICAL.luck-exponent", 1.16, 1.08),
            new Patch("luck-exp-divine-v350", "rarities.DIVINE.luck-exponent", 1.24, 1.12),
            new Patch("luck-exp-astral-v350", "rarities.ASTRAL.luck-exponent", 1.32, 1.19),
            // V349: the luck exponents, one step smaller. V348 shipped a
            // step of 0.15, which raised the floor nicely and brought
            // Astral in with it: a player on three billion percent Luck
            // saw one every fourteenth roll. At 0.08 that is one in 2,800.
            new Patch("luck-exp-legendary-v349", "rarities.LEGENDARY.luck-exponent", 1.15, 1.08),
            new Patch("luck-exp-mythical-v349", "rarities.MYTHICAL.luck-exponent", 1.3, 1.16),
            new Patch("luck-exp-divine-v349", "rarities.DIVINE.luck-exponent", 1.45, 1.24),
            new Patch("luck-exp-astral-v349", "rarities.ASTRAL.luck-exponent", 1.6, 1.32),
            // V347: the progression pass. A player went from nothing to a
            // maxed index in a day, and the feedback Leon brought named
            // why: Money buys the whole skill tree, the Money ladder was a
            // 52x stack on top of a 10x base rate, and the farm tree was
            // the cheapest power in the game.
            //
            // Money income drops to about 29% of what it was (this, plus
            // the Money ladder cut to a 30x stack, which rides the
            // structural skilltree section). The price knobs raise the
            // skill tree to 2.5x with its levels 1.35 times as steep and
            // the farm tree to 4x with levels 1.6 times as steep, both
            // with the first three levels of every node left cheap so the
            // first hour still moves. Together that is roughly seven to
            // eight times longer to the same place.
            new Patch("money-rate-v347", "economy.money-per-odds-multiplier", 10.0, 5.0),
            new Patch("skill-price-mult-v347", "economy.skill-prices.skilltree.multiplier", 1.0, 2.5),
            new Patch("skill-price-early-v347", "economy.skill-prices.skilltree.early-levels", 0, 3),
            new Patch("skill-price-eg-v347", "economy.skill-prices.skilltree.early-growth", 1.15, 1.1),
            new Patch("skill-price-scale-v347", "economy.skill-prices.skilltree.growth-scale", 1.0, 1.35),
            new Patch("farm-price-mult-v347", "economy.skill-prices.farmtree.multiplier", 1.0, 4.0),
            new Patch("farm-price-early-v347", "economy.skill-prices.farmtree.early-levels", 0, 3),
            new Patch("farm-price-eg-v347", "economy.skill-prices.farmtree.early-growth", 1.15, 1.1),
            new Patch("farm-price-scale-v347", "economy.skill-prices.farmtree.growth-scale", 1.0, 1.6),
            // Prestige, only a little, which is what Leon asked for.
            new Patch("prestige-rolls-v347", "prestige.rolls-per-level", 50, 70),
            new Patch("prestige-growth-v347", "prestige.level-cost-growth", 1.15, 1.17),
            // Armour was "just a filler" in that feedback, and it was: a
            // full Netherite set was +240% Luck against a tree handing out
            // thousands. A set is a branch of the tree now, and V343 made
            // it cost the rarest drops in the game.
            new Patch("armor-leather-luck-v347", "armor.tiers.LEATHER.luck-bonus", 0.05, 0.15),
            new Patch("armor-leather-speed-v347", "armor.tiers.LEATHER.speed-bonus", 0.05, 0.08),
            new Patch("armor-chain-luck-v347", "armor.tiers.CHAINMAIL.luck-bonus", 0.1, 0.35),
            new Patch("armor-chain-speed-v347", "armor.tiers.CHAINMAIL.speed-bonus", 0.08, 0.12),
            new Patch("armor-iron-luck-v347", "armor.tiers.IRON.luck-bonus", 0.15, 0.7),
            new Patch("armor-iron-speed-v347", "armor.tiers.IRON.speed-bonus", 0.12, 0.18),
            new Patch("armor-gold-luck-v347", "armor.tiers.GOLD.luck-bonus", 0.25, 1.2),
            new Patch("armor-gold-speed-v347", "armor.tiers.GOLD.speed-bonus", 0.16, 0.25),
            new Patch("armor-diamond-luck-v347", "armor.tiers.DIAMOND.luck-bonus", 0.4, 2.0),
            new Patch("armor-diamond-speed-v347", "armor.tiers.DIAMOND.speed-bonus", 0.2, 0.35),
            new Patch("armor-neth-luck-v347", "armor.tiers.NETHERITE.luck-bonus", 0.6, 3.0),
            new Patch("armor-neth-speed-v347", "armor.tiers.NETHERITE.speed-bonus", 0.25, 0.5),
            // V345: the shortest drop tooltip there is, Leon's call on the
            // Astral screenshot. Tag Luck is off every style as well, so
            // compact is now one line: rarity, odds and the shiny mark.
            new Patch("lore-style-compact-v345", "roll-item.lore-style", "stats", "compact"),
            new Patch("lore-style-compact-card-v345", "roll-item.lore-style", "card", "compact"),
            // V343: the Secret Realm has no prestige wall, Leon's call.
            new Patch("realm-no-prestige-v343", "secret-realm.min-prestige", 10, 0),
            // V343: every armour tier asks for two rarities and every piece
            // of a tier costs the same, Leon's call. These are the amounts
            // that moved; the rarity each tier gained is a new path, so it
            // is in ADDED_SECTIONS below.
            new Patch("armor-leather-v343", "armor.tiers.LEATHER.costs.COMMON", 40, 20),
            new Patch("armor-chain-v343", "armor.tiers.CHAINMAIL.costs.UNCOMMON", 16, 20),
            new Patch("armor-iron-v343", "armor.tiers.IRON.costs.RARE", 8, 10),
            new Patch("armor-gold-v343", "armor.tiers.GOLD.costs.EPIC", 2, 4),
            new Patch("armor-diamond-v343", "armor.tiers.DIAMOND.costs.LEGENDARY", 1, 3),
            new Patch("armor-netherite-v343", "armor.tiers.NETHERITE.costs.MYTHICAL", 1, 3),
            // V338, Leon's numbers for the realm: 20 minutes open, around
            // every three hours, and the drop digest every five minutes
            // rather than every two.
            new Patch("realm-open-20m-v338", "secret-realm.open-seconds", 900, 1200),
            new Patch("realm-gap-min-v338", "secret-realm.min-gap-minutes", 120, 165),
            new Patch("realm-gap-max-v338", "secret-realm.max-gap-minutes", 240, 195),
            new Patch("digest-5m-v338", "broadcast.digest-seconds", 120, 300),
            // V335: the shortest drop tooltip, Leon's call.
            new Patch("lore-style-stats-v335", "roll-item.lore-style", "card", "stats"),
            // V331: ten times the Nova Finder rate, Leon's call, and it
            // pays a Core rather than forcing a free forge.
            new Patch("nova-finder-v331", "farming.enchants.NOVA_FINDER.per-level", 0.0000005, 0.000005),
            new Patch("nova-finder-text-v331", "farming.enchants.NOVA_FINDER.description",
                    "A chance at a free Nova Core forge.",
                    "A chance to find a Nova Core while farming."),
            new Patch("nova-how-to-get-v331", "novacore.how-to-get",
                    "crates, /milestones (Playtime) and the /guide",
                    "crates, the Nova Finder enchant, /milestones and the /guide"),
            // V322: the crops farmed ladder, ten times what it was, Leon's
            // numbers. V320 put crop-unlock-at into ADDED_SECTIONS, so the
            // live config holds the old defaults and only a patch moves
            // them; each one is skipped if he has tuned that crop himself.
            new Patch("crop-unlock-carrots-v322", "farming.crop-unlock-at.CARROTS", 10000, 50000),
            new Patch("crop-unlock-potatoes-v322", "farming.crop-unlock-at.POTATOES", 25000, 100000),
            new Patch("crop-unlock-beetroots-v322", "farming.crop-unlock-at.BEETROOTS", 50000, 250000),
            new Patch("crop-unlock-netherwart-v322", "farming.crop-unlock-at.NETHER_WART", 100000, 500000),
            new Patch("crop-unlock-berries-v322", "farming.crop-unlock-at.SWEET_BERRIES", 250000, 1000000),
            // V322: a shift click opens every key, so the old ceiling of 25
            // would have quietly capped it on the live server.
            new Patch("crate-quick-open-all-v322", "crates.quick-open-max", 25, 500),
            // V298: Owner red only, prestige one level more each time, pass XP nerfed.
            // V303: reveals from 1 in 1,000 again, Leon's call.
            // V304: a full shiny tier is 3x.
            // V308: one rarity per set, from the old prices and from V306's.
            new Patch("armor-leather-v308a", "armor.tiers.LEATHER.costs",
                    Map.of("COMMON", 10, "UNCOMMON", 1), Map.of("COMMON", 40)),
            new Patch("armor-leather-v308b", "armor.tiers.LEATHER.costs",
                    Map.of("COMMON", 150, "UNCOMMON", 15), Map.of("COMMON", 40)),
            new Patch("armor-chainmail-v308a", "armor.tiers.CHAINMAIL.costs",
                    Map.of("COMMON", 25, "UNCOMMON", 5), Map.of("UNCOMMON", 16)),
            new Patch("armor-chainmail-v308b", "armor.tiers.CHAINMAIL.costs",
                    Map.of("COMMON", 400, "UNCOMMON", 50), Map.of("UNCOMMON", 16)),
            new Patch("armor-iron-v308a", "armor.tiers.IRON.costs",
                    Map.of("UNCOMMON", 25, "RARE", 5), Map.of("RARE", 8)),
            new Patch("armor-iron-v308b", "armor.tiers.IRON.costs",
                    Map.of("UNCOMMON", 250, "RARE", 25), Map.of("RARE", 8)),
            new Patch("armor-gold-v308a", "armor.tiers.GOLD.costs",
                    Map.of("UNCOMMON", 50, "RARE", 10), Map.of("EPIC", 2)),
            new Patch("armor-gold-v308b", "armor.tiers.GOLD.costs",
                    Map.of("UNCOMMON", 600, "RARE", 60), Map.of("EPIC", 2)),
            new Patch("armor-diamond-v308a", "armor.tiers.DIAMOND.costs",
                    Map.of("RARE", 25, "EPIC", 5), Map.of("LEGENDARY", 1)),
            new Patch("armor-diamond-v308b", "armor.tiers.DIAMOND.costs",
                    Map.of("RARE", 250, "EPIC", 10), Map.of("LEGENDARY", 1)),
            new Patch("armor-netherite-v308a", "armor.tiers.NETHERITE.costs",
                    Map.of("RARE", 50, "EPIC", 10), Map.of("MYTHICAL", 1)),
            new Patch("armor-netherite-v308b", "armor.tiers.NETHERITE.costs",
                    Map.of("EPIC", 30, "LEGENDARY", 5, "MYTHICAL", 1), Map.of("MYTHICAL", 1)),
            // V308: a boost's raised bar holds two hours, then back to 20.
            new Patch("crowd-reset-two-hours", "boost.crowd.reset-hours", 24, 2),
            // V306: armor far dearer, Netherite takes Mythicals, and one wipe.
            new Patch("armor-leather-v306", "armor.tiers.LEATHER.costs",
                    Map.of("COMMON", 10, "UNCOMMON", 1), Map.of("COMMON", 150, "UNCOMMON", 15)),
            new Patch("armor-chainmail-v306", "armor.tiers.CHAINMAIL.costs",
                    Map.of("COMMON", 25, "UNCOMMON", 5), Map.of("COMMON", 400, "UNCOMMON", 50)),
            new Patch("armor-iron-v306", "armor.tiers.IRON.costs",
                    Map.of("UNCOMMON", 25, "RARE", 5), Map.of("UNCOMMON", 250, "RARE", 25)),
            new Patch("armor-gold-v306", "armor.tiers.GOLD.costs",
                    Map.of("UNCOMMON", 50, "RARE", 10), Map.of("UNCOMMON", 600, "RARE", 60)),
            new Patch("armor-diamond-v306", "armor.tiers.DIAMOND.costs",
                    Map.of("RARE", 25, "EPIC", 5), Map.of("RARE", 250, "EPIC", 10)),
            new Patch("armor-netherite-v306", "armor.tiers.NETHERITE.costs",
                    Map.of("RARE", 50, "EPIC", 10), Map.of("EPIC", 30, "LEGENDARY", 5, "MYTHICAL", 1)),
            new Patch("armor-reset-1", "armor.reset-version", null, 1),
            new Patch("index-shiny-3x", "index.completion.per-shiny-rarity", 5.0, 3.0),
            new Patch("animate-one-in-thousand", "roll-item.animate-from-one-in", 100, 1000),
            new Patch("crowd-fifteen-minutes", "boost.crowd.duration-minutes", 30, 15),
            new Patch("crowd-cooldown-hour", "boost.crowd.cooldown-minutes", 150, 60),
            new Patch("shiny-firsts-ten", "first-ten.shiny-slots", 5, 10),
            new Patch("owner-red-only", "ranks.tiers.owner.colors",
                    List.of("#FF1744", "#FF3D00", "#FFC400"), List.of("#FF1744", "#B71C1C")),
            new Patch("walk-speed-farmtree", "farming.enchants.WALK_SPEED.always-unlocked", true, false),
            new Patch("prestige-plus-one-level", "prestige.levels-increment-per-prestige", 5, 1),
            new Patch("pass-xp-uncommon", "pass.xp.per-roll.UNCOMMON", 100, 20),
            new Patch("pass-xp-rare", "pass.xp.per-roll.RARE", 1000, 50),
            new Patch("pass-xp-epic", "pass.xp.per-roll.EPIC", 5000, 250),
            new Patch("pass-xp-legendary", "pass.xp.per-roll.LEGENDARY", 25000, 1000),
            new Patch("pass-xp-mythical", "pass.xp.per-roll.MYTHICAL", 100000, 2500),
            new Patch("pass-xp-divine", "pass.xp.per-roll.DIVINE", 500000, 5000),
            // V291: the Secret Realm for everyone, fifteen minutes, two hours apart.
            new Patch("realm-no-prestige", "secret-realm.min-prestige", 25, 0),
            new Patch("realm-fifteen-minutes", "secret-realm.open-seconds", 600, 900),
            new Patch("realm-two-hours", "secret-realm.min-gap-minutes", 90, 120),
            // V288: Owner's badge is the whole word.
            new Patch("owner-badge-word", "ranks.tiers.owner.letter", "O", "Owner"),
            // V285: Luck lifts the Nova forge a tenth as much.
            new Patch("nova-luck-weight-tenth", "novacore.luck-weight", 1.0, 0.1),
            // V284: the Nova Core is a Heart of the Sea again.
            new Patch("nova-core-heart-again", "consumables.nova_core.material",
                    "ENDER_PEARL", "HEART_OF_THE_SEA"),
            // V281: Coins nerfed at the sources, Leon's call. Coin Greed to +1,000%,
            // the farm tree Coins nodes a tenth each, Nuke pays 500 crops.
            new Patch("coin-greed-tenth", "farming.enchants.TOKEN_GREED.per-level", 0.01, 0.001),
            new Patch("nuke-crops-500", "farming.procs.nuke-crops", 10000, 500),
            new Patch("coins-node-tenth-tokens_1", "farmtree.nodes.tokens_1.value", 0.1, 0.01),
            new Patch("coins-node-tenth-tokens_2", "farmtree.nodes.tokens_2.value", 0.2, 0.02),
            new Patch("coins-node-tenth-tokens_3", "farmtree.nodes.tokens_3.value", 0.35, 0.035),
            new Patch("coins-node-tenth-tokens_4", "farmtree.nodes.tokens_4.value", 0.5, 0.05),
            new Patch("coins-node-tenth-tokens_5", "farmtree.nodes.tokens_5.value", 0.75, 0.075),
            new Patch("coins-node-tenth-tokens_6", "farmtree.nodes.tokens_6.value", 1, 0.1),
            new Patch("coins-node-tenth-tokens_7", "farmtree.nodes.tokens_7.value", 1.4, 0.14),
            new Patch("coins-node-tenth-tokens_8", "farmtree.nodes.tokens_8.value", 2, 0.2),
            new Patch("coins-node-tenth-tokens_9", "farmtree.nodes.tokens_9.value", 2.8, 0.28),
            // V280: the rarity Firsts hold ten again.
            new Patch("first-ten-slots-10", "first-ten.slots", 5, 10),
            // V277: Sweet Berries becomes Fern, and Potion Finder ten times rarer.
            new Patch("crop-fern-display", "farming.crop-types.SWEET_BERRIES.display", "Sweet Berries", "Fern"),
            new Patch("crop-fern-material", "farming.crop-types.SWEET_BERRIES.material", "SWEET_BERRY_BUSH", "FERN"),
            new Patch("node-fern-display", "farmtree.nodes.crop_berries.display", "Sweet Berries", "Fern"),
            new Patch("node-fern-icon", "farmtree.nodes.crop_berries.icon", "SWEET_BERRIES", "FERN"),
            new Patch("node-fern-yield", "farmtree.nodes.yield_berries.display", "Sweet Berry Yield", "Fern Yield"),
            new Patch("potion-finder-0.1", "farming.enchants.POTION_FINDER.per-level", 0.000005, 0.0000005),
            // V274: the procs not yet retuned, 1% for the early two, 0.1% for the late ones.
            new Patch("proc-v274-shard_greed", "farming.enchants.SHARD_GREED.per-level", 0.0008, 0.000005),
            new Patch("proc-v274-lightning", "farming.enchants.LIGHTNING.per-level", 0.0001, 0.000005),
            new Patch("proc-v274-nuke", "farming.enchants.NUKE.per-level", 0.0000005, 0.0000005),
            new Patch("proc-v274-coin_factory", "farming.enchants.COIN_FACTORY.per-level", 0.000015, 0.0000005),
            new Patch("proc-v274-gamba", "farming.enchants.GAMBA.per-level", 0.000008, 0.0000005),
            new Patch("proc-v274-prospector", "farming.enchants.PROSPECTOR.per-level", 0.00003, 0.0000005),
            new Patch("proc-v274-gem_rush", "farming.enchants.GEM_RUSH.per-level", 0.000005, 0.0000005),
            new Patch("proc-v274-gem_cascade", "farming.enchants.GEM_CASCADE.per-level", 0.000008, 0.0000005),
            new Patch("proc-v274-coin_storm", "farming.enchants.COIN_STORM.per-level", 0.000003, 0.0000005),
            new Patch("proc-v274-meteor", "farming.enchants.METEOR.per-level", 0.000004, 0.0000005),
            new Patch("proc-v274-black_hole", "farming.enchants.BLACK_HOLE.per-level", 0.0000015, 0.0000005),
            new Patch("proc-v274-supernova", "farming.enchants.SUPERNOVA.per-level", 0.0000003, 0.0000005),
            // V272: Key Finder 0.5% and Potion Finder 1% at the ceiling.
            new Patch("key-finder-0.5", "farming.enchants.KEY_FINDER.per-level", 0.00005, 0.0000025),
            new Patch("potion-finder-1", "farming.enchants.POTION_FINDER.per-level", 0.00004, 0.000005),
            // V270: Speed to +250%, Nova Finder to 0.1% at the ceiling.
            new Patch("walk-speed-250", "farming.enchants.WALK_SPEED.per-level", 0.0005, 0.00025),
            new Patch("nova-finder-0.1", "farming.enchants.NOVA_FINDER.per-level", 0.00001, 0.0000005),
            // V268: Momentum climbs ten times faster.
            new Patch("momentum-10x-faster", "farming.momentum.per-thousand-crops", 0.01, 0.1),
            // V267: Leon's numbers. Speed to +500%, TNT Blast 3x rarer and
            // smaller, Momentum to 5x, and no lp commands for the vault.
            new Patch("walk-speed-500", "farming.enchants.WALK_SPEED.per-level", 0.00005, 0.0005),
            new Patch("blast-chance-3x-lower", "farming.enchants.BLAST_HARVEST.per-level", 0.000004, 0.0000013),
            new Patch("blast-radius-4", "farming.blast.max-radius", 7, 4),
            new Patch("momentum-enchant-5x", "farming.enchants.MOMENTUM.per-level", 0.004, 0.0004),
            new Patch("momentum-cap-5x", "farming.momentum.per-level-cap", 0.05, 0.0004),
            new Patch("private-vault-no-lp", "unlock-commands.private-vault",
                    List.of("lp user {player} permission set playervaults.amount.1 true",
                            "lp user {player} permission set playervaults.use true"), List.of()),
            // V265: TNT Blast fires 50x less often, Leon's call.
            new Patch("blast-chance-50x-lower", "farming.enchants.BLAST_HARVEST.per-level", 0.0002, 0.000004),
            // V251: the tag showed twice, floating and in front of the name.
            new Patch("tag-no-nametag-prefix", "tag.manage-nametag", true, false),
            // V249: Member is what every starter wears now; mint, not grey.
            new Patch("rank-member-mint", "ranks.tiers.member.colors",
                    List.of("#B0BEC5"), List.of("#B9F6CA", "#69F0AE")),
            // V248: the footer pointed at discord.gg/spacerng, which is not
            // the invite, and an invite code cannot be written in small caps.
            new Patch("tab-footer-discord-command", "tab.footer",
                    List.of("", "&7ʏᴏᴜ'ʀᴇ ᴘʟᴀʏɪɴɢ ᴏɴ || &7ᴊᴏɪɴ ᴏᴜʀ ᴅɪѕᴄᴏʀᴅ ᴀᴛ",
                            "&d&lѕᴘᴀᴄᴇʀɴɢ.ᴍɪɴᴇʜᴜᴛ.ɢɢ || &9&lᴅɪѕᴄᴏʀᴅ.ɢɢ/ѕᴘᴀᴄᴇʀɴɢ", ""),
                    List.of("", "&7ʏᴏᴜ'ʀᴇ ᴘʟᴀʏɪɴɢ ᴏɴ || &7ᴊᴏɪɴ ᴏᴜʀ ᴅɪѕᴄᴏʀᴅ",
                            "&d&lѕᴘᴀᴄᴇʀɴɢ.ᴍɪɴᴇʜᴜᴛ.ɢɢ || &9&l/ᴅɪѕᴄᴏʀᴅ", "")),
            // V247: Linked wears a check mark in a really light blue.
            new Patch("rank-linked-check", "ranks.tiers.linked.letter", "L", "✔"),
            new Patch("rank-linked-light-blue", "ranks.tiers.linked.colors",
                    List.of("#5865F2", "#7289DA"), List.of("#CFEFFF", "#A9DDFF")),
            // V246: Leon's Tebex store, for the button in /buy.
            new Patch("store-url-tebex", "buy.store-url", "", "https://spacerng.tebex.store/"),
            // V245: Leon's numbers. Intermediate +50 Speed, and a finished
            // rarity 2x Luck, a finished shiny rarity 5x.
            new Patch("starforge-intermediate-speed-50", "starforge.tiers.INTERMEDIATE.speed-bonus", 5.0, 0.50),
            new Patch("index-completion-2x", "index.completion.per-rarity", 1.25, 2.0),
            new Patch("index-completion-shiny-5x", "index.completion.per-shiny-rarity", 2.0, 5.0),
            // V243: the tab in two columns, the layout Leon pointed at.
            new Patch("tab-header-columns", "tab.header",
                    List.of("", "{title}", "&8{online}&7/&8{max} online", ""),
                    List.of("", "{title}", "&7ʟᴀᴛᴇɴᴄʏ &b⌚ {ping} || &7ᴏɴʟɪɴᴇ ᴘʟᴀʏᴇʀѕ &a☺ &a{online}", "")),
            new Patch("tab-footer-columns", "tab.footer",
                    List.of("", "&7Rank &r{rank}   &8|&r   &7Luck &f{luck}   &8|&r   &7Rolls &f{rolls}",
                            "&8spacerng.minehut.gg", ""),
                    List.of("", "&7ʏᴏᴜ'ʀᴇ ᴘʟᴀʏɪɴɢ ᴏɴ || &7ᴊᴏɪɴ ᴏᴜʀ ᴅɪѕᴄᴏʀᴅ ᴀᴛ",
                            "&d&lѕᴘᴀᴄᴇʀɴɢ.ᴍɪɴᴇʜᴜᴛ.ɢɢ || &9&lᴅɪѕᴄᴏʀᴅ.ɢɢ/ѕᴘᴀᴄᴇʀɴɢ", "")),
            // V243: Owner's flat red becomes red into gold.
            new Patch("rank-owner-gradient", "ranks.tiers.owner.colors",
                    List.of("#FF3B3B"), List.of("#FF1744", "#FF3D00", "#FFC400")),
            // V237: Nova climbs above Comet instead of standing beside it.
            new Patch("rank-aura-nova-heartfall", "auras.by-rank.looks.nova", "eclipse", "heartfall"),
            // V235: Owner is red, Leon's call.
            new Patch("discord-owner-red", "discord.roles.owner.colors",
                    List.of("#FFE082", "#FF8F00"), List.of("#FF6B6B", "#D50000")),
            // V230: Leon's own channels, empty since V225.
            new Patch("discord-link-channel-leon", "discord.bot.link-channel", "", "1553697805690994749"),
            new Patch("discord-cards-channel-leon", "discord.bot.cards-channel", "", "1545800590280630272"),
            // V204: the ground pieces were still sinking at 0.30.
            new Patch("aura-ground-lift-45", "auras.ground-lift", 0.30, 0.45),
            // V203: the floating heads turned a full circle every four
            // seconds, which is a spin rather than a turn. Every eight now.
            new Patch("crate-head-slower", "holograms.crate-spin-degrees", 90, 45),
            new Patch("top-head-slower", "top-heads.spin-degrees-per-second", 90, 45),
            // V203: the question mark filled a third of the screen at 4.0.
            // 2.0 is about what a drop takes, which is what was asked for.
            new Patch("comet-head-scale-2", "roll-item.comet.head-scale", 4.0, 2.0),
            // V202: the crate reel was a smear for its first second, at
            // seventeen items a second. Fewer steps and a longer crawl,
            // alongside the new floor under the step time.
            new Patch("crate-reel-slower-steps", "crates.spin-steps", 32, 24),
            new Patch("crate-reel-slower-gap", "crates.slowest-gap-ticks", 7, 12),
            // V199: the reveal's two screen pieces go back to riding the
            // player. V198 defaulted them to being teleported, on the
            // theory that pinning was why they were invisible, and the
            // reel has been showing its own items through the same pinned
            // display all along. A server that already took the V198
            // section has "world" written into its file, so the default
            // alone would not reach it.
            new Patch("comet-screen-pinned", "roll-item.comet.screen.mode", "world", "pinned"),
            // V108: Common's label went from grey to white at Leon's request.
            new Patch("common-label-white", "rarities.COMMON.colors", List.of("&7"), List.of("&f")),
            // V118: tags wear the looks Leon picked, built from ground stars, sea lanterns and nether stars.
            new Patch("tag-aura-legendary", "auras.tag.LEGENDARY.concept", "celestial", "galaxy-grand"),
            new Patch("tag-aura-mythical", "auras.tag.MYTHICAL.concept", "cosmos", "nova-grand"),
            new Patch("tag-aura-divine", "auras.tag.DIVINE.concept", "seraph", "atom-grand"),
            // V121: Mythical wears a smaller singularity. Runs after the V118 patch above.
            new Patch("tag-aura-mythical-lite", "auras.tag.MYTHICAL.concept", "nova-grand", "singularity-lite"),
            // V126: Leon picked the card style for the Nova Core and other consumables.
            new Patch("consumable-style-card", "consumable-style", "classic", "card"),
            // V126: the tag odds styles were redrawn with marks on both sides.
            new Patch("tag-odds-dots", "tag.odds-style", "gradient", "dots"),
            // V126: a bigger crate head.
            new Patch("crate-head-bigger", "holograms.crate-head-scale", 2.0, 2.6),
            // V126: Luck now thins Commons out instead of leaving them at 94.5% of band rolls forever.
            new Patch("common-luck-factor-negative", "rarities.COMMON.luck-factor", 0.0, -0.25),
            // V132: Cosmic is a store crate, so Key Finder's rare find is a Nebula Key.
            new Patch("key-finder-rare-nebula", "farming.procs.key-finder-rare-reward", "cosmic_key", "nebula_key"),
            // V135: Leon raised the daily farming payout to 150, 100 and 50 Credits.
            new Patch("farming-payouts-150-100-50", "leaderboard.farming.credit-payouts",
                    List.of(150, 75, 25), List.of(150, 100, 50)),
            // V135: Leon picked the attribute card for the hoe.
            new Patch("hoe-style-attributes", "hoe-style", "classic", "attributes"),
            // V148: Leon wants the farming podium read from across the spawn.
            new Patch("podium-heads-bigger", "holograms.podium-head-scale", 1.8, 3.2),
            new Patch("podium-spacing-wider", "holograms.podium-spacing", 2.5, 4.5),
            // V191: five Server First spots per rarity rather than ten.
            new Patch("first-ten-five-slots", "first-ten.slots", 10, 5),
            // V190: the Nova Core is an ender pearl.
            new Patch("nova-core-ender-pearl", "consumables.nova_core.material",
                    "HEART_OF_THE_SEA", "ENDER_PEARL"),
            // V189: Leon wanted the Beta title in hacker green.
            new Patch("beta-title-terminal-green", "cosmetics.titles.beta.colors",
                    List.of("#A5F3FC", "#22D3EE"), List.of("#008F11", "#00FF41", "#39FF14")),
            // V186: Leon wanted the hoe's enchant proc worth more.
            new Patch("hoe-proc-share-higher", "farming.hoe-ladder.proc-share", 0.07, 0.18),
            // V186: the gold nugget sprite is tiny next to the others.
            new Patch("icon-coins-gold-ingot", "scoreboard.icons.coins",
                    "minecraft:items|minecraft:item/gold_nugget",
                    "minecraft:items|minecraft:item/gold_ingot"),
            // V186: Leon asked for +5 Speed on the third Starforge tier.
            new Patch("starforge-intermediate-speed-5", "starforge.tiers.INTERMEDIATE.speed-bonus", 0.05, 5.0),
            // V150: still too small in game, so bigger again.
            new Patch("podium-heads-bigger-2", "holograms.podium-head-scale", 3.2, 4.5),
            new Patch("podium-text-bigger-2", "holograms.podium-text-scale", 2.0, 3.2),
            new Patch("podium-spacing-wider-2", "holograms.podium-spacing", 4.5, 6.5),
            new Patch("cosmic-key-source-store", "crates.types.cosmic.key-source",
                    "Cosmic Keys are the rare find from Key Finder, about one key in twelve.",
                    "Cosmic Keys come from the store."),
            // V153: Leon wants the top rank around 7k Credits rather than
            // 4.5k, with the two under it spread to match.
            new Patch("rank-comet-1200", "ranks.tiers.comet.price-credits", 1000, 1200),
            new Patch("rank-nova-3200", "ranks.tiers.nova.price-credits", 2500, 3200),
            new Patch("rank-supernova-7000", "ranks.tiers.supernova.price-credits", 4500, 7000),
            // V156: the Linked tag wears Discord's current blurple. The old
            // second stop was grey, which washed the name out halfway.
            new Patch("rank-linked-blurple", "ranks.tiers.linked.colors",
                    List.of("#7289DA", "#B9BBBE"), List.of("#5865F2", "#7289DA")),
            // V157: the heads were still small in game, so the live value is
            // one of the older defaults. One patch per default it ever had,
            // each firing only on an exact match, so a hand-picked size stays.
            new Patch("podium-heads-6-from-1.3", "holograms.podium-head-scale", 1.3, 6.0),
            new Patch("podium-heads-6-from-1.8", "holograms.podium-head-scale", 1.8, 6.0),
            new Patch("podium-heads-6-from-3.2", "holograms.podium-head-scale", 3.2, 6.0),
            new Patch("podium-heads-6-from-4.5", "holograms.podium-head-scale", 4.5, 6.0),
            // V158: Luck multiplies the Nova Core chance in full, 100% Luck is 2x.
            new Patch("nova-luck-weight-full", "novacore.luck-weight", 0.15, 1.0),
            // V158: armor paid its Luck per piece, so a full Netherite set
            // was +1,000%. About a quarter of that now.
            new Patch("armor-luck-leather", "armor.tiers.LEATHER.luck-bonus", 0.25, 0.05),
            new Patch("armor-luck-chainmail", "armor.tiers.CHAINMAIL.luck-bonus", 0.45, 0.10),
            new Patch("armor-luck-iron", "armor.tiers.IRON.luck-bonus", 0.70, 0.15),
            new Patch("armor-luck-gold", "armor.tiers.GOLD.luck-bonus", 1.05, 0.25),
            new Patch("armor-luck-diamond", "armor.tiers.DIAMOND.luck-bonus", 1.60, 0.40),
            new Patch("armor-luck-netherite", "armor.tiers.NETHERITE.luck-bonus", 2.50, 0.60),
            // V158: Index Luck was too strong. Less per drop, and finishing a
            // rarity is 1.25x rather than doubling everything.
            new Patch("index-luck-per-drop-lower", "index.luck-per-discovery", 0.01, 0.004),
            new Patch("index-completion-lower", "index.completion.per-rarity", 2.0, 1.25),
            new Patch("index-completion-shiny-lower", "index.completion.per-shiny-rarity", 5.0, 2.0),
            // V158: the Index milestones start at 80 (Leon's tiers), and
            // milestone Credits are about 1.6x what they were.
            new Patch("milestone-index-tiers-80", "milestones.tracks.rarity.tiers",
                    List.of(Map.of("at", 25, "money", 5000),
                            Map.of("at", 50, "consumable", "roll_10x"),
                            Map.of("at", 70, "consumable", "free_skill"),
                            Map.of("at", 85, "consumable", "luck_potion"),
                            Map.of("at", 95, "consumable", "roll_10x", "consumable-amount", 2),
                            Map.of("at", 105, "consumable", "speed_potion"),
                            Map.of("at", 112, "consumable", "roll_10x", "consumable-amount", 3),
                            Map.of("at", 118, "consumable", "luck_potion", "consumable-amount", 3),
                            Map.of("at", 126, "consumable", "roll_10x", "consumable-amount", 5),
                            Map.of("at", 130, "consumable", "luck_250"),
                            Map.of("at", 140, "consumable", "roll_10x", "consumable-amount", 10),
                            Map.of("at", 144, "consumable", "luck_potion", "consumable-amount", 10)),
                    List.of(Map.of("at", 80, "consumable", "roll_10x", "consumable-amount", 2),
                            Map.of("at", 100, "consumable", "free_skill"),
                            Map.of("at", 120, "consumable", "roll_10x", "consumable-amount", 5),
                            Map.of("at", 130, "consumable", "luck_250"),
                            Map.of("at", 140, "consumable", "roll_10x", "consumable-amount", 10),
                            Map.of("at", 144, "consumable", "luck_potion", "consumable-amount", 10))),
            new Patch("milestone-credits-prestige-up", "milestones.credit-rewards.prestige",
                    Map.of("10", 25, "25", 50, "50", 100, "100", 250),
                    Map.of("10", 40, "25", 75, "50", 150, "100", 350)),
            new Patch("milestone-credits-index-up", "milestones.credit-rewards.rarity",
                    Map.of("70", 15, "105", 30, "130", 60, "144", 150),
                    Map.of("80", 20, "100", 40, "120", 60, "130", 80, "140", 120, "144", 200)),
            new Patch("milestone-credits-farming-up", "milestones.credit-rewards.farming",
                    Map.of("10000", 25, "100000", 50, "1000000", 150),
                    Map.of("10000", 40, "100000", 75, "1000000", 200)),
            // V158: Leon's rank multipliers, on Luck, Speed and Money alike.
            new Patch("rank-comet-1.1", "ranks.tiers.comet.multiplier", 1.2, 1.1),
            new Patch("rank-nova-1.25", "ranks.tiers.nova.multiplier", 1.5, 1.25),
            new Patch("rank-supernova-1.5", "ranks.tiers.supernova.multiplier", 2.0, 1.5),
            // V159: tool upgrades lift enchant procs a bit more.
            new Patch("hoe-proc-share-up", "farming.hoe-ladder.proc-share", 0.04, 0.07),
            // V165: the effect icons showed as a missing glyph; they live in the gui atlas.
            new Patch("icon-luck-gui-atlas", "scoreboard.icons.luck",
                    "minecraft:mob_effects|minecraft:luck", "minecraft:gui|minecraft:mob_effect/luck"),
            new Patch("icon-speed-gui-atlas", "scoreboard.icons.speed",
                    "minecraft:mob_effects|minecraft:speed", "minecraft:gui|minecraft:mob_effect/speed"),
            // V167: Speed is how fast a roll goes, so a clock rather than the Speed effect.
            new Patch("icon-speed-clock-from-effects", "scoreboard.icons.speed",
                    "minecraft:mob_effects|minecraft:speed", "minecraft:items|minecraft:item/clock_00"),
            new Patch("icon-speed-clock", "scoreboard.icons.speed",
                    "minecraft:gui|minecraft:mob_effect/speed", "minecraft:items|minecraft:item/clock_00"),
            // V169: the farm panel twice the size and without "Click Here".
            // Both keys are new, so the old value is "not there".
            new Patch("farm-panel-scale", "holograms.panels.farm.scale", null, 2.0),
            new Patch("farm-panel-no-click", "holograms.panels.farm.click", null, ""),
            // V170: the welcome panel has nothing to click either.
            new Patch("welcome-panel-no-click", "holograms.panels.welcome.click", null, ""),
            // V170: the Nova Core explained, on the item and on its panel.
            new Patch("nova-core-description", "consumables.nova_core.description",
                    "Spend it in /novacore on a shot at the next tier.",
                    "Forge it in /novacore for a shot at the next tier.\nEvery tier multiplies your Luck, Money and Coins.\n"
                            + "Miss and you fall back to your last checkpoint.\nThe more Luck you have, the better your odds."),
            new Patch("nova-core-panel", "holograms.panels.novacore.lines",
                    List.of("<white>Forge <#F48FB1>Nova Cores</#F48FB1> and climb",
                            "<white>the tier ladder for huge bonuses"),
                    List.of("<white>Forge <#F48FB1>Nova Cores</#F48FB1> to climb 20 tiers",
                            "<white>Every tier multiplies <#81C784>Luck</#81C784>, <#81C784>Money</#81C784> and <#FFD54F>Coins</#FFD54F>",
                            "<white>More Luck means better odds per forge")),
            // V174: every rarity got a look of its own, painted in its own
            // colour instead of star glyphs round a sea lantern. These run
            // after the V118 and V121 patches above, so a server still on a
            // pre-V118 value is carried the whole way.
            new Patch("tag-aura-epic-sigil", "auras.tag.EPIC.concept", "runes", "sigil"),
            new Patch("tag-aura-legendary-ember", "auras.tag.LEGENDARY.concept", "galaxy-grand", "ember"),
            new Patch("tag-aura-legendary-embers", "auras.tag.LEGENDARY.accent", "sparkle", "embers"),
            new Patch("tag-aura-mythical-eclipse", "auras.tag.MYTHICAL.concept", "singularity-lite", "eclipse"),
            new Patch("tag-aura-divine-ascend", "auras.tag.DIVINE.concept", "atom-grand", "ascend"),
            // V175: the shiny looks repainted the same way, and the look a
            // heavy one falls back to when two of them stand together.
            new Patch("shiny-aura-epic-prism", "auras.shiny.EPIC.concept", "helix", "prism"),
            new Patch("shiny-aura-legendary-pyre", "auras.shiny.LEGENDARY.concept", "nova-grand", "pyre"),
            new Patch("shiny-aura-legendary-embers", "auras.shiny.LEGENDARY.accent", "sparkle", "embers"),
            new Patch("shiny-aura-mythical-rift", "auras.shiny.MYTHICAL.concept", "titan", "rift"),
            new Patch("shiny-aura-divine-empyrean", "auras.shiny.DIVINE.concept", "supernova", "empyrean"),
            new Patch("heavy-fallback-ascend", "auras.heavy.fallback", "galaxy-grand", "ascend"),
            // V210: the flicker is gone and every rarity has its own mark.
            new Patch("symbol-epic-diamond", "rarities.EPIC.symbol-char", "✦", "◆"),
            new Patch("symbol-mythical-ornate", "rarities.MYTHICAL.symbol-char", "✧", "❖"),
            // V210: the odds counter sits in the middle of the screen.
            new Patch("counter-centred", "roll-item.comet.screen.counter-up", 0.35, 0.0),
            // V212: centred sat behind the crosshair, so a little above it.
            new Patch("counter-above-crosshair", "roll-item.comet.screen.counter-up", 0.0, 0.28),
            // V215: a pet costs a hundred Cosmic Dust, Leon's call.
            new Patch("pet-make-cost-100", "pets.upgrades.make-cost", 10, 100),
            // V312: Tag Luck moves off the equipped drop and into
            // /secretindex, and the realm asks for Prestige 10. Both sit
            // outside a structural section, so the live config keeps the
            // old value unless it is patched across one path at a time.
            new Patch("secret-luck-on", "secret-realm.secret-luck", false, true),
            new Patch("secret-realm-p10", "secret-realm.min-prestige", 0, 10),
            // V313: the egg ladder and Gems for every pet upgrade. None of
            // pets lives in a structural section, so each tuned number has
            // to come across on its own or the live server keeps the old
            // one and the new divine-chance keys do nothing.
            new Patch("pet-min-prestige-0", "pets.min-prestige", 10, 0),
            new Patch("pet-egg1-p0", "pets.eggs.tiers.stardust.min-prestige", 10, 0),
            new Patch("pet-egg1-ceiling", "pets.eggs.tiers.stardust.max-rarity", "RARE", "MYTHICAL"),
            new Patch("pet-egg2-cost", "pets.eggs.tiers.nebula.cost", 1000, 1500),
            new Patch("pet-egg2-boost", "pets.eggs.tiers.nebula.boost", 20, 25),
            new Patch("pet-egg2-p10", "pets.eggs.tiers.nebula.min-prestige", 20, 10),
            new Patch("pet-egg2-ceiling", "pets.eggs.tiers.nebula.max-rarity", "LEGENDARY", "MYTHICAL"),
            new Patch("pet-egg3-cost", "pets.eggs.tiers.supernova.cost", 10000, 12000),
            new Patch("pet-egg3-p25", "pets.eggs.tiers.supernova.min-prestige", 20, 25),
            new Patch("pet-egg3-ceiling", "pets.eggs.tiers.supernova.max-rarity", "DIVINE", "MYTHICAL"),
            new Patch("pet-rarity-gems", "pets.upgrades.rarity-base-cost", 8, 1000),
            new Patch("pet-rarity-growth", "pets.upgrades.rarity-cost-growth", 1.35, 2.0),
            new Patch("pet-tier-gems", "pets.upgrades.tier-base-cost", 25, 1500),
            new Patch("pet-tier-growth", "pets.upgrades.tier-cost-growth", 1.40, 2.0),
            new Patch("pet-shiny-gems", "pets.upgrades.shiny-cost", 50, 100000),
            // V319: Astral joins the two lists that name the top tiers.
            // Both are lists of strings, so the whole list is the value.
            new Patch("astral-discord", "discord.announce.drop-rarities",
                    List.of("MYTHICAL", "DIVINE"), List.of("MYTHICAL", "DIVINE", "ASTRAL")),
            new Patch("astral-firsts", "first-ten.rarities",
                    List.of("LEGENDARY", "MYTHICAL", "DIVINE"),
                    List.of("LEGENDARY", "MYTHICAL", "DIVINE", "ASTRAL")));

    // V159: every hoe enchant runs to level 10,000, except Credit Finder,
    // which stays at 1,000 because it pays Credits.
    private static final List<Patch> ENCHANT_PATCHES = java.util.stream.Stream.of(
                    "TOKEN_GREED", "MOMENTUM", "SHARD_GREED", "KEY_FINDER", "BLAST_HARVEST",
                    "POTION_FINDER", "LIGHTNING", "NOVA_FINDER", "NUKE", "COIN_FACTORY", "GAMBA",
                    "PROSPECTOR", "GEM_RUSH",
                    "GEM_CASCADE", "COIN_STORM", "METEOR", "BLACK_HOLE", "SUPERNOVA")
            .map(id -> new Patch("enchant-10k-" + id, "farming.enchants." + id + ".max-level", 1000, 10000))
            .toList();

    /**
     * V190: and the ceiling a player can actually reach.
     *
     * max-level says where an enchant can ever finish; base-cap says where
     * it starts, and maxLevelFor returns the smaller of the two. Every
     * enchant shipped with base-cap 100, so "10,000 levels" was a number
     * in a config file that nobody could see in game: the rack read out of
     * 100 until both Enchant Mastery nodes were bought, and those cost
     * billions. The price curve is the balance now, which is what it was
     * always for.
     */
    private static final List<Patch> ENCHANT_CAP_PATCHES = java.util.stream.Stream.of(
                    "TOKEN_GREED", "MOMENTUM", "SHARD_GREED", "KEY_FINDER", "BLAST_HARVEST",
                    "POTION_FINDER", "LIGHTNING", "NOVA_FINDER", "NUKE", "COIN_FACTORY", "GAMBA",
                    "PROSPECTOR", "GEM_RUSH", "GEM_CASCADE", "COIN_STORM", "METEOR",
                    "BLACK_HOLE", "SUPERNOVA")
            .map(id -> new Patch("enchant-cap-10k-" + id, "farming.enchants." + id + ".base-cap", 100, 10000))
            .toList();

    /**
     * V328: and the ceiling mastery climbs TO.
     *
     * V190 raised base-cap to 10,000 to make the levels reachable, and
     * max-level was already 10,000, so maxLevelFor came back as
     * min(10000, 10000 + mastery) and the two Enchant Mastery nodes have
     * bought nothing at all since. Leon reported exactly that. max-level
     * is 20,000 now: base-cap is still where an enchant starts, and the
     * 9,900 levels the two mastery nodes give are the distance between
     * them. Nothing anybody has is touched, because base-cap did not
     * move.
     */
    private static final List<Patch> ENCHANT_MAX_PATCHES = java.util.stream.Stream.of(
                    "TOKEN_GREED", "MOMENTUM", "SHARD_GREED", "KEY_FINDER", "BLAST_HARVEST",
                    "POTION_FINDER", "LIGHTNING", "NOVA_FINDER", "NUKE", "COIN_FACTORY", "GAMBA",
                    "PROSPECTOR", "GEM_RUSH", "GEM_CASCADE", "COIN_STORM", "METEOR",
                    "BLACK_HOLE", "SUPERNOVA", "WALK_SPEED")
            .map(id -> new Patch("enchant-max-20k-" + id, "farming.enchants." + id + ".max-level", 10000, 20000))
            .toList();

    /**
     * Paths deleted outright, once, and remembered like a patch.
     *
     * farming is not a structural section, so an enchant taken out of the
     * jar is still sitting in a live config and still loads. This is the
     * only way one actually leaves.
     */
    private static final List<String[]> REMOVALS = List.of(
            // coming-soon needs nothing here: the key is simply absent from
            // a live config, and a missing key falls through to the jar's
            // default, which is where it is set.
            // V190: Leon took Golden Touch and Harvest Echo out.
            new String[]{"enchant-gone-golden-touch", "farming.enchants.GOLDEN_TOUCH"},
            new String[]{"enchant-gone-harvest-echo", "farming.enchants.HARVEST_ECHO"},
            // V326: Leon took Alchemy out. The farmtree node goes with the
            // structural rewrite; the enchant itself has to be deleted
            // here or it stays in the live config and keeps loading.
            new String[]{"enchant-gone-alchemy", "farming.enchants.ALCHEMY"},
            new String[]{"enchant-gone-alchemy-share", "farming.procs.alchemy-share"},
            new String[]{"enchant-gone-alchemy-rate", "farming.procs.alchemy-rate"});

    /** Like a Patch, for one field of the entry with a given id inside a list of maps. */
    private record EntryPatch(String id, String list, String entryId, String field, Object oldDefault,
                              Object newDefault) {
    }

    private static final List<EntryPatch> ENTRY_PATCHES = List.of(
            // V344: the six crop unlock nodes left the farm tree, crops
            // have opened on crops farmed since V323, so the guide step
            // that asked for crop_wheat has to ask for a node that exists.
            new EntryPatch("guide-farm-skills-v344", "guide.quests", "farm_skills", "target",
                    "crop_wheat", "yield_wheat"),
            // V114 moved Index Luck, Armor and Farming earlier in the skill tree.
            // V257: say where levelling up happens.
            new EntryPatch("guide-level-display-prestige", "guide.quests", "reach_level", "display",
                    "Reach level 5", "Reach level 5 in /prestige"),
            new EntryPatch("guide-level-hint-prestige", "guide.quests", "reach_level", "hint",
                    "Rolling levels you up. Spend levels in /prestige.",
                    "Rolling fills your level bar. Level up in /prestige."),
            new EntryPatch("guide-hint-index-luck", "guide.quests", "index_luck", "hint",
                    "In /skilltree, right of Luck. It's what lets you equip a tag.",
                    "In /skilltree, right above Luck I. It's what lets you equip a tag."),
            new EntryPatch("guide-hint-armor", "guide.quests", "armor_unlock", "hint",
                    "Buy Armor Unlocked in /skilltree, above Farming.",
                    "Buy Armor in /skilltree, right after Money I."),
            new EntryPatch("guide-hint-farming", "guide.quests", "farming_unlock", "hint",
                    "Buy Farming Unlocked in /skilltree to get the Farmer's Hoe.",
                    "Buy Farming in /skilltree, right after Armor, to get the Farmer's Hoe."),
            // V121: the tag gate is called Tag Luck, and Index Luck is the per entry skill.
            new EntryPatch("guide-display-tag-luck", "guide.quests", "index_luck", "display",
                    "Unlock Index Luck", "Unlock Tag Luck"),
            // V126: Index Luck I moved in front of Tag Luck.
            new EntryPatch("guide-hint-tag-luck-after-index", "guide.quests", "index_luck", "hint",
                    "In /skilltree, right above Luck I. It's what lets you equip a tag.",
                    "In /skilltree, right after Index Luck I. It's what lets you equip a tag."));

    /**
     * Replaces one exact line anywhere inside a (nested) list, like a
     * rotating tip. A null new text removes the line.
     */
    private record TextPatch(String id, String path, String oldText, String newText) {
    }

    /**
     * Replaces a whole list of lines at once, while it still reads exactly
     * like the old default (V336). TextPatch swaps one line for one line
     * and cannot add or drop one, which is what a rewritten hologram
     * panel needs.
     */
    private record ListPatch(String id, String path, List<String> oldLines, List<String> newLines) {
    }

    /**
     * Drops one entry out of a list of maps by its id (V343), which is how
     * a guide step for something that no longer exists is taken off a live
     * config. REMOVALS deletes a path and a list entry has none.
     */
    private record EntryRemoval(String id, String list, String entryId) {
    }

    private static final List<EntryRemoval> ENTRY_REMOVALS = List.of(
            // V343: the Tag Luck node is gone from the tree, so the guide
            // step asking for it could never be finished, and an unfinished
            // step holds up every step behind it.
            new EntryRemoval("guide-drop-index-luck-v343", "guide.quests", "index_luck"));

    private static final List<ListPatch> LIST_PATCHES = List.of(
            // V337: Leon on the holograms, "dont make the lines so long".
            // Every one of these is a line floating in the world, read at
            // a glance while walking past, so they are cut to about the
            // length of the other panels.
            new ListPatch("realm-panel-v337", "holograms.panels.realm.lines",
                    List.of("<white>Roll in here for a chance at a <#B0BEC5>Secret</#B0BEC5>",
                            "<white>Every secret found multiplies your <#80DEEA>index Luck</#80DEEA>",
                            "<gray>Luck and Speed do nothing in here, the odds are flat",
                            "<white>Opens on its own, /secretrealm takes you in"),
                    List.of("<white>Roll here for a <#B0BEC5>Secret</#B0BEC5>",
                            "<white>Secrets multiply your Luck",
                            "<gray>Luck and Speed do nothing here",
                            "<white>/secretrealm takes you in")),
            // And the Nova panel, which also promised better odds per
            // forge. Every forge has been a flat 50/50 since V322.
            new ListPatch("nova-panel-v337", "holograms.panels.novacore.lines",
                    List.of("<white>Forge <#F48FB1>Nova Cores</#F48FB1> to climb 20 tiers",
                            "<white>Every tier multiplies <#81C784>Luck</#81C784>, <#81C784>Money</#81C784> and <#FFD54F>Coins</#FFD54F>",
                            "<white>More Luck means better odds per forge"),
                    List.of("<white>Forge <#F48FB1>Cores</#F48FB1> for 20 tiers",
                            "<white>Every tier multiplies your stats",
                            "<white>Every forge is a 50/50")),
            // One button per line over a crate, instead of two buttons on
            // one long line. These run after the TextPatches above, so a
            // server taking V336 and V337 in one go lands on the three.
            new ListPatch("crate-desc-farm-v337", "crates.types.farm.description",
                    List.of("<white>Right click opens one <dark_gray>|<white> Shift right click opens all",
                            "<gray>Left click shows every reward and its chance"),
                    List.of("<white>Right click opens one",
                            "<white>Shift right click opens all",
                            "<gray>Left click shows the rewards")),
            new ListPatch("crate-desc-cosmic-v337", "crates.types.cosmic.description",
                    List.of("<white>Right click opens one <dark_gray>|<white> Shift right click opens all",
                            "<gray>Left click shows every reward and its chance"),
                    List.of("<white>Right click opens one",
                            "<white>Shift right click opens all",
                            "<gray>Left click shows the rewards")),
            new ListPatch("crate-desc-vote-v337", "crates.types.vote.description",
                    List.of("<white>Right click opens one <dark_gray>|<white> Shift right click opens all",
                            "<gray>Left click shows every reward and its chance"),
                    List.of("<white>Right click opens one",
                            "<white>Shift right click opens all",
                            "<gray>Left click shows the rewards")),
            new ListPatch("crate-desc-nebula-v337", "crates.types.nebula.description",
                    List.of("<white>Right click opens one <dark_gray>|<white> Shift right click opens all",
                            "<gray>Left click shows every reward and its chance"),
                    List.of("<white>Right click opens one",
                            "<white>Shift right click opens all",
                            "<gray>Left click shows the rewards")),
            // V336: the Secret Realm panel says what the realm is for.
            // Leon's call: no "Click Here" on something entered with a
            // command, and say that the odds inside are flat.
            new ListPatch("realm-panel-v336", "holograms.panels.realm.lines",
                    List.of("<white>Opens on its own, for a few minutes",
                            "<white>Roll inside for a <#B0BEC5>secret</#B0BEC5>",
                            "<white>/secretrealm takes you in while it is open"),
                    List.of("<white>Roll in here for a chance at a <#B0BEC5>Secret</#B0BEC5>",
                            "<white>Every secret found multiplies your <#80DEEA>index Luck</#80DEEA>",
                            "<gray>Luck and Speed do nothing in here, the odds are flat",
                            "<white>Opens on its own, /secretrealm takes you in")));

    // Built from its code point so no dash character sits in this file.
    private static final String DASH = String.valueOf((char) 0x2014);

    private static final List<TextPatch> TEXT_PATCHES = List.of(
            // V343: the respec is free, so the tip selling it for shinies
            // was asking for something nobody has to pay.
            new TextPatch("respec-free-tip-v343", "announcements.messages",
                    "&3\u258e &e\u25b8 &7Respec any time for a few &fshinies&7 - the price climbs one per respec.",
                    "&3\u258e &e\u25b8 &7Respec any time in /skilltree, &ffree&7 - spend your Money somewhere else."),
            // And the two tips still selling Tag Luck, which pays nothing
            // since V337 and has no skill behind it since V343.
            new TextPatch("tip-tag-cosmetic-v343", "announcements.messages",
                    "&3\u258e &e\u25b8 &7Every drop has its own &bTag Luck&7, equip the best in &e/index&7.",
                    "&3\u258e &e\u25b8 &7Wear any drop you have found as a &btag&7 from &e/index&7."),
            new TextPatch("tip-secret-luck-v343", "announcements.messages",
                    "&3\u258e &e\u25b8 &7Unlock &fTag Luck&7 in &e/skilltree&7 before you can wear one.",
                    "&3\u258e &e\u25b8 &7The Luck multiplier is your best &dsecret&7 - see &d/secretindex&7."),
            // V337: the index tag pays no Luck any more, the best secret
            // does, so the panel over the index NPC stopped being true.
            new TextPatch("index-panel-tag-luck-v337", "holograms.panels.index.lines",
                    "<white>Equip a tag for <#80DEEA>Tag Luck</#80DEEA>",
                    "<white>Equip a tag to show it off"),
            // V336: keys stopped being items in V332, and the buttons are
            // split per job now: right click opens one, shift right click
            // opens all, left click shows the rewards. These lines float
            // over every crate, so they were telling players to do
            // something that no longer exists.
            new TextPatch("crate-click-farm-v336", "crates.types.farm.description",
                    "<white>Right-click with a <#FFD54F>Farm Key</#FFD54F> to open",
                    "<white>Right click opens one <dark_gray>|<white> Shift right click opens all"),
            new TextPatch("crate-click-cosmic-v336", "crates.types.cosmic.description",
                    "<white>Right-click with a <#B388FF>Cosmic Key</#B388FF> to open",
                    "<white>Right click opens one <dark_gray>|<white> Shift right click opens all"),
            new TextPatch("crate-click-vote-v336", "crates.types.vote.description",
                    "<white>Right-click with a <#69F0AE>Vote Key</#69F0AE> to open",
                    "<white>Right click opens one <dark_gray>|<white> Shift right click opens all"),
            new TextPatch("crate-click-nebula-v336", "crates.types.nebula.description",
                    "<white>Right-click with a <#82B1FF>Nebula Key</#82B1FF> to open",
                    "<white>Right click opens one <dark_gray>|<white> Shift right click opens all"),
            new TextPatch("crate-look-farm-v336", "crates.types.farm.description",
                    "<gray>Left-click to see what's inside",
                    "<gray>Left click shows every reward and its chance"),
            new TextPatch("crate-look-cosmic-v336", "crates.types.cosmic.description",
                    "<gray>Left-click to see what's inside",
                    "<gray>Left click shows every reward and its chance"),
            new TextPatch("crate-look-vote-v336", "crates.types.vote.description",
                    "<gray>Left-click to see what's inside",
                    "<gray>Left click shows every reward and its chance"),
            new TextPatch("crate-look-nebula-v336", "crates.types.nebula.description",
                    "<gray>Left-click to see what's inside",
                    "<gray>Left click shows every reward and its chance"),
            // V228: Perk Tickets left the store.
            new TextPatch("store-panel-no-tickets", "holograms.panels.store.lines",
                    "<white>Luck boost, the pass and perk tickets", "<white>Luck boost and the Battle Pass"),
            // V132: the Discord and Perks tips in the same look as every other tip.
            new TextPatch("tip-discord-1", "announcements.messages", "&5&l| DISCORD SERVER",
                    "&3▎ &e▸ &7Join our &9&lDiscord&7 for giveaways, news and updates."),
            new TextPatch("tip-discord-2", "announcements.messages", "&5| &fJoin us for giveaways, news and updates.",
                    "&3▎ &e▸ &7Click to join: &b&ndiscord.gg/spacerng"),
            new TextPatch("tip-discord-3", "announcements.messages", "&5| &b&nhttps://discord.gg/spacerng", null),
            new TextPatch("tip-perks-1", "announcements.messages", "&6&l| PERKS",
                    "&3▎ &e▸ &7Trade drops for perks in &d/perks&7. Keep the good ones with save rolls."),
            new TextPatch("tip-perks-2", "announcements.messages", "&6| &fTrade &e/roll&7 drops for perks in &e/perks&7.",
                    "&3▎ &e▸ &7Every perk you have found is listed in &d/perks index&7."),
            new TextPatch("tip-perks-3", "announcements.messages", "&6| &7Higher tier drops = better perk odds.", null),
            // V132: dashes out of the tips.
            new TextPatch("tip-dash-novacore", "announcements.messages",
                    "&3▎ &e▸ &d/novacore&7 is the fourth way to raise your Luck " + DASH + " after skills, armor and prestige.",
                    "&3▎ &e▸ &d/novacore&7 is the fourth way to raise your Luck, after skills, armor and prestige."),
            new TextPatch("tip-dash-crops", "announcements.messages",
                    "&3▎ &e▸ &7Pick what grows for you with &e/crops&7 " + DASH + " the field is yours alone.",
                    "&3▎ &e▸ &7Pick what grows for you with &e/crops&7. The field is yours alone."),
            new TextPatch("tip-dash-daily", "announcements.messages",
                    "&3▎ &e▸ &7Claim your streak every day with &e/daily&7 " + DASH + " miss one and it resets.",
                    "&3▎ &e▸ &7Claim your streak every day with &e/daily&7. Miss one and it resets."),
            new TextPatch("tip-dash-pass", "announcements.messages",
                    "&3▎ &e▸ &6/pass&7 pays out for every roll and every harvest " + DASH + " rarer rolls are worth more XP.",
                    "&3▎ &e▸ &6/pass&7 pays out for every roll and every harvest. Rarer rolls are worth more XP."),
            // V136: perks are rolled with Perk Tickets and there is no loadout any more.
            new TextPatch("panel-perks-1", "holograms.panels.perks.lines", "<white>Roll perks with your drops",
                    "<white>Roll a perk with <#B39DDB>Perk Tickets</#B39DDB>"),
            new TextPatch("panel-perks-2", "holograms.panels.perks.lines",
                    "<white>and equip your best <#B39DDB>loadout</#B39DDB>",
                    "<white>and chase the <#B39DDB>Universe</#B39DDB> perk"),
            new TextPatch("tip-perks-v136", "announcements.messages",
                    "&3▎ &e▸ &7Trade drops for perks in &d/perks&7. Keep the good ones with save rolls.",
                    "&3▎ &e▸ &7Roll a perk in &d/perks&7 with Perk Tickets. Every 100 rolls promise a Mythical."));

    private ConfigMigrator() {
    }

    private static List<Object> rewrite(List<?> list, TextPatch patch) {
        List<Object> out = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof List<?> inner) {
                out.add(rewrite(inner, patch));
            } else if (patch.oldText().equals(entry)) {
                if (patch.newText() != null) out.add(patch.newText());
            } else {
                out.add(entry);
            }
        }
        return out;
    }

    /**
     * Runs both steps and saves once if either changed anything. Loud in
     * the log, on purpose: a rewrite the owner didn't make by hand is
     * exactly the kind of thing they have to be able to see afterwards.
     */
    public static void run(SolRNGPlugin plugin) {
        FileConfiguration disk = plugin.getConfig();
        boolean changed = migrateStructural(plugin, disk);
        changed |= applyPatches(plugin, disk);
        if (!changed) return;

        File file = new File(plugin.getDataFolder(), "config.yml");
        try {
            disk.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Couldn't save the migrated config.yml: " + ex.getMessage());
        }
    }

    private static boolean migrateStructural(SolRNGPlugin plugin, FileConfiguration disk) {
        int diskVersion = disk.getInt("config-version", 1);

        InputStream defaultsStream = plugin.getResource("config.yml");
        if (defaultsStream == null) return false;

        YamlConfiguration defaults;
        // UTF-8 explicitly: the platform default on a Windows host would
        // mangle every star in the sections copied across.
        try (InputStreamReader reader = new InputStreamReader(defaultsStream, StandardCharsets.UTF_8)) {
            defaults = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Couldn't read the packaged config.yml for migration: " + ex.getMessage());
            return false;
        }
        int jarVersion = defaults.getInt("config-version", 1);

        // A whole new section never merges into an existing config.yml on
        // its own, so copy each across the first time the jar ships it.
        boolean added = false;
        for (String section : ADDED_SECTIONS) {
            // contains(path, true) IGNORES defaults, and that is the whole
            // point here. plugin.getConfig() carries the jar's config.yml
            // as its defaults, so plain contains() answers true for every
            // section the jar ships whether the server has it or not, and
            // nothing was ever copied. Worse, a later
            // getConfigurationSection() on a path that lives only in the
            // defaults hands back a new EMPTY section rather than the
            // default one, which is why boss.types and pets.types loaded
            // as zero entries on a server whose file predates them.
            if (!defaults.contains(section)) continue;
            boolean missing = !disk.contains(section, true);
            // And an EMPTY section counts as missing. Reading a path that
            // lived only in the defaults created a blank section in memory,
            // and the next save wrote that blank section to disk, which
            // would otherwise lock the real one out forever.
            if (!missing && disk.get(section) instanceof org.bukkit.configuration.ConfigurationSection existing
                    && existing.getKeys(false).isEmpty()) {
                missing = true;
            }
            if (missing) {
                disk.set(section, defaults.get(section));
                plugin.getLogger().info("Config: added the new section " + section + ".");
                added = true;
            }
        }

        if (diskVersion >= jarVersion) return added;

        plugin.getLogger().info("Config on disk is version " + diskVersion
                + ", jar is version " + jarVersion
                + ". Rewriting structural sections in place.");

        for (String section : STRUCTURAL) {
            if (defaults.contains(section)) {
                disk.set(section, defaults.get(section));
                plugin.getLogger().info(" - refreshed section: " + section);
            }
        }
        disk.set("config-version", jarVersion);
        return true;
    }

    private static boolean applyPatches(SolRNGPlugin plugin, FileConfiguration disk) {
        List<String> applied = new ArrayList<>(disk.getStringList("applied-patches"));
        boolean changed = false;
        List<Patch> allPatches = new ArrayList<>(PATCHES);
        allPatches.addAll(ENCHANT_PATCHES);
        allPatches.addAll(ENCHANT_CAP_PATCHES);
        allPatches.addAll(ENCHANT_MAX_PATCHES);

        for (String[] removal : REMOVALS) {
            if (applied.contains(removal[0]) || !disk.contains(removal[1], true)) continue;
            disk.set(removal[1], null);
            applied.add(removal[0]);
            plugin.getLogger().info("Config: removed " + removal[1] + ".");
            changed = true;
        }
        for (Patch patch : allPatches) {
            if (applied.contains(patch.id())) continue;
            // A map-shaped value comes back from disk as a section, so it is
            // compared by its entries (keys as strings) rather than as an object.
            Object current = disk.get(patch.path());
            if (current instanceof org.bukkit.configuration.ConfigurationSection section) {
                current = section.getValues(false);
            }
            if (Objects.equals(current, patch.oldDefault())) {
                if (patch.newDefault() instanceof Map<?, ?>) disk.set(patch.path(), null);
                disk.set(patch.path(), patch.newDefault());
                plugin.getLogger().info("Config patch " + patch.id() + ": " + patch.path()
                        + " is now " + patch.newDefault());
            }
            // Marked applied either way, so a value chosen by hand is never revisited.
            applied.add(patch.id());
            changed = true;
        }
        for (EntryPatch patch : ENTRY_PATCHES) {
            if (applied.contains(patch.id())) continue;
            List<Map<?, ?>> entries = disk.getMapList(patch.list());
            for (Map<?, ?> entry : entries) {
                if (!patch.entryId().equals(entry.get("id"))) continue;
                if (Objects.equals(entry.get(patch.field()), patch.oldDefault())) {
                    @SuppressWarnings("unchecked")
                    Map<Object, Object> editable = (Map<Object, Object>) entry;
                    editable.put(patch.field(), patch.newDefault());
                    disk.set(patch.list(), entries);
                    plugin.getLogger().info("Config patch " + patch.id() + ": " + patch.list() + " "
                            + patch.entryId() + "." + patch.field() + " updated");
                }
            }
            applied.add(patch.id());
            changed = true;
        }
        // V133: Common, Uncommon and Rare odds no longer overlap on their labels.
        if (!applied.contains("band-odds-no-overlap")) {
            List<Map<?, ?>> items = disk.getMapList("items");
            if (com.spacerng.solrng.rarity.OddsBands.remapItems(items)) {
                disk.set("items", items);
                plugin.getLogger().info("Config patch band-odds-no-overlap: Common, Uncommon and Rare odds respaced");
            }
            applied.add("band-odds-no-overlap");
            changed = true;
        }
        // V304: Epic and up spaced 5x apart. Matched on rarity and the old
        // odds, so a drop whose odds were tuned by hand keeps them.
        if (!applied.contains("rarity-gaps-5x")) {
            java.util.Map<String, Long> spaced = new java.util.HashMap<>();
            long[][] table = {
                    {33000, 75000}, {36600, 83200}, {40600, 92300}, {44900, 102000}, {49500, 112500},
                    {54800, 125000}, {60700, 138000}, {67300, 153000}, {74600, 170000}, {82500, 187500}};
            for (long[] row : table) spaced.put("EPIC:" + row[0], row[1]);
            long[][] legendary = {{100000, 937500}, {119000, 1120000}, {141000, 1320000},
                    {168000, 1575000}, {200000, 1875000}};
            for (long[] row : legendary) spaced.put("LEGENDARY:" + row[0], row[1]);
            long[][] mythical = {{250000, 9375000}, {500000, 18750000}, {1000000, 37500000}};
            for (long[] row : mythical) spaced.put("MYTHICAL:" + row[0], row[1]);
            spaced.put("DIVINE:10000000", 187500000L);
            List<Map<?, ?>> items = disk.getMapList("items");
            boolean moved = false;
            for (Map<?, ?> entry : items) {
                Object odds = entry.get("odds");
                if (!(odds instanceof Number number)) continue;
                Long now = spaced.get(entry.get("rarity") + ":" + number.longValue());
                if (now == null) continue;
                @SuppressWarnings("unchecked")
                Map<Object, Object> editable = (Map<Object, Object>) entry;
                editable.put("odds", now);
                moved = true;
            }
            if (moved) {
                disk.set("items", items);
                plugin.getLogger().info("Config patch rarity-gaps-5x: Epic and up respaced");
            }
            applied.add("rarity-gaps-5x");
            changed = true;
        }

        // V340: Epic and up are a lot less rare, Leon's call ("lower the
        // odds of the rest by 5-10x"). Epic, Legendary and Mythical are
        // five times easier, Divine seven and a half, Astral ten, and the
        // spread inside every tier is kept.
        //
        // Every old value is named rather than divided on sight, for two
        // reasons: a server that installed V340 fresh must not have its
        // already-lowered odds halved again, and an item Leon has retuned
        // by hand keeps his number.
        //
        // Money pays on an item's odds, so an Epic drop is worth a fifth
        // of what it was. That follows from the odds and is the point:
        // they come five times as often.
        if (!applied.contains("drops-easier-v340")) {
            java.util.Map<String, Long> easier = new java.util.HashMap<>();
            long[][] epic = {{75000, 15000}, {83200, 16600}, {92300, 18500}, {102000, 20400}, {112500, 22500}, {125000, 25000}, {138000, 27600}, {153000, 30600}, {170000, 34000}, {187500, 37500}};
            for (long[] row : epic) easier.put("EPIC:" + row[0], row[1]);
            long[][] legendary = {{937500, 188000}, {1010000, 202000}, {1120000, 224000}, {1210000, 242000}, {1320000, 264000}, {1430000, 286000}, {1575000, 315000}, {1680000, 336000}, {1790000, 358000}, {1875000, 375000}};
            for (long[] row : legendary) easier.put("LEGENDARY:" + row[0], row[1]);
            long[][] mythical = {{9375000, 1880000}, {11200000, 2240000}, {14000000, 2800000}, {18750000, 3750000}, {22500000, 4500000}, {28000000, 5600000}, {33000000, 6600000}, {37500000, 7500000}};
            for (long[] row : mythical) easier.put("MYTHICAL:" + row[0], row[1]);
            long[][] divine = {{187500000, 25000000}, {225000000, 30000000}, {270000000, 36000000}, {320000000, 42700000}, {380000000, 50700000}, {450000000, 60000000}, {520000000, 69300000}, {600000000, 80000000}, {680000000, 90700000}, {750000000, 100000000}};
            for (long[] row : divine) easier.put("DIVINE:" + row[0], row[1]);
            long[][] astral = {{3750000000L, 375000000L}, {7500000000L, 750000000L},
                    {15000000000L, 1500000000L}};
            for (long[] row : astral) easier.put("ASTRAL:" + row[0], row[1]);
            List<Map<?, ?>> items = disk.getMapList("items");
            boolean moved = false;
            for (Map<?, ?> entry : items) {
                Object odds = entry.get("odds");
                if (!(odds instanceof Number number)) continue;
                Long now = easier.get(entry.get("rarity") + ":" + number.longValue());
                if (now == null) continue;
                @SuppressWarnings("unchecked")
                Map<Object, Object> editable = (Map<Object, Object>) entry;
                editable.put("odds", now);
                moved = true;
            }
            if (moved) {
                disk.set("items", items);
                plugin.getLogger().info("Config patch drops-easier-v340: Epic and up made less rare");
            }
            applied.add("drops-easier-v340");
            changed = true;
        }
        // V319: the new top-tier drops. Leon asked for at least ten Divine
        // and more at every tier from Legendary; there was ONE Divine, so a
        // Divine was a single named drop rather than a tier.
        //
        // items is not structural and never can be - it is 190 drops and
        // the whole table is Leon's - so a new drop has to be appended to
        // whatever is on disk. Matched by NAME: a name already there is
        // skipped, so this is safe to run against a config somebody has
        // edited, and it never touches an existing entry's odds.
        //
        // Read out of the jar's own config rather than retyped here. A
        // second copy of twenty-four drops in Java is a second copy that
        // can disagree with the first.
        if (!applied.contains("astral-tier-drops")) {
            // The jar's own config, read again here: applyPatches only has
            // the file on disk, and plugin.getConfig()'s defaults cannot be
            // used because getMapList would hand back the MERGED list.
            List<Map<?, ?>> jarItems = List.of();
            InputStream jarStream = plugin.getResource("config.yml");
            if (jarStream != null) {
                try (InputStreamReader reader = new InputStreamReader(jarStream, StandardCharsets.UTF_8)) {
                    jarItems = YamlConfiguration.loadConfiguration(reader).getMapList("items");
                } catch (IOException ex) {
                    plugin.getLogger().log(Level.WARNING,
                            "Couldn't read the packaged config.yml for the Astral drops: " + ex.getMessage());
                }
            }
            List<Map<?, ?>> diskItems = disk.getMapList("items");
            java.util.Set<String> have = new java.util.HashSet<>();
            for (Map<?, ?> entry : diskItems) have.add(String.valueOf(entry.get("name")));

            List<Object> merged = new ArrayList<>(diskItems);
            int added = 0;
            for (Map<?, ?> entry : jarItems) {
                String rarity = String.valueOf(entry.get("rarity"));
                if (!"LEGENDARY".equals(rarity) && !"MYTHICAL".equals(rarity)
                        && !"DIVINE".equals(rarity) && !"ASTRAL".equals(rarity)) {
                    continue;
                }
                if (have.add(String.valueOf(entry.get("name")))) {
                    merged.add(entry);
                    added++;
                }
            }
            if (added > 0) {
                disk.set("items", merged);
                plugin.getLogger().info("Config patch astral-tier-drops: added " + added
                        + " Legendary and up drops, Astral included");
            }
            applied.add("astral-tier-drops");
            changed = true;
        }
        // V321: the Skilltree Unlock Voucher comes out of every crate,
        // Leon's call. /milestones keeps three and the Battle Pass gets
        // one on the free track and two on the paid one, which is a
        // structural section and three ADDED keys respectively, so only
        // the crates need doing by hand here.
        //
        // Removing an entry rather than swapping one, which none of the
        // patch kinds can do: the weight it carried goes back to the rest
        // of the table instead of being handed to something else, because
        // picking a replacement for Leon is not this patch's job.
        if (!applied.contains("no-voucher-in-crates")) {
            boolean hit = false;
            for (String path : new String[]{"crates.types.farm.rewards",
                    "crates.types.cosmic.rewards", "crates.types.vote.rewards",
                    "crates.types.nebula.rewards", "crates.types.boss.rewards"}) {
                List<?> rewards = disk.getList(path);
                if (rewards == null) continue;
                List<Object> kept = new ArrayList<>();
                for (Object reward : rewards) {
                    if (reward instanceof java.util.Map<?, ?> map
                            && "free_skill".equals(String.valueOf(map.get("consumable")))) {
                        continue;
                    }
                    kept.add(reward);
                }
                if (kept.size() != rewards.size()) {
                    disk.set(path, kept);
                    hit = true;
                }
            }
            if (hit) {
                plugin.getLogger().info("Config patch no-voucher-in-crates: vouchers removed from the crates");
            }
            applied.add("no-voucher-in-crates");
            changed = true;
        }
        // V321: and the three the pass now gives. Added, not mapped, since
        // the pass never gave one, and only where the slot is still empty
        // so a hand-tuned pass is left alone. Level 24 free, 25 and 38
        // premium, which are the levels whose tracks had no consumable.
        if (!applied.contains("voucher-in-pass")) {
            List<?> levels = disk.getList("pass.levels");
            if (levels != null) {
                List<Object> rewritten = new ArrayList<>(levels);
                boolean hit = false;
                for (Object[] spot : new Object[][]{{24, "free"}, {25, "premium"}, {38, "premium"}}) {
                    int index = (Integer) spot[0] - 1;
                    String track = (String) spot[1];
                    if (index < 0 || index >= rewritten.size()) continue;
                    if (!(rewritten.get(index) instanceof java.util.Map<?, ?> level)) continue;
                    if (!(level.get(track) instanceof java.util.Map<?, ?> side)) continue;
                    if (side.get("consumable") != null) continue;
                    java.util.Map<Object, Object> newSide = new java.util.LinkedHashMap<>(side);
                    newSide.put("consumable", "free_skill");
                    java.util.Map<Object, Object> newLevel = new java.util.LinkedHashMap<>(level);
                    newLevel.put(track, newSide);
                    rewritten.set(index, newLevel);
                    hit = true;
                }
                if (hit) {
                    disk.set("pass.levels", rewritten);
                    plugin.getLogger().info("Config patch voucher-in-pass: vouchers added to the pass");
                }
            }
            applied.add("voucher-in-pass");
            changed = true;
        }
        // V209: renamed drops, only where the old name is still on disk.
        if (!applied.contains("renamed-drops")) {
            List<Map<?, ?>> items = disk.getMapList("items");
            boolean renamed = false;
            for (Map<?, ?> entry : items) {
                String now = com.spacerng.solrng.rarity.RarityManager.RENAMED.get(String.valueOf(entry.get("name")));
                if (now == null) continue;
                @SuppressWarnings("unchecked")
                Map<Object, Object> editable = (Map<Object, Object>) entry;
                editable.put("name", now);
                renamed = true;
            }
            if (renamed) {
                disk.set("items", items);
                plugin.getLogger().info("Config patch renamed-drops: items renamed");
            }
            applied.add("renamed-drops");
            changed = true;
        }
        for (TextPatch patch : TEXT_PATCHES) {
            if (applied.contains(patch.id())) continue;
            List<?> list = disk.getList(patch.path());
            if (list != null) {
                List<Object> rewritten = rewrite(list, patch);
                if (!rewritten.equals(list)) {
                    disk.set(patch.path(), rewritten);
                    plugin.getLogger().info("Config patch " + patch.id() + ": " + patch.path() + " updated");
                }
            }
            applied.add(patch.id());
            changed = true;
        }
        for (EntryRemoval removal : ENTRY_REMOVALS) {
            if (applied.contains(removal.id())) continue;
            List<Map<?, ?>> entries = disk.getMapList(removal.list());
            boolean dropped = entries.removeIf(entry -> removal.entryId().equals(entry.get("id")));
            if (dropped) {
                disk.set(removal.list(), entries);
                plugin.getLogger().info("Config patch " + removal.id() + ": " + removal.entryId()
                        + " removed from " + removal.list());
            }
            applied.add(removal.id());
            changed = true;
        }
        for (ListPatch patch : LIST_PATCHES) {
            if (applied.contains(patch.id())) continue;
            // Only while it still reads like the old default, the same
            // promise every other patch makes: a panel Leon has reworded
            // by hand keeps his words.
            if (disk.getStringList(patch.path()).equals(patch.oldLines())) {
                disk.set(patch.path(), patch.newLines());
                plugin.getLogger().info("Config patch " + patch.id() + ": " + patch.path() + " rewritten");
            }
            applied.add(patch.id());
            changed = true;
        }
        // V349: Astral is five times rarer again. V340 made it ten times
        // easier while the table was frozen and an Astral was unreachable
        // at any Luck at all; now that Luck climbs the ladder properly it
        // does not need that help, and the rarest thing in the game should
        // stay the rarest thing in the game.
        if (!applied.contains("astral-rarer-v349")) {
            java.util.Map<Long, Long> rarer = java.util.Map.of(
                    375000000L, 1875000000L, 750000000L, 3750000000L, 1500000000L, 7500000000L);
            List<Map<?, ?>> items = disk.getMapList("items");
            boolean moved = false;
            for (Map<?, ?> entry : items) {
                if (!"ASTRAL".equals(entry.get("rarity"))) continue;
                Object odds = entry.get("odds");
                if (!(odds instanceof Number number)) continue;
                Long now = rarer.get(number.longValue());
                if (now == null) continue;
                @SuppressWarnings("unchecked")
                Map<Object, Object> editable = (Map<Object, Object>) entry;
                editable.put("odds", now);
                moved = true;
            }
            if (moved) {
                disk.set("items", items);
                plugin.getLogger().info("Config patch astral-rarer-v349: Astral odds back up");
            }
            applied.add("astral-rarer-v349");
            changed = true;
        }
        // V350: Mythical two and a half times rarer at the table and
        // Divine three times, which is the half of the dial that does not
        // touch the tiers under them. Money pays on an item's odds, so a
        // Divine is worth three times what it was and comes a third as
        // often: the same Money per roll, a rarer trophy.
        if (!applied.contains("top-odds-v350")) {
            long[][] table = {
                    {25000000, 75000000},
                    {30000000, 90000000},
                    {36000000, 108000000},
                    {42700000, 128000000},
                    {50700000, 152000000},
                    {60000000, 180000000},
                    {69300000, 208000000},
                    {80000000, 240000000},
                    {90700000, 272000000},
                    {100000000, 300000000},
                    {1880000, 4700000},
                    {2240000, 5600000},
                    {2800000, 7000000},
                    {3750000, 9380000},
                    {4500000, 11200000},
                    {5600000, 14000000},
                    {6600000, 16500000},
                    {7500000, 18800000}
            };
            java.util.Map<Long, Long> rarer = new java.util.HashMap<>();
            for (long[] row : table) rarer.put(row[0], row[1]);
            List<Map<?, ?>> items = disk.getMapList("items");
            boolean moved = false;
            for (Map<?, ?> entry : items) {
                Object rarity = entry.get("rarity");
                if (!"MYTHICAL".equals(rarity) && !"DIVINE".equals(rarity)) continue;
                Object odds = entry.get("odds");
                if (!(odds instanceof Number number)) continue;
                Long now = rarer.get(number.longValue());
                if (now == null) continue;
                @SuppressWarnings("unchecked")
                Map<Object, Object> editable = (Map<Object, Object>) entry;
                editable.put("odds", now);
                moved = true;
            }
            if (moved) {
                disk.set("items", items);
                plugin.getLogger().info("Config patch top-odds-v350: Mythical and Divine made rarer");
            }
            applied.add("top-odds-v350");
            changed = true;
        }
        // V351: the ten Nova Core multipliers above tier 20, and the two
        // draughts that multiply Luck. A list and two consumables, neither
        // of which a Patch can reach, and both only touched when the live
        // config still holds exactly what came before them.
        if (!applied.contains("nova-ladder-v351")) {
            List<Double> ladder = disk.getDoubleList("novacore.multipliers");
            if (ladder.size() == 20) {
                ladder.addAll(List.of(15.0, 19.0, 24.0, 30.0, 38.0, 48.0, 60.0, 75.0, 95.0, 120.0));
                disk.set("novacore.multipliers", ladder);
                plugin.getLogger().info("Config patch nova-ladder-v351: ten more Nova Core tiers");
            }
            applied.add("nova-ladder-v351");
            changed = true;
        }
        // V352: the six that multiply, three Luck and three Speed, priced
        // one Mythical, one Divine, one Astral. This replaces the pair
        // V351 shipped, so the V351 patch below only ever fires on a
        // config that took it before this jar existed.
        if (!applied.contains("draughts-shelf-v352")) {
            InputStream jarStream = plugin.getResource("config.yml");
            if (jarStream != null) {
                try (InputStreamReader reader = new InputStreamReader(jarStream, StandardCharsets.UTF_8)) {
                    YamlConfiguration jar = YamlConfiguration.loadConfiguration(reader);
                    for (String id : List.of("draught_prism", "draught_quasar", "draught_singularity",
                            "draught_flux", "draught_warp", "draught_lightspeed")) {
                        disk.set("consumables." + id, jar.get("consumables." + id));
                    }
                    plugin.getLogger().info("Config patch draughts-shelf-v352: six multiplying draughts");
                } catch (IOException ex) {
                    plugin.getLogger().warning("Could not write the V352 draughts: " + ex.getMessage());
                }
            }
            applied.add("draughts-shelf-v352");
            applied.add("draughts-multi-v351");
            changed = true;
        }
        if (!applied.contains("draughts-multi-v351")) {
            if (!disk.contains("consumables.draught_prism", true)) {
                InputStream jarStream = plugin.getResource("config.yml");
                if (jarStream != null) {
                    try (InputStreamReader reader = new InputStreamReader(jarStream, StandardCharsets.UTF_8)) {
                        YamlConfiguration jar = YamlConfiguration.loadConfiguration(reader);
                        for (String id : List.of("draught_prism", "draught_singularity")) {
                            disk.set("consumables." + id, jar.get("consumables." + id));
                        }
                        plugin.getLogger().info("Config patch draughts-multi-v351: two draughts added");
                    } catch (IOException ex) {
                        plugin.getLogger().warning("Could not add the V351 draughts: " + ex.getMessage());
                    }
                }
            }
            applied.add("draughts-multi-v351");
            changed = true;
        }
        // V315: permanent Luck in small pieces, Leon's ladder: the
        // cheapest crate pays 5 percent, the middle one 10 and the best
        // 25, and nothing hands out the old +50 or +250 any more.
        //
        // A crate's rewards are a list of maps with no id, so neither
        // Patch (a fixed path), EntryPatch (a field of an entry by id)
        // nor TextPatch (a list of strings) can reach them. And neither
        // crates nor pass may join STRUCTURAL: Leon tunes the crate
        // weights and the Boss Box by hand, and a structural rewrite
        // takes his numbers with it.
        //
        // The old entries are mapped rather than merged. A crate that
        // had both a 50 and a 250 keeps two entries now pointing at the
        // same consumable, where the jar has one with their weights
        // added; the odds come out the same and nothing is lost.
        if (!applied.contains("luck-small-crates")) {
            boolean hit = false;
            for (String[] swap : new String[][]{
                    {"crates.types.farm.rewards", "luck_50", "luck_5"},
                    {"crates.types.farm.rewards", "luck_250", "luck_5"},
                    {"crates.types.vote.rewards", "luck_50", "luck_5"},
                    {"crates.types.vote.rewards", "luck_250", "luck_5"},
                    {"crates.types.cosmic.rewards", "luck_50", "luck_10"},
                    {"crates.types.cosmic.rewards", "luck_250", "luck_10"},
                    {"crates.types.nebula.rewards", "luck_50", "luck_25"},
                    {"crates.types.nebula.rewards", "luck_250", "luck_25"}}) {
                List<?> rewards = disk.getList(swap[0]);
                if (rewards == null) continue;
                List<Object> rewritten = new ArrayList<>();
                boolean changedHere = false;
                for (Object reward : rewards) {
                    if (reward instanceof java.util.Map<?, ?> map
                            && swap[1].equals(String.valueOf(map.get("consumable")))) {
                        java.util.Map<Object, Object> copy = new java.util.LinkedHashMap<>(map);
                        copy.put("consumable", swap[2]);
                        rewritten.add(copy);
                        changedHere = true;
                    } else {
                        rewritten.add(reward);
                    }
                }
                if (changedHere) {
                    disk.set(swap[0], rewritten);
                    hit = true;
                }
            }
            if (hit) {
                plugin.getLogger().info("Config patch luck-small-crates: crate permanent Luck rewritten");
            }
            applied.add("luck-small-crates");
            changed = true;
        }
        // V331: more Nova Cores out of every crate, Leon's call ("increase
        // the amount of novacores you can get from crates and the enchant
        // by quite a bit"). A crate's rewards are a list of maps with no
        // id, so this is the same hand-rolled walk the V315 Luck swap
        // needed, and for the same reason: crates may not join STRUCTURAL
        // because Leon tunes the weights by hand.
        //
        // Only an entry that still holds the OLD amount is rewritten, so a
        // crate he has already retuned is left exactly as it is.
        if (!applied.contains("nova-cores-crates-v331")) {
            boolean hit = false;
            for (Object[] bump : new Object[][]{
                    {"crates.types.farm.rewards", 1, 3, 10},
                    {"crates.types.vote.rewards", 1, 3, 10},
                    {"crates.types.nebula.rewards", 1, 5, 16},
                    {"crates.types.cosmic.rewards", 2, 10, 16}}) {
                List<?> rewards = disk.getList((String) bump[0]);
                if (rewards == null) continue;
                List<Object> rewritten = new ArrayList<>();
                boolean changedHere = false;
                for (Object reward : rewards) {
                    if (reward instanceof java.util.Map<?, ?> map
                            && "nova_core".equals(String.valueOf(map.get("consumable")))
                            && toInt(map.get("amount"), 1) == (Integer) bump[1]) {
                        java.util.Map<Object, Object> copy = new java.util.LinkedHashMap<>(map);
                        copy.put("amount", bump[2]);
                        copy.put("weight", bump[3]);
                        rewritten.add(copy);
                        changedHere = true;
                    } else {
                        rewritten.add(reward);
                    }
                }
                if (changedHere) {
                    disk.set((String) bump[0], rewritten);
                    hit = true;
                }
            }
            if (hit) {
                plugin.getLogger().info("Config patch nova-cores-crates-v331: crates pay more Nova Cores");
            }
            applied.add("nova-cores-crates-v331");
            changed = true;
        }
        // V315: the Battle Pass carries permanent Luck too, which it never
        // did before, so there is nothing to map - it has to be ADDED. The
        // premium track at levels 20, 35 and 40 had no consumable of its
        // own, which is why those three and not the ones a player would
        // notice losing. Only ever filled when the slot is still empty, so
        // a hand-tuned pass is left alone.
        if (!applied.contains("luck-small-pass")) {
            List<?> levels = disk.getList("pass.levels");
            if (levels != null) {
                List<Object> rewritten = new ArrayList<>(levels);
                boolean hit = false;
                for (int[] spot : new int[][]{{20, 5}, {35, 10}, {40, 25}}) {
                    int index = spot[0] - 1;
                    if (index < 0 || index >= rewritten.size()) continue;
                    if (!(rewritten.get(index) instanceof java.util.Map<?, ?> level)) continue;
                    if (!(level.get("premium") instanceof java.util.Map<?, ?> premium)) continue;
                    if (premium.get("consumable") != null) continue;
                    java.util.Map<Object, Object> newPremium = new java.util.LinkedHashMap<>(premium);
                    newPremium.put("consumable", "luck_" + spot[1]);
                    java.util.Map<Object, Object> newLevel = new java.util.LinkedHashMap<>(level);
                    newLevel.put("premium", newPremium);
                    rewritten.set(index, newLevel);
                    hit = true;
                }
                if (hit) {
                    disk.set("pass.levels", rewritten);
                    plugin.getLogger().info("Config patch luck-small-pass: permanent Luck added to the pass");
                }
            }
            applied.add("luck-small-pass");
            changed = true;
        }
        // V223: Bedrock joins through its own address, with .bedrock before
        // minehut. The server card gave the Java address with the Bedrock
        // port, which does not connect. The field sits in a list of maps,
        // which none of the patches above can reach.
        if (!applied.contains("bedrock-address-card")) {
            List<?> fields = disk.getList("discord.cards.server.fields");
            if (fields != null) {
                List<Object> rewritten = new ArrayList<>();
                boolean hit = false;
                for (Object field : fields) {
                    if (field instanceof java.util.Map<?, ?> map
                            && "`spacerng.minehut.gg` port `19132`".equals(map.get("value"))) {
                        java.util.Map<Object, Object> copy = new java.util.LinkedHashMap<>(map);
                        copy.put("value", "`spacerng.bedrock.minehut.gg` port `19132`");
                        rewritten.add(copy);
                        hit = true;
                    } else {
                        rewritten.add(field);
                    }
                }
                if (hit) {
                    disk.set("discord.cards.server.fields", rewritten);
                    plugin.getLogger().info("Config patch bedrock-address-card: discord.cards.server.fields updated");
                }
            }
            applied.add("bedrock-address-card");
            changed = true;
        }
        // V225: the How to Link card in the layout Leon showed. It is a
        // list of maps, so it is swapped whole, and only while it is still
        // the untouched V143 card.
        if (!applied.contains("link-card-v225")) {
            List<?> fields = disk.getList("discord.cards.link.fields");
            if (fields != null && !fields.isEmpty() && fields.get(0) instanceof java.util.Map<?, ?> first
                    && "1. Join the server".equals(first.get("name"))) {
                InputStream stream = plugin.getResource("config.yml");
                if (stream != null) {
                    try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                        YamlConfiguration jar = YamlConfiguration.loadConfiguration(reader);
                        if (jar.isConfigurationSection("discord.cards.link")) {
                            disk.set("discord.cards.link", jar.get("discord.cards.link"));
                            plugin.getLogger().info("Config patch link-card-v225: discord.cards.link replaced");
                        }
                    } catch (IOException ignored) {
                        // Left as it was.
                    }
                }
            }
            applied.add("link-card-v225");
            changed = true;
        }
        // V306: the changelog2 card from the jar, brought up to date after
        // V302 copied an older version of it into the live config.
        if (!applied.contains("changelog2-v306")) {
            InputStream stream = plugin.getResource("config.yml");
            if (stream != null) {
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    YamlConfiguration jar = YamlConfiguration.loadConfiguration(reader);
                    if (jar.isConfigurationSection("discord.cards.changelog2")) {
                        disk.set("discord.cards.changelog2", null);
                        disk.set("discord.cards.changelog2", jar.get("discord.cards.changelog2"));
                        plugin.getLogger().info("Config patch changelog2-v306: discord.cards.changelog2 replaced");
                    }
                } catch (IOException ignored) {
                    // Left as it was.
                }
            }
            applied.add("changelog2-v306");
            changed = true;
        }
        // V230: the link card gets its button. Swapped whole, and only while
        // it has no link-button key, which no card before V230 had.
        if (!applied.contains("link-card-v230")) {
            if (disk.isConfigurationSection("discord.cards.link") && !disk.contains("discord.cards.link.link-button", true)) {
                InputStream stream = plugin.getResource("config.yml");
                if (stream != null) {
                    try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                        YamlConfiguration jar = YamlConfiguration.loadConfiguration(reader);
                        if (jar.isConfigurationSection("discord.cards.link")) {
                            disk.set("discord.cards.link", jar.get("discord.cards.link"));
                            plugin.getLogger().info("Config patch link-card-v230: discord.cards.link replaced");
                        }
                    } catch (IOException ignored) {
                        // Left as it was.
                    }
                }
            }
            applied.add("link-card-v230");
            changed = true;
        }
        // V232: the link pays +100% Luck and nothing else, so the card's
        // reward lines are replaced while they still promise the old ones.
        if (!applied.contains("link-card-rewards-v232")) {
            Object fields = disk.get("discord.cards.link.fields");
            if (fields != null && String.valueOf(fields).contains("+10% Luck, Money, Coins and Shiny")) {
                InputStream stream = plugin.getResource("config.yml");
                if (stream != null) {
                    try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                        YamlConfiguration jar = YamlConfiguration.loadConfiguration(reader);
                        if (jar.isConfigurationSection("discord.cards.link")) {
                            disk.set("discord.cards.link", jar.get("discord.cards.link"));
                            plugin.getLogger().info("Config patch link-card-rewards-v232: discord.cards.link replaced");
                        }
                    } catch (IOException ignored) {
                        // Left as it was.
                    }
                }
            }
            applied.add("link-card-rewards-v232");
            changed = true;
        }
        if (changed) disk.set("applied-patches", applied);
        return changed;
    }
}
