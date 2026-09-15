package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.perk.PerkManager;
import com.spacerng.solrng.perk.PerkType;
import com.spacerng.solrng.player.PlayerData;
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
 * /perks, laid out like Leon's reference: Pity Luck at the top, the perk
 * index on the left, your perk in the middle and Roll Perk on the right,
 * with the Perk Ticket bundles along the bottom and your ticket count
 * beside them.
 */
public class PerkRollerGui {

    private static final int SIZE = 45;
    private static final int PITY_SLOT = 4;
    private static final int INDEX_SLOT = 19;
    private static final int PERK_SLOT = 22;
    private static final int ROLL_SLOT = 25;
    private static final int[] BUY_SLOTS = {39, 40, 41};
    private static final int TICKET_SLOT = 43;

    public static int indexSlot() { return INDEX_SLOT; }
    public static int rollSlot() { return ROLL_SLOT; }

    /** How many tickets a bottom-row slot sells, or null when it isn't a bundle. */
    public static Integer buyAmountAt(SolRNGPlugin plugin, int slot) {
        List<Integer> amounts = new ArrayList<>(plugin.getPerkManager().ticketPrices().keySet());
        for (int i = 0; i < BUY_SLOTS.length && i < amounts.size(); i++) {
            if (BUY_SLOTS[i] == slot) return amounts.get(i);
        }
        return null;
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        PerkRollerHolder holder = new PerkRollerHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Perks");
        holder.setInventory(inv);

        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        PerkManager perks = plugin.getPerkManager();

        inv.setItem(PITY_SLOT, pityIcon(perks, data));
        inv.setItem(INDEX_SLOT, indexIcon(perks, data));
        inv.setItem(PERK_SLOT, PerkLore.activeItem(plugin, data));
        inv.setItem(ROLL_SLOT, rollIcon(plugin, data));
        int i = 0;
        for (var bundle : perks.ticketPrices().entrySet()) {
            if (i >= BUY_SLOTS.length) break;
            inv.setItem(BUY_SLOTS[i++], buyIcon(data, bundle.getKey(), bundle.getValue()));
        }
        inv.setItem(TICKET_SLOT, ticketIcon(data));
        return inv;
    }

    private static ItemStack pityIcon(PerkManager perks, PlayerData data) {
        int pity = Math.min(data.getPerkPity(), perks.pityRolls());
        ItemStack item = new ItemStack(Material.AMETHYST_CLUSTER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Pity Luck"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, perks.pityRolls() + " rolls in a row without a Mythical"),
                Lore.line(ChatColor.GRAY, "make the next one Mythical or Divine."),
                "",
                Lore.stat(ChatColor.LIGHT_PURPLE, "Rolls without one", pity + " / " + perks.pityRolls()),
                "  " + Lore.bar((double) pity / perks.pityRolls()),
                Lore.stat(ChatColor.GOLD, "Guaranteed within", Math.max(1, perks.pityRolls() - pity) + " rolls")));
        if (pity >= perks.pityRolls() - 1) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack indexIcon(PerkManager perks, PlayerData data) {
        int total = perks.types().size() * 5;
        int found = 0;
        for (PerkType type : perks.types()) {
            for (int level = 1; level <= 5; level++) {
                if (data.getPerkFound().contains(type.key(level))) found++;
            }
        }
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.AQUA, "Perk Index"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Every perk and what each level gives."),
                Lore.line(ChatColor.GRAY, "Switch on confirmation for the ones"),
                Lore.line(ChatColor.GRAY, "you never want to roll away."),
                "",
                Lore.stat(ChatColor.GREEN, "Found", found + " / " + total),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to open"));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack rollIcon(SolRNGPlugin plugin, PlayerData data) {
        PerkManager perks = plugin.getPerkManager();
        ItemStack item = new ItemStack(Material.SLIME_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GREEN, "Roll Perk"));
        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, "Rolls a new perk and replaces yours."));
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "Cost", "1 Perk Ticket"));
        lore.add(Lore.stat(ChatColor.AQUA, "You have", String.format("%,d", data.getPerkTickets())));
        lore.add("");
        lore.add(Lore.section(ChatColor.LIGHT_PURPLE, "Chances"));
        for (PerkType type : perks.types()) {
            lore.add(Lore.mark(ChatColor.GRAY) + plugin.getRarityManager().style(type.rarity(), type.display())
                    + ChatColor.DARK_GRAY + "  " + ChatColor.WHITE + PerkIndexGui.percent(perks.chanceOf(type)));
        }
        StringBuilder levels = new StringBuilder();
        for (int level = 1; level <= 5; level++) {
            if (level > 1) levels.append("  ");
            levels.append(ChatColor.GRAY).append(PerkType.roman(level)).append(" ")
                    .append(ChatColor.WHITE).append(PerkIndexGui.percent(perks.levelChance(level)));
        }
        lore.add(Lore.mark(ChatColor.YELLOW) + levels);
        lore.add("");
        PerkType current = perks.activeType(data);
        if (current != null && data.getPerkConfirm().contains(current.key(data.getActivePerkLevel()))) {
            lore.add(Lore.line(ChatColor.GOLD, "Your perk asks before it is replaced."));
        }
        if (data.getPerkTickets() < 1) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Not enough Perk Tickets");
            lore.add(Lore.line(ChatColor.GRAY, "Buy them along the bottom."));
        } else {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to roll");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buyIcon(PlayerData data, int amount, long price) {
        boolean affordable = data.getPoints() >= price;
        ItemStack item = new ItemStack(Material.ENDER_EYE, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "" + ChatColor.BOLD + amount + "x " + ChatColor.DARK_GRAY + "| "
                + Lore.rainbow("Perk Ticket"));
        meta.setLore(List.of(
                ChatColor.GRAY + "Running low on Perk Tickets? Buy",
                ChatColor.WHITE + "" + amount + "x Perk Tickets " + ChatColor.GRAY + "for "
                        + Currency.CREDITS.price(price, affordable) + ChatColor.GRAY + ".",
                "",
                affordable ? ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to buy"
                        : ChatColor.RED + "" + ChatColor.BOLD + "Not enough Credits"));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack ticketIcon(PlayerData data) {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GREEN, "Your Perk Tickets"));
        meta.setLore(List.of(
                Lore.stat(ChatColor.GREEN, "Perk Tickets", String.format("%,d", data.getPerkTickets())),
                Lore.stat(ChatColor.LIGHT_PURPLE, "Credits", Currency.CREDITS.amount(data.getPoints())),
                "",
                Lore.footnote("One ticket is one roll.")));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }
}
