package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.perk.PerkInstance;
import com.spacerng.solrng.perk.PerkManager;
import com.spacerng.solrng.perk.PerkStat;
import com.spacerng.solrng.perk.PerkType;
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
import java.util.UUID;

/**
 * Every perk that exists, one row per type and one column per tier, and
 * which of them this player has rolled. A found cell shows the best
 * level so far; an unfound one says which rolls can give it.
 *
 * Found is remembered apart from the vault, so discarding a perk never
 * takes it out of the index.
 */
public class PerkIndexGui {

    private static final int SIZE = 54;
    private static final int BACK_SLOT = 0;
    private static final int PROGRESS_SLOT = 4;
    private static final int VAULT_SLOT = 8;
    private static final UUID SAMPLE_ID = new UUID(0L, 0L);

    public static int backSlot() { return BACK_SLOT; }
    public static int vaultSlot() { return VAULT_SLOT; }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        PerkIndexHolder holder = new PerkIndexHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Perk Index");
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        String style = PerkLore.style(plugin);

        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack rail = pane(Material.MAGENTA_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, i < 9 ? rail : filler);

        PerkType[] types = PerkType.values();
        Rarity[] tiers = Rarity.values();
        int found = 0;
        int maxed = 0;
        for (int row = 0; row < types.length && row < 5; row++) {
            PerkType type = types[row];
            int base = (row + 1) * 9;
            int typeFound = 0;
            for (int col = 0; col < tiers.length && col < 7; col++) {
                Rarity tier = tiers[col];
                int best = data.bestPerkLevel(type, tier);
                if (best > 0) {
                    found++;
                    typeFound++;
                    if (best >= 5) maxed++;
                }
                inv.setItem(base + 1 + col, best > 0
                        ? foundIcon(plugin, type, tier, best, style)
                        : unfoundIcon(plugin, type, tier));
            }
            inv.setItem(base, typeIcon(plugin, type, typeFound, Math.min(7, tiers.length)));
        }

        int total = Math.min(5, types.length) * Math.min(7, tiers.length);
        inv.setItem(BACK_SLOT, link(Material.SPECTRAL_ARROW, "Perk Roller", "Trade drops for perks."));
        inv.setItem(PROGRESS_SLOT, progressIcon(found, maxed, total));
        inv.setItem(VAULT_SLOT, link(Material.ENDER_CHEST, "Perk Vault", "See and equip your perks."));
        return inv;
    }

    private static ItemStack foundIcon(SolRNGPlugin plugin, PerkType type, Rarity tier, int best, String style) {
        ItemStack item = PerkLore.item(plugin, new PerkInstance(SAMPLE_ID, type, tier, best), style);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>(meta.getLore() == null ? List.of() : meta.getLore());
        lore.add("");
        lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + (best >= 5 ? "Found at level V" : "Found"));
        if (best < 5) lore.add(Lore.footnote("Your best so far. A higher level can still roll."));
        meta.setLore(lore);
        if (best >= 5) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack unfoundIcon(SolRNGPlugin plugin, PerkType type, Rarity tier) {
        PerkManager perks = plugin.getPerkManager();
        ItemStack item = new ItemStack(Material.STONE_BUTTON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_GRAY + "???");
        List<String> lore = new ArrayList<>();
        lore.add(Lore.mark(ChatColor.DARK_GRAY)
                + plugin.getRarityManager().style(tier, tier.displayName() + " " + type.label()));
        lore.add("");
        boolean anyRoll = false;
        for (Rarity rollTier : perks.getRolls().keySet()) {
            double chance = perks.chanceOf(rollTier, tier);
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
            lore.add(Lore.line(ChatColor.GRAY, "No roll gives this tier yet."));
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Coming soon");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack typeIcon(SolRNGPlugin plugin, PerkType type, int typeFound, int tiers) {
        PerkManager perks = plugin.getPerkManager();
        ItemStack item = new ItemStack(type.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(type.colour(), type.label()));
        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(type.colour(), type.description()));
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "Stats it can give"));
        List<PerkStat> pool = type.pool();
        for (int i = 0; i < pool.size(); i++) {
            PerkStat stat = pool.get(i);
            Rarity from = null;
            for (Rarity tier : Rarity.values()) {
                if (perks.statCountFor(tier) > i) {
                    from = tier;
                    break;
                }
            }
            lore.add(Lore.line(stat.colour(), stat.label()
                    + (from == null ? ChatColor.DARK_GRAY + ", never" : ChatColor.DARK_GRAY + ", from "
                    + plugin.getRarityManager().style(from, from.displayName()))));
            lore.add("  " + ChatColor.DARK_GRAY + stat.description());
        }
        lore.add("");
        lore.add(Lore.stat(ChatColor.GREEN, "Found", typeFound + " / " + tiers));
        meta.setLore(lore);
        if (typeFound >= tiers) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack progressIcon(int found, int maxed, int total) {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Perk Index"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Every perk you can roll."),
                "",
                Lore.stat(ChatColor.GREEN, "Found", found + " / " + total),
                "  " + Lore.bar(total == 0 ? 0.0 : (double) found / total),
                Lore.stat(ChatColor.GOLD, "At level V", maxed + " / " + total),
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

    /** 12.5%, 0.40%, 0.004%: enough digits to never read as zero. */
    static String percent(double fraction) {
        double pct = fraction * 100.0;
        if (pct >= 1.0) return String.format("%.1f%%", pct);
        if (pct >= 0.1) return String.format("%.2f%%", pct);
        return String.format("%.3f%%", pct);
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }
}
