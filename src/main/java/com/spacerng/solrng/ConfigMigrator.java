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
            "milestones");

    /**
     * Sections copied from the jar whenever the server's config has none
     * yet. A dotted path works too, for a new entry inside a section the
     * server already has, like one more hologram panel.
     */
    private static final List<String> ADDED_SECTIONS = List.of(
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
            // V148: the podium's own text size.
            "holograms.podium-text-scale",
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

    private static final List<Patch> PATCHES = List.of(
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
            new Patch("pet-make-cost-100", "pets.upgrades.make-cost", 10, 100));

    // V159: every hoe enchant runs to level 10,000, except Credit Finder,
    // which stays at 1,000 because it pays Credits.
    private static final List<Patch> ENCHANT_PATCHES = java.util.stream.Stream.of(
                    "TOKEN_GREED", "MOMENTUM", "SHARD_GREED", "KEY_FINDER", "BLAST_HARVEST",
                    "POTION_FINDER", "LIGHTNING", "NOVA_FINDER", "NUKE", "COIN_FACTORY", "GAMBA",
                    "PROSPECTOR", "GEM_RUSH", "ALCHEMY",
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
                    "PROSPECTOR", "GEM_RUSH", "ALCHEMY", "GEM_CASCADE", "COIN_STORM", "METEOR",
                    "BLACK_HOLE", "SUPERNOVA")
            .map(id -> new Patch("enchant-cap-10k-" + id, "farming.enchants." + id + ".base-cap", 100, 10000))
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
            new String[]{"enchant-gone-harvest-echo", "farming.enchants.HARVEST_ECHO"});

    /** Like a Patch, for one field of the entry with a given id inside a list of maps. */
    private record EntryPatch(String id, String list, String entryId, String field, Object oldDefault,
                              Object newDefault) {
    }

    private static final List<EntryPatch> ENTRY_PATCHES = List.of(
            // V114 moved Index Luck, Armor and Farming earlier in the skill tree.
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

    // Built from its code point so no dash character sits in this file.
    private static final String DASH = String.valueOf((char) 0x2014);

    private static final List<TextPatch> TEXT_PATCHES = List.of(
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
        if (changed) disk.set("applied-patches", applied);
        return changed;
    }
}
