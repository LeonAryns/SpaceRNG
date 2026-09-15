package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.perk.PerkInstance;
import com.spacerng.solrng.perk.PerkManager;
import com.spacerng.solrng.perk.PerkStat;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How a perk reads on a tooltip, shared by the vault, the roller and the
 * perk index. The style comes from {@code perk-style} in config and
 * {@code /rngadmin perkstyles} shows every one on hover.
 *
 * Each style returns the body only (stats and level); the menu adds its
 * own footer, so an equipped perk and an index entry can share a look.
 */
public final class PerkLore {

    public static final List<String> PERK_STYLES = List.of("card", "classic", "compact", "detailed", "meter");

    private PerkLore() { }

    public static String style(SolRNGPlugin plugin) {
        String raw = plugin.getConfig().getString("perk-style", "card").toLowerCase(Locale.ROOT);
        return PERK_STYLES.contains(raw) ? raw : "card";
    }

    /** 「 Legendary Luck Perk III 」 with the tier's own colours. */
    public static String name(SolRNGPlugin plugin, PerkInstance perk) {
        return ChatColor.DARK_GRAY + "「 " + ChatColor.RESET + ChatColor.BOLD
                + plugin.getRarityManager().style(perk.tier(), perk.display())
                + ChatColor.GRAY + " " + perk.roman() + ChatColor.RESET + ChatColor.DARK_GRAY + " 」";
    }

    /** ★★★☆☆ in gold and dark grey. */
    public static String stars(int level) {
        StringBuilder out = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            out.append(i <= level ? ChatColor.GOLD : ChatColor.DARK_GRAY).append(Lore.STAR);
        }
        return out.toString();
    }

    /** A perk as a plain tooltip item: name and body, no footer. */
    public static ItemStack item(SolRNGPlugin plugin, PerkInstance perk, String style) {
        ItemStack item = new ItemStack(perk.type().icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name(plugin, perk));
        meta.setLore(body(plugin, perk, style));
        item.setItemMeta(meta);
        return item;
    }

    public static List<String> body(SolRNGPlugin plugin, PerkInstance perk, String style) {
        PerkManager perks = plugin.getPerkManager();
        var stats = perks.statsOf(perk);
        List<String> lore = new ArrayList<>();
        switch (style) {
            case "classic" -> {
                lore.add(Lore.section(perk.type().colour(), perk.type().label()));
                for (var entry : stats.entrySet()) {
                    PerkStat stat = entry.getKey();
                    lore.add(Lore.stat(stat.colour(), stat.label(), stat.format(entry.getValue())));
                }
                lore.add("");
                lore.add(Lore.section(ChatColor.AQUA, "Details"));
                lore.add(Lore.stat(ChatColor.AQUA, "Tier", perk.tier().displayName()));
                lore.add(Lore.stat(ChatColor.YELLOW, "Level", perk.roman()));
            }
            case "compact" -> {
                for (var entry : stats.entrySet()) {
                    PerkStat stat = entry.getKey();
                    lore.add(Lore.mark(stat.colour()) + stat.colour() + stat.format(entry.getValue())
                            + " " + ChatColor.GRAY + stat.label());
                }
                lore.add(stars(perk.level()));
            }
            case "detailed" -> {
                lore.add(Lore.line(perk.type().colour(), perk.type().description()));
                lore.add("");
                PerkInstance maxed = new PerkInstance(perk.id(), perk.type(), perk.tier(), 5);
                for (var entry : stats.entrySet()) {
                    PerkStat stat = entry.getKey();
                    lore.add(Lore.stat(stat.colour(), stat.label(), stat.format(entry.getValue())));
                    lore.add("  " + ChatColor.DARK_GRAY + stat.description());
                    if (perk.level() < 5) {
                        lore.add("  " + ChatColor.DARK_GRAY + "At level V: " + ChatColor.GRAY
                                + stat.format(perks.valueOf(maxed, stat)));
                    }
                }
                lore.add("");
                lore.add(Lore.stat(ChatColor.YELLOW, "Level", perk.roman() + " of V  " + stars(perk.level())));
                lore.add(Lore.stat(ChatColor.AQUA, "Stats", stats.size() + " of 4"));
            }
            case "meter" -> {
                for (var entry : stats.entrySet()) {
                    PerkStat stat = entry.getKey();
                    double ceiling = perks.ceilingOf(stat);
                    double fraction = ceiling <= 0.0 ? 0.0 : entry.getValue() / ceiling;
                    lore.add(Lore.stat(stat.colour(), stat.label(), stat.format(entry.getValue())));
                    lore.add("  " + Lore.bar(fraction) + ChatColor.DARK_GRAY + " "
                            + Math.round(fraction * 100) + "% of a Divine V");
                }
                lore.add("");
                lore.add(Lore.stat(ChatColor.YELLOW, "Level", stars(perk.level())));
            }
            default -> { // card
                lore.add(Lore.line(perk.type().colour(), perk.type().description()));
                lore.add("");
                for (var entry : stats.entrySet()) {
                    PerkStat stat = entry.getKey();
                    lore.add(Lore.mark(stat.colour()) + stat.colour() + ChatColor.BOLD + stat.format(entry.getValue())
                            + ChatColor.RESET + " " + ChatColor.GRAY + stat.label());
                    lore.add("  " + ChatColor.DARK_GRAY + stat.description());
                }
                lore.add("");
                lore.add(Lore.stat(ChatColor.YELLOW, "Level", perk.roman() + "  " + stars(perk.level())));
            }
        }
        return lore;
    }

    /** Sums every equipped perk into one tooltip: the loadout at a glance. */
    public static ItemStack loadoutSummary(SolRNGPlugin plugin, com.spacerng.solrng.player.PlayerData data) {
        PerkManager perks = plugin.getPerkManager();
        ItemStack item = new ItemStack(org.bukkit.Material.BEACON);
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
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
