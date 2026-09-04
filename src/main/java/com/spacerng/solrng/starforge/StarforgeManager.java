package com.spacerng.solrng.starforge;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Starforge — the item players right-click to roll, left-click to
 * toggle Auto Roll, and drop-key to open settings. Its tier is what sets
 * a player's BASE Luck; the skill tree and armor add on top, and the
 * index multiplier scales the whole thing.
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

            Map<Rarity, Long> costs = new EnumMap<>(Rarity.class);
            ConfigurationSection costsSection = t.getConfigurationSection("costs");
            if (costsSection != null) {
                for (String rarityKey : costsSection.getKeys(false)) {
                    try {
                        costs.put(Rarity.valueOf(rarityKey.toUpperCase()), costsSection.getLong(rarityKey));
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("[SolRNG] Unknown rarity '" + rarityKey + "' in starforge tier " + id);
                    }
                }
            }

            tiers.put(id, new StarforgeTier(
                    id,
                    t.getString("display", id),
                    t.getDouble("luck-bonus", 0.0),
                    costs,
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

    /** True while a Starforge is in either hand — main or off, both count. */
    public boolean isHolding(Player player) {
        return isStarforge(player.getInventory().getItemInMainHand())
                || isStarforge(player.getInventory().getItemInOffHand());
    }

    /**
     * Recomputes every online player's Starforge Luck from whether they're
     * actually holding it, and switches Auto Roll off for anyone who's put
     * theirs away. Same live-recompute pattern as worn armor.
     */
    public void refreshHeldBonuses() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
            boolean holding = isHolding(player);
            data.setStarforgeLuckBonus(holding ? luckBonusOf(data) : 0.0);

            if (!holding && data.isAutoRollEnabled()) {
                data.setAutoRollEnabled(false);
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(
                                ChatColor.RED + "" + ChatColor.BOLD + "Auto Roll OFF "
                                        + ChatColor.RESET + ChatColor.GRAY + "(Starforge put away)"));
            }
        }
    }

    private NamespacedKey rarityKey() {
        return plugin.getRollListener().getRarityKey();
    }

    public long countHeld(Player player, Rarity rarity) {
        NamespacedKey key = rarityKey();
        long total = 0L;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getItemMeta() == null) continue;
            String rarityName = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (rarity.name().equals(rarityName)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    public boolean canAfford(Player player, StarforgeTier tier) {
        for (Map.Entry<Rarity, Long> cost : tier.getCosts().entrySet()) {
            if (countHeld(player, cost.getKey()) < cost.getValue()) return false;
        }
        return true;
    }

    private void consume(Player player, Rarity rarity, long amount) {
        NamespacedKey key = rarityKey();
        ItemStack[] contents = player.getInventory().getStorageContents();
        long remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getItemMeta() == null) continue;
            String rarityName = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
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
     * Buys a tier with rolled drops. Only upgrades — you can't buy a tier
     * at or below the one you already own. Hands over the new item on
     * success.
     */
    public boolean purchase(Player player, PlayerData data, String tierId) {
        StarforgeTier target = tiers.get(tierId);
        if (target == null) return false;

        StarforgeTier current = tierOf(data);
        if (current != null && target.getOrder() <= current.getOrder()) return false;
        if (!canAfford(player, target)) return false;

        for (Map.Entry<Rarity, Long> cost : target.getCosts().entrySet()) {
            consume(player, cost.getKey(), cost.getValue());
            data.addConverted(cost.getKey(), cost.getValue());
        }

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
        meta.setDisplayName(ChatColor.WHITE + tier.getDisplay());
        meta.setLore(statLines(tier));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, tier.getId());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * The shared top half of the tooltip — stats and controls. The shop
     * icon appends a price block below this; the held item stops here.
     */
    public List<String> statLines(StarforgeTier tier) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "When Held:");
        lore.add(ChatColor.AQUA + "◆ " + ChatColor.GRAY + "Luck: " + ChatColor.GREEN
                + "+" + formatPercent(tier.getLuckBonus()) + "%");
        lore.add("");
        lore.add(ChatColor.GRAY + "Punch " + ChatColor.DARK_GRAY + "» " + ChatColor.AQUA + "Auto Roll");
        lore.add(ChatColor.GRAY + "Drop " + ChatColor.DARK_GRAY + "» " + ChatColor.AQUA + "Settings");
        lore.add("");
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "INTERACT TO ROLL");
        return lore;
    }

    public static String formatPercent(double luckBonus) {
        return String.valueOf(Math.round(luckBonus * 100));
    }
}
