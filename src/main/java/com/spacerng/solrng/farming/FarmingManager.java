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
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "FARMER'S HOE");

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "FARMING");
        lore.add("");
        if (data != null) {
            var enchants = plugin.getHoeEnchantManager();
            boolean any = false;
            for (var enchant : enchants.getEnchants().values()) {
                int level = enchants.levelOf(data, enchant.id());
                if (level <= 0) continue;
                lore.add(ChatColor.GRAY + "▎ " + enchant.styled(level));
                any = true;
            }
            if (!any) {
                lore.add(ChatColor.DARK_GRAY + "▎ No enchants yet — see /farmtree");
            }
            lore.add("");
        }
        lore.add(ChatColor.GRAY + "Bound to you — can't be dropped.");
        lore.add(ChatColor.GRAY + "Used to work the SpaceRNG farm.");
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(boundKey, PersistentDataType.BYTE, (byte) 1);
        hoe.setItemMeta(meta);
        return hoe;
    }

    public boolean isBoundHoe(ItemStack item) {
        return item != null && item.getItemMeta() != null
                && item.getItemMeta().getPersistentDataContainer().has(boundKey, PersistentDataType.BYTE);
    }
}
