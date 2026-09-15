package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.perk.PerkInstance;
import com.spacerng.solrng.perk.PerkManager;
import com.spacerng.solrng.perk.PerkStat;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * How a perk reads on a tooltip, shared by the vault, the roller and the
 * perk index. The style comes from {@code perk-style} in config and
 * {@code /rngadmin perkstyles} shows every one on hover.
 *
 * Each style returns the body only (stats and range); the menu adds its
 * own footer, so an equipped perk and an index entry can share a look.
 */
public final class PerkLore {

    public static final List<String> PERK_STYLES = List.of("card", "classic", "compact", "detailed", "meter");

    private PerkLore() { }

    public static String style(SolRNGPlugin plugin) {
        String raw = plugin.getConfig().getString("perk-style", "card").toLowerCase(Locale.ROOT);
        return PERK_STYLES.contains(raw) ? raw : "card";
    }

    /** 「 Legendary Perk 」 in the tier's own colours. */
    public static String name(SolRNGPlugin plugin, PerkInstance perk) {
        return ChatColor.DARK_GRAY + "「 " + ChatColor.RESET + ChatColor.BOLD
                + plugin.getRarityManager().style(perk.tier(), perk.display())
                + ChatColor.RESET + ChatColor.DARK_GRAY + " 」";
    }

    /** "1.37x Luck, 1.2x Money" for chat lines. */
    public static String shortStats(PerkInstance perk) {
        StringJoiner out = new StringJoiner(ChatColor.DARK_GRAY + ", ");
        perk.stats().forEach((stat, bonus) ->
                out.add(stat.colour() + stat.format(bonus) + " " + ChatColor.GRAY + stat.label()));
        return out.toString();
    }

    /** "1.3x to 2.5x" for a tier. */
    public static String rangeText(PerkManager perks, com.spacerng.solrng.rarity.Rarity tier) {
        double[] range = perks.rangeOf(tier);
        return PerkStat.multiplier(1.0 + range[0]) + " to " + PerkStat.multiplier(1.0 + range[1]);
    }

    /** A perk as a plain tooltip item: name and body, no footer. */
    public static ItemStack item(SolRNGPlugin plugin, PerkInstance perk, String style) {
        PerkStat strongest = perk.strongest();
        ItemStack item = new ItemStack(strongest == null ? Material.NETHER_STAR : strongest.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name(plugin, perk));
        meta.setLore(body(plugin, perk, style));
        item.setItemMeta(meta);
        return item;
    }

    public static List<String> body(SolRNGPlugin plugin, PerkInstance perk, String style) {
        PerkManager perks = plugin.getPerkManager();
        String range = rangeText(perks, perk.tier());
        List<String> lore = new ArrayList<>();
        switch (style) {
            case "classic" -> {
                lore.add(Lore.section(ChatColor.AQUA, "Stats"));
                for (var entry : perk.stats().entrySet()) {
                    PerkStat stat = entry.getKey();
                    lore.add(Lore.stat(stat.colour(), stat.label(), stat.format(entry.getValue())));
                }
                lore.add("");
                lore.add(Lore.section(ChatColor.AQUA, "Details"));
                lore.add(Lore.stat(ChatColor.AQUA, "Tier", perk.tier().displayName()));
                lore.add(Lore.stat(ChatColor.AQUA, "Range", range));
            }
            case "compact" -> {
                for (var entry : perk.stats().entrySet()) {
                    PerkStat stat = entry.getKey();
                    lore.add(Lore.mark(stat.colour()) + stat.colour() + stat.format(entry.getValue())
                            + " " + ChatColor.GRAY + stat.label());
                }
            }
            case "detailed" -> {
                for (var entry : perk.stats().entrySet()) {
                    PerkStat stat = entry.getKey();
                    long roll = Math.round(perks.quality(perk.tier(), entry.getValue()) * 100);
                    lore.add(Lore.stat(stat.colour(), stat.label(), stat.format(entry.getValue())));
                    lore.add("  " + ChatColor.DARK_GRAY + stat.description());
                    lore.add("  " + ChatColor.DARK_GRAY + "Rolled " + ChatColor.GRAY + roll + "%"
                            + ChatColor.DARK_GRAY + " of the way up its range");
                }
                lore.add("");
                lore.add(Lore.stat(ChatColor.AQUA, "Range", range));
                lore.add(Lore.stat(ChatColor.AQUA, "Stats", String.valueOf(perk.stats().size())));
            }
            case "meter" -> {
                for (var entry : perk.stats().entrySet()) {
                    PerkStat stat = entry.getKey();
                    double quality = perks.quality(perk.tier(), entry.getValue());
                    lore.add(Lore.stat(stat.colour(), stat.label(), stat.format(entry.getValue())));
                    lore.add("  " + Lore.bar(quality) + ChatColor.DARK_GRAY + " "
                            + Math.round(quality * 100) + "% of the range");
                }
                lore.add("");
                lore.add(Lore.stat(ChatColor.AQUA, "Range", range));
            }
            default -> { // card
                for (var entry : perk.stats().entrySet()) {
                    PerkStat stat = entry.getKey();
                    lore.add(Lore.mark(stat.colour()) + stat.colour() + ChatColor.BOLD + stat.format(entry.getValue())
                            + ChatColor.RESET + " " + ChatColor.GRAY + stat.label());
                    lore.add("  " + ChatColor.DARK_GRAY + stat.description());
                }
                lore.add("");
                lore.add(Lore.stat(ChatColor.AQUA, "Range", range));
            }
        }
        return lore;
    }

    /** Sums every equipped perk into one tooltip: the loadout at a glance. */
    public static ItemStack loadoutSummary(SolRNGPlugin plugin, com.spacerng.solrng.player.PlayerData data) {
        PerkManager perks = plugin.getPerkManager();
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Your Loadout"));
        List<String> lore = new ArrayList<>();
        lore.add(Lore.stat(ChatColor.AQUA, "Equipped",
                data.getEquippedPerks().size() + " / " + perks.loadoutSlots()));
        lore.add("");
        boolean any = false;
        for (PerkStat stat : PerkStat.values()) {
            double total = perks.totalOf(data, stat);
            if (total <= 0.0) continue;
            if (!any) lore.add(Lore.section(ChatColor.GREEN, "All perks together"));
            any = true;
            lore.add(Lore.mark(stat.colour()) + stat.colour() + stat.format(total) + " " + ChatColor.GRAY + stat.label());
        }
        if (!any) {
            lore.add(Lore.line(ChatColor.GRAY, "Nothing equipped yet."));
            lore.add(Lore.footnote("Equip a perk in the vault."));
        } else {
            lore.add("");
            lore.add(Lore.footnote("The same stat on two perks adds up."));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
