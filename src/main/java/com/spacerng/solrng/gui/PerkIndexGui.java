package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.perk.PerkManager;
import com.spacerng.solrng.perk.PerkStat;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Every stat a perk can roll, one row per stat and one column per tier,
 * and which of them this player has rolled. A found cell shows the best
 * roll so far and where it sits in the range; an unfound one says which
 * rolls can give it. The right-hand column links back to the roller and
 * the vault.
 *
 * Found is remembered apart from the vault, so discarding a perk never
 * takes it out of the index.
 */
public class PerkIndexGui {

    private static final int SIZE = 54;
    private static final int BACK_SLOT = 8;
    private static final int PROGRESS_SLOT = 17;
    private static final int VAULT_SLOT = 26;
    // A roll this close to the top of its range counts as perfect.
    private static final double PERFECT = 0.95;

    public static int backSlot() { return BACK_SLOT; }
    public static int vaultSlot() { return VAULT_SLOT; }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        PerkIndexHolder holder = new PerkIndexHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Perk Index");
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        PerkManager perks = plugin.getPerkManager();

        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack rail = pane(Material.MAGENTA_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, i % 9 == 8 ? rail : filler);

        List<PerkStat> pool = perks.pool();
        Rarity[] tiers = Rarity.values();
        int rows = Math.min(6, pool.size());
        int columns = Math.min(7, tiers.length);
        int found = 0;
        int perfect = 0;
        for (int row = 0; row < rows; row++) {
            PerkStat stat = pool.get(row);
            int base = row * 9;
            int statFound = 0;
            for (int col = 0; col < columns; col++) {
                Rarity tier = tiers[col];
                double best = data.bestPerkValue(stat, tier);
                if (best > 0.0) {
                    found++;
                    statFound++;
                    if (perks.quality(tier, best) >= PERFECT) perfect++;
                }
                inv.setItem(base + 1 + col, best > 0.0
                        ? foundIcon(plugin, stat, tier, best)
                        : unfoundIcon(plugin, stat, tier));
            }
            inv.setItem(base, statIcon(plugin, stat, statFound, columns));
        }

        int total = rows * columns;
        inv.setItem(BACK_SLOT, link(Material.SPECTRAL_ARROW, "Perk Roller", "Trade drops for perks."));
        inv.setItem(PROGRESS_SLOT, progressIcon(found, perfect, total));
        inv.setItem(VAULT_SLOT, link(Material.ENDER_CHEST, "Perk Vault", "See and equip your perks."));
        return inv;
    }

    private static String cellName(SolRNGPlugin plugin, PerkStat stat, Rarity tier) {
        return plugin.getRarityManager().style(tier, tier.displayName() + " " + stat.label());
    }

    private static ItemStack foundIcon(SolRNGPlugin plugin, PerkStat stat, Rarity tier, double best) {
        PerkManager perks = plugin.getPerkManager();
        double quality = perks.quality(tier, best);
        ItemStack item = new ItemStack(stat.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_GRAY + "「 " + ChatColor.RESET + ChatColor.BOLD
                + cellName(plugin, stat, tier) + ChatColor.RESET + ChatColor.DARK_GRAY + " 」");
        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(stat.colour(), stat.description()));
        lore.add("");
        lore.add(Lore.stat(stat.colour(), "Best roll", stat.format(best)));
        lore.add("  " + Lore.bar(quality) + ChatColor.DARK_GRAY + " " + Math.round(quality * 100) + "%");
        lore.add(Lore.stat(ChatColor.AQUA, "Range", PerkLore.rangeText(perks, tier)));
        lore.add("");
        if (quality >= PERFECT) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Found, a perfect roll");
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        } else {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Found");
            lore.add(Lore.footnote("Your best so far. A higher roll can still land."));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack unfoundIcon(SolRNGPlugin plugin, PerkStat stat, Rarity tier) {
        PerkManager perks = plugin.getPerkManager();
        ItemStack item = new ItemStack(Material.STONE_BUTTON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_GRAY + "???");
        List<String> lore = new ArrayList<>();
        lore.add(Lore.mark(ChatColor.DARK_GRAY) + cellName(plugin, stat, tier));
        lore.add(Lore.stat(ChatColor.AQUA, "Range", PerkLore.rangeText(perks, tier)));
        lore.add("");
        boolean anyRoll = false;
        for (Rarity rollTier : perks.getRolls().keySet()) {
            double chance = perks.chanceOf(rollTier, tier, stat);
            if (chance <= 0.0) continue;
            if (!anyRoll) lore.add(Lore.section(ChatColor.LIGHT_PURPLE, "Rolls from"));
            anyRoll = true;
            lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, rollTier.displayName() + " Roll", percent(chance)));
        }
        if (anyRoll) {
            lore.add("");
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Not found yet");
            lore.add(Lore.line(ChatColor.GRAY, "Roll it in /perks"));
        } else {
            lore.add(Lore.line(ChatColor.GRAY, "No roll gives this yet."));
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Coming soon");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack statIcon(SolRNGPlugin plugin, PerkStat stat, int statFound, int tiers) {
        PerkManager perks = plugin.getPerkManager();
        ItemStack item = new ItemStack(stat.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(stat.colour(), stat.label()));
        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(stat.colour(), stat.description()));
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "Ranges"));
        for (Rarity tier : Rarity.values()) {
            lore.add(Lore.mark(ChatColor.GRAY) + plugin.getRarityManager().style(tier, tier.displayName())
                    + ChatColor.DARK_GRAY + "  " + ChatColor.WHITE + PerkLore.rangeText(perks, tier));
        }
        lore.add("");
        lore.add(Lore.stat(ChatColor.GREEN, "Found", statFound + " / " + tiers));
        meta.setLore(lore);
        if (statFound >= tiers) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack progressIcon(int found, int perfect, int total) {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Perk Index"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Every stat at every tier."),
                "",
                Lore.stat(ChatColor.GREEN, "Found", found + " / " + total),
                "  " + Lore.bar(total == 0 ? 0.0 : (double) found / total),
                Lore.stat(ChatColor.GOLD, "Perfect rolls", perfect + " / " + total),
                "",
                Lore.footnote("A discarded perk stays found.")));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack link(Material material, String name, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, name));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, description),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to open"));
        item.setItemMeta(meta);
        return item;
    }

    /** 12.5%, 0.4%, 0.0001%: two significant digits, never scientific, never zero. */
    static String percent(double fraction) {
        double pct = fraction * 100.0;
        if (pct >= 1.0) return String.format("%.1f%%", pct).replace(".0%", "%");
        if (pct <= 0.0) return "0%";
        return new java.math.BigDecimal(pct).round(new java.math.MathContext(2))
                .stripTrailingZeros().toPlainString() + "%";
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }
}
