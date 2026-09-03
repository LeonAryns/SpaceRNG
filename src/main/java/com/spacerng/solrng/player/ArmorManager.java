package com.spacerng.solrng.player;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * /armor — sets bought once with rolled drops (like the skill tree). Each
 * piece grants its tier's Luck bonus independently while worn (checked
 * live from equipped armor, not just "do you own it") — no need to match
 * a full set, and mixing tiers across slots is fine.
 */
public class ArmorManager {

    private final SolRNGPlugin plugin;
    private final NamespacedKey tierKey;
    private final Map<String, ArmorTier> tiers = new LinkedHashMap<>();
    private final Logger logger;

    public ArmorManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.tierKey = new NamespacedKey(plugin, "solrng_armor_tier");
        this.logger = plugin.getLogger();
    }

    public NamespacedKey getTierKey() {
        return tierKey;
    }

    public void load(FileConfiguration config) {
        tiers.clear();
        ConfigurationSection section = config.getConfigurationSection("armor.tiers");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection t = section.getConfigurationSection(id);
            if (t == null) continue;
            try {
                String display = t.getString("display", id);
                double luckBonus = t.getDouble("luck-bonus", 0.0);
                Map<Rarity, Long> costs = new EnumMap<>(Rarity.class);
                ConfigurationSection costsSection = t.getConfigurationSection("costs");
                if (costsSection != null) {
                    for (String rarityKey : costsSection.getKeys(false)) {
                        costs.put(Rarity.valueOf(rarityKey.toUpperCase()), costsSection.getLong(rarityKey));
                    }
                }
                tiers.put(id, new ArmorTier(id, display, costs, luckBonus));
            } catch (Exception ex) {
                logger.warning("[SolRNG] Skipped malformed armor tier '" + id + "': " + ex.getMessage());
            }
        }
        logger.info("[SolRNG] Loaded " + tiers.size() + " armor tiers.");
    }

    public Map<String, ArmorTier> getTiers() {
        return tiers;
    }

    public ArmorTier get(String id) {
        return tiers.get(id);
    }

    public boolean canAfford(Player player, ArmorTier tier, NamespacedKey rarityKey) {
        for (Map.Entry<Rarity, Long> cost : tier.getCosts().entrySet()) {
            if (countHeld(player, rarityKey, cost.getKey()) < cost.getValue()) return false;
        }
        return true;
    }

    /**
     * Buys a tier: consumes the drop cost from inventory and hands over
     * the actual 4-piece armor set. Returns true on success.
     */
    public boolean purchase(Player player, PlayerData data, String tierId, NamespacedKey rarityKey) {
        ArmorTier tier = tiers.get(tierId);
        if (tier == null) return false;
        if (data.hasPurchasedArmor(tierId)) return false;
        if (!canAfford(player, tier, rarityKey)) return false;

        for (Map.Entry<Rarity, Long> cost : tier.getCosts().entrySet()) {
            consume(player, rarityKey, cost.getKey(), cost.getValue());
            data.addConverted(cost.getKey(), cost.getValue());
        }

        data.markArmorPurchased(tierId);
        givePiece(player, tier.helmet(), tier);
        givePiece(player, tier.chestplate(), tier);
        givePiece(player, tier.leggings(), tier);
        givePiece(player, tier.boots(), tier);
        return true;
    }

    private void givePiece(Player player, Material material, ArmorTier tier) {
        ItemStack piece = new ItemStack(material);
        ItemMeta meta = piece.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + tier.getDisplay());
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.getId());
        piece.setItemMeta(meta);

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(piece);
        overflow.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private long countHeld(Player player, NamespacedKey rarityKey, Rarity rarity) {
        long total = 0L;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getItemMeta() == null) continue;
            String rarityName = stack.getItemMeta().getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING);
            if (rarity.name().equals(rarityName)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void consume(Player player, NamespacedKey rarityKey, Rarity rarity, long amount) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        long remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getItemMeta() == null) continue;
            String rarityName = stack.getItemMeta().getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING);
            if (!rarity.name().equals(rarityName)) continue;

            long take = Math.min(remaining, stack.getAmount());
            stack.setAmount((int) (stack.getAmount() - take));
            remaining -= take;
            if (stack.getAmount() <= 0) {
                contents[i] = null;
            }
        }
        player.getInventory().setStorageContents(contents);
    }

    /**
     * Recomputes every online player's armor Luck bonus from what they're
     * actually wearing right now — each worn piece contributes its own
     * tier's Luck bonus independently (no need to match a full set, and
     * mixing tiers across slots is fine).
     */
    public void refreshWornBonuses() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
            data.setArmorLuckBonus(wornLuckBonus(player));
        }
    }

    private double wornLuckBonus(Player player) {
        PlayerInventory inv = player.getInventory();
        double total = 0.0;
        for (ItemStack piece : new ItemStack[]{inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()}) {
            ArmorTier tier = tiers.get(tierOf(piece));
            if (tier != null) {
                total += tier.getLuckBonus();
            }
        }
        return total;
    }

    private String tierOf(ItemStack piece) {
        if (piece == null || piece.getItemMeta() == null) return null;
        return piece.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
    }
}
