package com.spacerng.solrng.starforge;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Starforge — the item players right-click to roll and left-click to
 * toggle Auto Roll. Its tier is what sets a player's BASE Luck; the skill
 * tree and armor add on top of that, and the index multiplier scales the
 * whole thing.
 *
 * The tier is stored on PlayerData rather than read off the held item, so
 * losing or stashing the physical item never costs a player the Luck they
 * paid for. The item itself is just the interaction handle, tagged with a
 * PersistentDataContainer key so any tier's item is recognised.
 */
public class StarforgeManager {

    public static final String DEFAULT_TIER = "BASIC";

    private final SolRNGPlugin plugin;
    private final NamespacedKey itemKey;
    private final Map<String, StarforgeTier> tiers = new LinkedHashMap<>();

    public StarforgeManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "solrng_starforge");
    }

    public NamespacedKey getItemKey() {
        return itemKey;
    }

    public void load(FileConfiguration config) {
        tiers.clear();
        ConfigurationSection section = config.getConfigurationSection("starforge.tiers");
        if (section == null) {
            plugin.getLogger().warning("[SolRNG] No starforge.tiers configured.");
            return;
        }

        int order = 0;
        for (String id : section.getKeys(false)) {
            ConfigurationSection t = section.getConfigurationSection(id);
            if (t == null) continue;
            tiers.put(id, new StarforgeTier(
                    id,
                    t.getString("display", id),
                    t.getDouble("luck-bonus", 0.0),
                    t.getDouble("cost-money", 0.0),
                    order++));
        }
        plugin.getLogger().info("[SolRNG] Loaded " + tiers.size() + " Starforge tiers.");
    }

    public Map<String, StarforgeTier> getTiers() {
        return tiers;
    }

    public List<StarforgeTier> getOrderedTiers() {
        return new ArrayList<>(tiers.values());
    }

    public StarforgeTier get(String id) {
        return tiers.get(id);
    }

    /** The player's current tier, falling back to Basic if theirs is unknown. */
    public StarforgeTier tierOf(PlayerData data) {
        StarforgeTier tier = tiers.get(data.getStarforgeTier());
        return tier != null ? tier : tiers.get(DEFAULT_TIER);
    }

    public double luckBonusOf(PlayerData data) {
        StarforgeTier tier = tierOf(data);
        return tier == null ? 0.0 : tier.getLuckBonus();
    }

    private Economy economy() {
        var registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    public double balanceOf(Player player) {
        Economy economy = economy();
        return economy == null ? 0.0 : economy.getBalance(player);
    }

    /**
     * Buys a tier with Money. Only upgrades — you can't buy a tier at or
     * below the one you already own. Hands over the new item on success.
     */
    public boolean purchase(Player player, PlayerData data, String tierId) {
        StarforgeTier target = tiers.get(tierId);
        if (target == null) return false;

        StarforgeTier current = tierOf(data);
        if (current != null && target.getOrder() <= current.getOrder()) return false;

        Economy economy = economy();
        if (economy == null || economy.getBalance(player) < target.getMoneyCost()) return false;
        economy.withdrawPlayer(player, target.getMoneyCost());

        data.setStarforgeTier(target.getId());
        replaceHeldStarforge(player, target);
        return true;
    }

    /**
     * Swaps every Starforge in the player's inventory for the new tier's
     * item, or just gives them one if they'd lost it.
     */
    public void replaceHeldStarforge(Player player, StarforgeTier tier) {
        ItemStack[] contents = player.getInventory().getContents();
        boolean replacedAny = false;
        for (int i = 0; i < contents.length; i++) {
            if (isStarforge(contents[i])) {
                contents[i] = create(tier);
                replacedAny = true;
            }
        }
        player.getInventory().setContents(contents);
        if (!replacedAny) {
            player.getInventory().addItem(create(tier));
        }
    }

    public boolean isStarforge(ItemStack item) {
        return item != null && item.getItemMeta() != null
                && item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.STRING);
    }

    /** Builds the physical item for a tier. */
    public ItemStack create(StarforgeTier tier) {
        Material material = Material.matchMaterial(
                plugin.getConfig().getString("roll-item.material", "NETHER_STAR"));
        if (material == null) material = Material.NETHER_STAR;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + tier.getDisplay());
        meta.setLore(List.of(
                ChatColor.DARK_AQUA + "+" + formatPercent(tier.getLuckBonus()) + "% Luck",
                "",
                ChatColor.GRAY + "Right-click to " + ChatColor.WHITE + "roll",
                ChatColor.GRAY + "Left-click to toggle " + ChatColor.WHITE + "Auto Roll",
                "",
                ChatColor.DARK_GRAY + "Upgrade it in /starforge"
        ));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, tier.getId());
        item.setItemMeta(meta);
        return item;
    }

    public static String formatPercent(double luckBonus) {
        return String.valueOf(Math.round(luckBonus * 100));
    }
}
