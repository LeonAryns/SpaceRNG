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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * /armor - pieces bought ONE AT A TIME with rolled drops. Each piece
 * grants its tier's Luck and Speed independently while worn (checked live
 * from equipped armor, not just "do you own it"), so a lone pair of boots
 * still pays out and mixing tiers across slots is fine.
 */
public class ArmorManager {

    private final SolRNGPlugin plugin;
    private final NamespacedKey tierKey;
    private final Map<String, ArmorTier> tiers = new LinkedHashMap<>();
    private final Logger logger;

    public ArmorManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.tierKey = SolRNGPlugin.key( "solrng_armor_tier");
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
                double speedBonus = t.getDouble("speed-bonus", 0.0);
                Map<Rarity, Long> costs = new EnumMap<>(Rarity.class);
                ConfigurationSection costsSection = t.getConfigurationSection("costs");
                if (costsSection != null) {
                    for (String rarityKey : costsSection.getKeys(false)) {
                        costs.put(Rarity.valueOf(rarityKey.toUpperCase()), costsSection.getLong(rarityKey));
                    }
                }
                tiers.put(id, new ArmorTier(id, display, costs, luckBonus, speedBonus));
            } catch (Exception ex) {
                logger.warning("Skipped malformed armor tier '" + id + "': " + ex.getMessage());
            }
        }
        logger.info("Loaded " + tiers.size() + " armor tiers.");
    }

    public Map<String, ArmorTier> getTiers() {
        return tiers;
    }

    public ArmorTier get(String id) {
        return tiers.get(id);
    }

    /** The cost shown/charged is per piece, not per set. */
    public boolean canAfford(Player player, ArmorTier tier, ArmorPiece piece) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        for (Map.Entry<Rarity, Long> cost : tier.costsFor(piece).entrySet()) {
            if (DropWallet.total(plugin, player, data, cost.getKey()) < cost.getValue()) return false;
        }
        return true;
    }

    /**
     * Buys ONE piece: consumes that piece's drop cost and hands the item
     * over. Returns true on success.
     */
    public boolean purchase(Player player, PlayerData data, String tierId, ArmorPiece piece, NamespacedKey rarityKey) {
        ArmorTier tier = tiers.get(tierId);
        if (tier == null) return false;
        if (data.hasPurchasedArmor(tierId, piece)) return false;
        if (!canAfford(player, tier, piece)) return false;

        for (Map.Entry<Rarity, Long> cost : tier.costsFor(piece).entrySet()) {
            DropWallet.spend(plugin, player, data, cost.getKey(), cost.getValue());
        }

        data.markArmorPurchased(tierId, piece);
        givePiece(player, tier, piece);
        return true;
    }

    /**
     * Builds and hands over one piece. The Luck/Speed it grants is written
     * into the item's own lore - the bonus is invisible otherwise, since
     * it's applied by this plugin rather than by vanilla attributes.
     */
    private void givePiece(Player player, ArmorTier tier, ArmorPiece piece) {
        ItemStack item = new ItemStack(tier.materialFor(piece));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + tier.pieceDisplay(piece));
        meta.setLore(statLines(tier));
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.getId());
        // Bought armor never wears out (V253).
        meta.setUnbreakable(true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);

        // Straight onto the body when it beats what's there: an empty slot,
        // or an older piece of plugin armor, which goes back in the bag.
        // Anything else in the slot, an elytra or a pumpkin, is left alone.
        PlayerInventory inv = player.getInventory();
        org.bukkit.inventory.EquipmentSlot slot = slotOf(piece);
        ItemStack worn = inv.getItem(slot);
        boolean empty = worn == null || worn.getType().isAir();
        ArmorTier wornTier = empty ? null : tiers.get(tierOf(worn));
        if (empty || (wornTier != null && rank(wornTier) < rank(tier))) {
            inv.setItem(slot, item);
            player.sendMessage(ChatColor.GREEN + "Equipped " + ChatColor.AQUA + tier.pieceDisplay(piece));
            if (empty) return;
            item = worn;
        }

        Stash.give(plugin, player, item);
    }

    private static org.bukkit.inventory.EquipmentSlot slotOf(ArmorPiece piece) {
        return switch (piece) {
            case HELMET -> org.bukkit.inventory.EquipmentSlot.HEAD;
            case CHESTPLATE -> org.bukkit.inventory.EquipmentSlot.CHEST;
            case LEGGINGS -> org.bukkit.inventory.EquipmentSlot.LEGS;
            case BOOTS -> org.bukkit.inventory.EquipmentSlot.FEET;
        };
    }

    /** Which of two tiers is better: more Luck, then more Speed. */
    private static double rank(ArmorTier tier) {
        return tier.getLuckBonus() * 1000.0 + tier.getSpeedBonus();
    }

    /** The "When Worn" block - shared by the shop icon and the real item. */
    public List<String> statLines(ArmorTier tier) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "When Worn:");
        lore.add(ChatColor.AQUA + "◆ " + ChatColor.GRAY + "Luck: " + ChatColor.GREEN
                + "+" + Math.round(tier.getLuckBonus() * 100) + "%");
        lore.add(ChatColor.AQUA + "◆ " + ChatColor.GRAY + "Speed: " + ChatColor.YELLOW
                + "+" + Math.round(tier.getSpeedBonus() * 100));
        return lore;
    }

    /**
     * Recomputes every online player's armor Luck bonus from what they're
     * actually wearing right now - each worn piece contributes its own
     * tier's Luck bonus independently (no need to match a full set, and
     * mixing tiers across slots is fine).
     */
    public void refreshWornBonuses() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
            PlayerInventory inv = player.getInventory();

            double luck = 0.0;
            double speed = 0.0;
            for (ItemStack piece : new ItemStack[]{inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()}) {
                ArmorTier tier = tiers.get(tierOf(piece));
                if (tier != null) {
                    luck += tier.getLuckBonus();
                    speed += tier.getSpeedBonus();
                }
            }
            data.setArmorLuckBonus(luck);
            data.setArmorSpeedBonus(speed);
        }
    }

    /**
     * V306: armor got far dearer, so what was bought at the old prices is
     * taken back once per player: worn, in the inventory and in /pv, and
     * the record of what they bought. armor.reset-version says which
     * round this is; a player whose save is behind it is wiped on join.
     */
    public void wipeIfOld(Player player, PlayerData data) {
        int version = plugin.getConfig().getInt("armor.reset-version", 0);
        if (data.getArmorVersion() >= version) return;
        int removed = 0;
        PlayerInventory inv = player.getInventory();
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (tierOf(armor[i]) != null) {
                armor[i] = null;
                removed++;
            }
        }
        inv.setArmorContents(armor);
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (tierOf(contents[i]) != null) {
                removed += contents[i].getAmount();
                contents[i] = null;
            }
        }
        inv.setStorageContents(contents);
        if (tierOf(inv.getItemInOffHand()) != null) {
            inv.setItemInOffHand(null);
            removed++;
        }
        for (List<ItemStack> page : data.getVaults().values()) {
            for (int i = 0; i < page.size(); i++) {
                if (tierOf(page.get(i)) != null) {
                    page.set(i, null);
                    removed++;
                }
            }
        }
        boolean bought = !data.getPurchasedArmorTiers().isEmpty();
        // V308: every piece bought is paid back at the price it was bought
        // for (the flat per-piece prices before V306), into the drop bank.
        Map<Rarity, Long> refund = new java.util.EnumMap<>(Rarity.class);
        for (String key : data.getPurchasedArmorTiers()) {
            String tierId = key.contains(":") ? key.substring(0, key.indexOf(':')) : key;
            Map<Rarity, Long> paid = OLD_PRICES.get(tierId);
            if (paid == null) continue;
            for (Map.Entry<Rarity, Long> cost : paid.entrySet()) refund.merge(cost.getKey(), cost.getValue(), Long::sum);
        }
        for (Map.Entry<Rarity, Long> back : refund.entrySet()) data.addBankedDrops(back.getKey(), back.getValue());
        data.getPurchasedArmorTiers().clear();
        data.setArmorVersion(version);
        if (removed > 0 || bought) {
            StringBuilder back = new StringBuilder();
            for (Map.Entry<Rarity, Long> entry : refund.entrySet()) {
                if (back.length() > 0) back.append(ChatColor.GRAY).append(", ");
                back.append(plugin.getRarityManager().style(entry.getKey(),
                        entry.getValue() + " " + entry.getKey().displayName()));
            }
            player.sendMessage(ChatColor.GOLD + "Armor was reworked: every piece has its own price now and the"
                    + " higher sets take rarer drops. Your old armor was taken back"
                    + (back.length() > 0 ? " and refunded: " + back + ChatColor.GOLD + ", in your /convert bank" : "")
                    + ". See " + ChatColor.YELLOW + "/armor" + ChatColor.GOLD + ".");
        }
    }

    /** What one piece cost before V306, for the refund. */
    private static final Map<String, Map<Rarity, Long>> OLD_PRICES = Map.of(
            "LEATHER", Map.of(Rarity.COMMON, 10L, Rarity.UNCOMMON, 1L),
            "CHAINMAIL", Map.of(Rarity.COMMON, 25L, Rarity.UNCOMMON, 5L),
            "IRON", Map.of(Rarity.UNCOMMON, 25L, Rarity.RARE, 5L),
            "GOLD", Map.of(Rarity.UNCOMMON, 50L, Rarity.RARE, 10L),
            "DIAMOND", Map.of(Rarity.RARE, 25L, Rarity.EPIC, 5L),
            "NETHERITE", Map.of(Rarity.RARE, 50L, Rarity.EPIC, 10L));

    /** Plugin armor, which never takes durability damage (V253). */
    public boolean isPluginArmor(ItemStack piece) {
        return tierOf(piece) != null;
    }

    private String tierOf(ItemStack piece) {
        if (piece == null || piece.getItemMeta() == null) return null;
        return piece.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
    }
}
