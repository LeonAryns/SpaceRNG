package com.spacerng.solrng.farming;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Fast-regrow farm crops: fully-grown crops harvested via FarmingListener
 * pay Tokens instead of just dropping their vanilla item, then snap
 * straight back to fully grown after a short delay (no waiting on random
 * tick growth). Everyone harvests the same field — reward is scaled per
 * player by {@link com.spacerng.solrng.player.PlayerData#getFarmTokenMultiplier()}.
 * Harvesting requires the "farming_unlock" skill tree node, which also
 * grants the {@link #createBoundHoe()} item.
 */
public class FarmingManager {

    private final SolRNGPlugin plugin;
    private final NamespacedKey boundKey;
    private final Map<Material, Long> cropTokens = new EnumMap<>(Material.class);
    private int regrowTicks = 60;

    public FarmingManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.boundKey = new NamespacedKey(plugin, "solrng_bound_hoe");
    }

    public void load(FileConfiguration config) {
        cropTokens.clear();
        regrowTicks = config.getInt("farming.regrow-seconds", 3) * 20;

        ConfigurationSection section = config.getConfigurationSection("farming.crops");
        if (section == null) {
            plugin.getLogger().info("[SolRNG] Loaded 0 farming crop types.");
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                Material material = Material.valueOf(key.toUpperCase());
                cropTokens.put(material, section.getLong(key + ".tokens", 1L));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[SolRNG] Skipped unknown farming crop material '" + key + "'.");
            }
        }
        plugin.getLogger().info("[SolRNG] Loaded " + cropTokens.size() + " farming crop types.");
    }

    public boolean isCrop(Material material) {
        return cropTokens.containsKey(material);
    }

    public long tokensFor(Material material) {
        return cropTokens.getOrDefault(material, 0L);
    }

    public int getRegrowTicks() {
        return regrowTicks;
    }

    /** Reward for unlocking "farming_unlock" — soulbound via {@link #isBoundHoe}. */
    public ItemStack createBoundHoe() {
        return createBoundHoe(null);
    }

    /**
     * The hoe, with whatever enchants the owner's farming tree currently
     * justifies written into its lore. Passing null gives the plain item —
     * the enchants are derived, never stored, so a fresh copy is always
     * accurate.
     */
    public ItemStack createBoundHoe(com.spacerng.solrng.player.PlayerData data) {
        ItemStack hoe = new ItemStack(Material.WOODEN_HOE);
        ItemMeta meta = hoe.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Farmer's Hoe " + ChatColor.DARK_GRAY + "["
                + ChatColor.YELLOW + "I" + ChatColor.DARK_GRAY + "]");

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "Farming Tool");
        lore.add("");

        var enchants = plugin.getHoeEnchantManager();
        double tokenBonus = data == null ? 0.0 : enchants.powerOf(data, "TOKEN_GREED");
        double speedBonus = data == null ? 0.0 : enchants.powerOf(data, "GREEN_THUMB");

        lore.add(ChatColor.GOLD + "Information");
        lore.add(ChatColor.DARK_GRAY + "\u251c " + ChatColor.GREEN + "TOKENS: "
                + ChatColor.WHITE + "+" + String.format("%.0f", tokenBonus * 100.0) + "%");
        lore.add(ChatColor.DARK_GRAY + "\u251c " + ChatColor.AQUA + "SPEED: "
                + ChatColor.WHITE + "+" + String.format("%.0f", speedBonus * 100.0) + "%");
        lore.add("");

        lore.add(ChatColor.GOLD + "Enchants");
        boolean any = false;
        if (data != null) {
            for (var enchant : enchants.getEnchants().values()) {
                int level = enchants.levelOf(data, enchant.id());
                if (level <= 0) continue;
                lore.add(ChatColor.DARK_GRAY + "\u251c " + enchant.styled(level));
                any = true;
            }
        }
        if (!any) {
            lore.add(ChatColor.DARK_GRAY + "\u251c " + ChatColor.RED + "No Enchants");
        }
        lore.add("");

        lore.add(ChatColor.GOLD + "Attachments");
        lore.add(ChatColor.DARK_GRAY + "\u251c " + ChatColor.RED + "No Attachments");
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "[" + ChatColor.YELLOW + "RIGHT CLICK TO UPGRADE"
                + ChatColor.DARK_GRAY + "]");

        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(boundKey, PersistentDataType.BYTE, (byte) 1);
        hoe.setItemMeta(meta);
        return hoe;
    }

    /** Rewrites a held hoe in place so its lore matches what's been bought. */
    public void refreshHoe(org.bukkit.entity.Player player, com.spacerng.solrng.player.PlayerData data) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isBoundHoe(contents[i])) {
                contents[i] = createBoundHoe(data);
            }
        }
        player.getInventory().setContents(contents);
    }

    public boolean isBoundHoe(ItemStack item) {
        return item != null && item.getItemMeta() != null
                && item.getItemMeta().getPersistentDataContainer().has(boundKey, PersistentDataType.BYTE);
    }
}
