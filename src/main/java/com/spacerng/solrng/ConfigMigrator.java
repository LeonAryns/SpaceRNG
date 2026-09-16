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
            "skilltree", "farmtree", "shiny", "perks", "linked-account");

    /**
     * Sections copied from the jar whenever the server's config has none
     * yet. A dotted path works too, for a new entry inside a section the
     * server already has, like one more hologram panel.
     */
    private static final List<String> ADDED_SECTIONS = List.of("discord", "holograms",
            "holograms.panels.armor", "holograms.panels.starforge", "holograms.panels.potion",
            "holograms.panels.convert", "holograms.panels.pass", "holograms.panels.store",
            "holograms.panels.novacore", "holograms.panels.perks", "holograms.panels.index",
            "holograms.panels.farmtree", "holograms.panels.daily", "holograms.panels.leaderboards",
            "holograms.panels.stash",
            // V132: a Vote and a Nebula crate, with their keys.
            "crates.types.vote", "crates.types.nebula", "consumables.vote_key", "consumables.nebula_key",
            // V135: Credits on some milestone tiers.
            "milestones.credit-rewards",
            // V137: ranks.
            "ranks",
            // V138: what one Credit Finder proc pays.
            "farming.procs.credit-finder-amount");

    private record Patch(String id, String path, Object oldDefault, Object newDefault) {
    }

    private static final List<Patch> PATCHES = List.of(
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
            new Patch("cosmic-key-source-store", "crates.types.cosmic.key-source",
                    "Cosmic Keys are the rare find from Key Finder, about one key in twelve.",
                    "Cosmic Keys come from the store."));

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
            if (!disk.contains(section) && defaults.contains(section)) {
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
        for (Patch patch : PATCHES) {
            if (applied.contains(patch.id())) continue;
            if (Objects.equals(disk.get(patch.path()), patch.oldDefault())) {
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
