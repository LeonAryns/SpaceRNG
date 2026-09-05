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
    private final java.util.List<HoeTier> hoeTiers = new java.util.ArrayList<>();
    private int regrowTicks = 60;

    /**
     * One rung of the tool itself. A tier is a flat bonus on the hoe
     * rather than an enchant, which is why it shows in the Information
     * block: it's what the tool IS, not something bolted onto it.
     */
    public record HoeTier(String display, Material material, double tokenBonus, double speedBonus) {
    }

    public FarmingManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.boundKey = new NamespacedKey(plugin, "solrng_bound_hoe");
    }

    public void load(FileConfiguration config) {
        cropTokens.clear();
        regrowTicks = config.getInt("farming.regrow-seconds", 3) * 20;

        hoeTiers.clear();
        for (Map<?, ?> raw : config.getMapList("farming.hoe-tiers")) {
            Material material = Material.matchMaterial(String.valueOf(raw.get("material")));
            if (material == null) continue;
            hoeTiers.add(new HoeTier(
                    raw.get("display") == null ? "Hoe" : String.valueOf(raw.get("display")),
                    material,
                    asDouble(raw.get("token-bonus")),
                    asDouble(raw.get("speed-bonus"))));
        }
        if (hoeTiers.isEmpty()) {
            hoeTiers.add(new HoeTier("Wooden", Material.WOODEN_HOE, 0.0, 0.0));
        }

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

    private static double asDouble(Object raw) {
        if (raw == null) return 0.0;
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    public java.util.List<HoeTier> getHoeTiers() {
        return hoeTiers;
    }

    /** Which rung of the tool ladder this player has bought up to. */
    public int tierIndexOf(com.spacerng.solrng.player.PlayerData data) {
        if (data == null) return 0;
        int bought = (int) Math.round(plugin.getSkillTreeManager()
                .totalOf(data, com.spacerng.solrng.player.SkillNode.Effect.HOE_TIER));
        return Math.max(0, Math.min(hoeTiers.size() - 1, bought));
    }

    public HoeTier tierOf(com.spacerng.solrng.player.PlayerData data) {
        return hoeTiers.get(tierIndexOf(data));
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
        HoeTier tier = tierOf(data);
        int tierIndex = tierIndexOf(data);

        ItemStack hoe = new ItemStack(tier.material());
        ItemMeta meta = hoe.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + tier.display() + " Hoe " + ChatColor.DARK_GRAY + "["
                + ChatColor.YELLOW + roman(tierIndex + 1) + ChatColor.DARK_GRAY + "]");

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "Farming Tool");
        lore.add("");

        var enchants = plugin.getHoeEnchantManager();
        // The tool's own tier and its Token Greed both raise Tokens; Green
        // Thumb and the tier both raise Speed. They're summed here because
        // the tooltip is answering "what is this hoe worth", not "where did
        // each percent come from".
        double tokenBonus = tier.tokenBonus() + (data == null ? 0.0 : enchants.powerOf(data, "TOKEN_GREED"));
        double speedBonus = tier.speedBonus() + (data == null ? 0.0 : enchants.powerOf(data, "GREEN_THUMB"));

        lore.add(ChatColor.GOLD + "Information");
        lore.add(ChatColor.DARK_GRAY + "\u251c " + ChatColor.YELLOW + "TIER: "
                + ChatColor.WHITE + tier.display());
        lore.add(ChatColor.DARK_GRAY + "\u251c " + ChatColor.GREEN + "TOKENS: "
                + ChatColor.WHITE + "+" + String.format("%,.0f", tokenBonus * 100.0) + "%");
        lore.add(ChatColor.DARK_GRAY + "\u251c " + ChatColor.AQUA + "SPEED: "
                + ChatColor.WHITE + "+" + String.format("%,.0f", speedBonus * 100.0) + "%");
        lore.add("");

        lore.add(ChatColor.GOLD + "Enchants");
        boolean any = false;
        if (data != null) {
            for (var enchant : enchants.getEnchants().values()) {
                int level = enchants.levelOf(data, enchant.id());
                if (level <= 0) continue;
                lore.add(ChatColor.DARK_GRAY + "\u251c " + enchant.colour() + enchant.display()
                        + ChatColor.DARK_GRAY + " " + level
                        + ChatColor.DARK_GRAY + "/" + enchants.maxLevelFor(data, enchant));
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

    private static String roman(int value) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return value >= 1 && value <= numerals.length ? numerals[value - 1] : String.valueOf(value);
    }

    public boolean isBoundHoe(ItemStack item) {
        return item != null && item.getItemMeta() != null
                && item.getItemMeta().getPersistentDataContainer().has(boundKey, PersistentDataType.BYTE);
    }
}
