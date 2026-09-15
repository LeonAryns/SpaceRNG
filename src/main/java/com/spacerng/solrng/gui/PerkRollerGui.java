package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.perk.PerkManager;
import com.spacerng.solrng.perk.PerkType;
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
 * Four roll buttons in a row, one per Roll tier. Each shows what a roll
 * costs, what it can produce and how many new index entries it could
 * still give you. The top rail holds how perks work, the vault and the
 * perk index; the bottom rail shows what your loadout adds up to.
 */
public class PerkRollerGui {

    private static final int SIZE = 45;
    private static final int GUIDE_SLOT = 2;
    private static final int VAULT_SLOT = 4;
    private static final int INDEX_SLOT = 6;
    private static final int LOADOUT_SLOT = 40;
    private static final Map<Rarity, Integer> BUTTON_SLOT = new EnumMap<>(Rarity.class);
    private static final Map<Rarity, Material> BUTTON_ICON = new EnumMap<>(Rarity.class);

    static {
        BUTTON_SLOT.put(Rarity.EPIC, 19);
        BUTTON_SLOT.put(Rarity.LEGENDARY, 21);
        BUTTON_SLOT.put(Rarity.MYTHICAL, 23);
        BUTTON_SLOT.put(Rarity.DIVINE, 25);
        BUTTON_ICON.put(Rarity.EPIC, Material.AMETHYST_CLUSTER);
        BUTTON_ICON.put(Rarity.LEGENDARY, Material.END_CRYSTAL);
        BUTTON_ICON.put(Rarity.MYTHICAL, Material.NETHERITE_INGOT);
        BUTTON_ICON.put(Rarity.DIVINE, Material.DRAGON_HEAD);
    }

    public static NamespacedKey rollTierKey(SolRNGPlugin plugin) {
        return SolRNGPlugin.key("solrng_roll_tier");
    }

    public static int vaultSlot() { return VAULT_SLOT; }
    public static int indexSlot() { return INDEX_SLOT; }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        PerkRollerHolder holder = new PerkRollerHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Perk Roller");
        holder.setInventory(inv);

        // Magenta and purple rails top and bottom so this menu never reads
        // as any other in the plugin; a dark middle so the buttons stand out.
        ItemStack magenta = pane(Material.MAGENTA_STAINED_GLASS_PANE);
        ItemStack purple = pane(Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) inv.setItem(i, i % 2 == 0 ? magenta : purple);
        for (int i = 36; i < 45; i++) inv.setItem(i, i % 2 == 0 ? purple : magenta);
        for (int i = 9; i < 36; i++) inv.setItem(i, filler);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        PerkManager perks = plugin.getPerkManager();

        inv.setItem(GUIDE_SLOT, guideIcon(plugin));
        inv.setItem(VAULT_SLOT, vaultLinkIcon(data));
        inv.setItem(INDEX_SLOT, PerkVaultGui.indexLinkIcon(data));
        inv.setItem(LOADOUT_SLOT, PerkLore.loadoutSummary(plugin, data));

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
        PerkManager perks = plugin.getPerkManager();
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
        lore.add(Lore.line(affordable ? ChatColor.YELLOW : ChatColor.RED, roll.costAmount() + "x "
                + plugin.getRarityManager().style(roll.costRarity(), roll.costRarity().displayName())
                + ChatColor.GRAY + " drops"));
        lore.add(Lore.stat(ChatColor.DARK_GRAY, "You have", have + " (bank and inventory)"));
        lore.add("");
        lore.add(Lore.section(ChatColor.LIGHT_PURPLE, "The perk lands at"));
        int minStats = Integer.MAX_VALUE;
        int maxStats = 0;
        int newForIndex = 0;
        int possible = 0;
        for (Rarity r : Rarity.values()) {
            double chance = roll.tierChances().getOrDefault(r, 0.0);
            if (chance <= 0.0) continue;
            minStats = Math.min(minStats, perks.statCountFor(r));
            maxStats = Math.max(maxStats, perks.statCountFor(r));
            for (PerkType type : PerkType.values()) {
                possible++;
                if (data.bestPerkLevel(type, r) == 0) newForIndex++;
            }
            lore.add(Lore.mark(ChatColor.GRAY) + plugin.getRarityManager().style(r, r.displayName())
                    + ChatColor.DARK_GRAY + "  " + ChatColor.WHITE
                    + PerkIndexGui.percent(perks.chanceOf(tier, r) * PerkType.values().length));
        }
        lore.add("");
        if (maxStats > 0) {
            lore.add(Lore.stat(ChatColor.AQUA, "Stats per perk",
                    minStats == maxStats ? String.valueOf(maxStats) : minStats + " to " + maxStats));
        }
        lore.add(Lore.stat(newForIndex > 0 ? ChatColor.GREEN : ChatColor.DARK_GRAY,
                "New for your index", newForIndex + " of " + possible));
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

    /** How perks work, in one tooltip: tiers, levels and the five types. */
    private static ItemStack guideIcon(SolRNGPlugin plugin) {
        PerkManager perks = plugin.getPerkManager();
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.AQUA, "How Perks Work"));
        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, "Trade drops for a random perk."));
        lore.add(Lore.line(ChatColor.GRAY, "Equip up to " + perks.loadoutSlots() + " in the vault."));
        lore.add("");
        lore.add(Lore.section(ChatColor.LIGHT_PURPLE, "Tier"));
        lore.add(Lore.line(ChatColor.LIGHT_PURPLE, "Higher tiers give bigger stats"));
        lore.add(Lore.line(ChatColor.LIGHT_PURPLE, "and more of them."));
        lore.add("");
        lore.add(Lore.section(ChatColor.YELLOW, "Level"));
        StringBuilder levels = new StringBuilder();
        String[] roman = {"I", "II", "III", "IV", "V"};
        for (int level = 1; level <= 5; level++) {
            if (level > 1) levels.append(ChatColor.DARK_GRAY).append("  ");
            levels.append(ChatColor.GRAY).append(roman[level - 1]).append(" ")
                    .append(ChatColor.WHITE).append(Math.round(perks.levelChance(level) * 100)).append("%");
        }
        lore.add(Lore.mark(ChatColor.YELLOW) + levels);
        lore.add(Lore.line(ChatColor.YELLOW, "Level V is five times level I."));
        lore.add("");
        lore.add(Lore.section(ChatColor.GREEN, "Types"));
        for (PerkType type : PerkType.values()) {
            lore.add(Lore.mark(type.colour()) + type.colour() + type.label()
                    + ChatColor.DARK_GRAY + "  " + ChatColor.GRAY + type.description());
        }
        meta.setLore(lore);
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
