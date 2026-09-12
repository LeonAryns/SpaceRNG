package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.perk.PerkManager;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Four big crystal pillars, one per Roll tier. Each shows what a roll
 * costs, what it can produce, and lets you fire it. The vault opens
 * from the left crystal.
 */
public class PerkRollerGui {

    private static final int SIZE = 45;
    private static final int VAULT_SLOT = 4;
    private static final Map<Rarity, Integer> BUTTON_SLOT = new EnumMap<>(Rarity.class);
    private static final Map<Rarity, Material> BUTTON_ICON = new EnumMap<>(Rarity.class);

    static {
        BUTTON_SLOT.put(Rarity.EPIC, 20);
        BUTTON_SLOT.put(Rarity.LEGENDARY, 22);
        BUTTON_SLOT.put(Rarity.MYTHICAL, 24);
        BUTTON_SLOT.put(Rarity.DIVINE, 30);
        BUTTON_ICON.put(Rarity.EPIC, Material.AMETHYST_CLUSTER);
        BUTTON_ICON.put(Rarity.LEGENDARY, Material.END_CRYSTAL);
        BUTTON_ICON.put(Rarity.MYTHICAL, Material.NETHERITE_INGOT);
        BUTTON_ICON.put(Rarity.DIVINE, Material.DRAGON_HEAD);
    }

    public static NamespacedKey rollTierKey(SolRNGPlugin plugin) {
        return SolRNGPlugin.key("solrng_roll_tier");
    }

    public static int vaultSlot() { return VAULT_SLOT; }

    public static Map<Rarity, Integer> buttonSlots() { return BUTTON_SLOT; }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        PerkRollerHolder holder = new PerkRollerHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Perk Roller");
        holder.setInventory(inv);

        // Background: a subtle constellation of colored panes on top and
        // bottom rows so this menu never reads as any other in the plugin.
        ItemStack magenta = pane(Material.MAGENTA_STAINED_GLASS_PANE);
        ItemStack purple = pane(Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack pink = pane(Material.PINK_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) inv.setItem(i, i % 2 == 0 ? magenta : purple);
        for (int i = 36; i < 45; i++) inv.setItem(i, i % 2 == 0 ? purple : magenta);
        for (int i = 9; i < 36; i++) inv.setItem(i, pink);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        PerkManager perks = plugin.getPerkManager();

        inv.setItem(VAULT_SLOT, vaultLinkIcon(data));

        for (Map.Entry<Rarity, Integer> entry : BUTTON_SLOT.entrySet()) {
            Rarity tier = entry.getKey();
            var roll = perks.getRoll(tier);
            if (roll == null) continue;
            inv.setItem(entry.getValue(), rollButton(plugin, data, tier, roll));
        }

        return inv;
    }

    private static ItemStack rollButton(SolRNGPlugin plugin, PlayerData data, Rarity tier,
                                        PerkManager.RollTier roll) {
        Material material = BUTTON_ICON.getOrDefault(tier, Material.NETHER_STAR);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String tierName = plugin.getRarityManager().style(tier, tier.displayName() + " Roll");
        meta.setDisplayName(ChatColor.DARK_GRAY + "「 " + ChatColor.RESET
                + ChatColor.BOLD + tierName + ChatColor.RESET + ChatColor.DARK_GRAY + " 」");

        long have = data.getBankedDrops(roll.costRarity())
                + countPhysical(plugin, data, roll.costRarity());
        boolean affordable = have >= roll.costAmount();

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.AQUA, "Cost"));
        String costLabel = roll.costAmount() + "x "
                + plugin.getRarityManager().style(roll.costRarity(), roll.costRarity().displayName());
        lore.add((affordable ? ChatColor.YELLOW : ChatColor.RED) + Lore.BULLET + " "
                + ChatColor.GRAY + "Drops: " + costLabel);
        lore.add(Lore.stat(ChatColor.DARK_GRAY, "You have", have + " (bank + inventory)"));
        lore.add("");
        lore.add(Lore.section(ChatColor.LIGHT_PURPLE, "Rolls at"));
        for (Rarity r : Rarity.values()) {
            double chance = roll.tierChances().getOrDefault(r, 0.0);
            if (chance <= 0.0) continue;
            String pct = chance >= 0.01 ? String.format("%.1f%%", chance * 100)
                                        : String.format("%.2f%%", chance * 100);
            lore.add(ChatColor.GRAY + Lore.BULLET + " "
                    + plugin.getRarityManager().style(r, r.displayName())
                    + ChatColor.DARK_GRAY + " - " + ChatColor.WHITE + pct);
        }
        lore.add("");
        if (affordable) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to roll");
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Not enough drops");
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(rollTierKey(plugin), PersistentDataType.STRING, tier.name());
        item.setItemMeta(meta);
        return item;
    }

    private static long countPhysical(SolRNGPlugin plugin, PlayerData data, Rarity rarity) {
        var player = Bukkit.getPlayer(data.getUuid());
        if (player == null) return 0L;
        var key = plugin.getRollListener().getRarityKey();
        long total = 0L;
        for (var stack : player.getInventory().getContents()) {
            if (stack == null || stack.getItemMeta() == null) continue;
            String name = stack.getItemMeta().getPersistentDataContainer()
                    .get(key, PersistentDataType.STRING);
            if (name == null) continue;
            try {
                if (Rarity.valueOf(name) == rarity) total += stack.getAmount();
            } catch (IllegalArgumentException ignored) { }
        }
        return total;
    }

    private static ItemStack vaultLinkIcon(PlayerData data) {
        ItemStack item = new ItemStack(Material.ENDER_CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Perk Vault"));
        int equipped = data.getEquippedPerks().size();
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "See and equip your perks."),
                Lore.stat(ChatColor.GREEN, "Equipped", String.valueOf(equipped)),
                Lore.stat(ChatColor.AQUA, "In vault", String.valueOf(data.getPerkVault().size())),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to open"));
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
