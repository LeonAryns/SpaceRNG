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

    // ------------------------------------------------------------ levels
    //
    // V353, Leon's call: a tier is not a one-off purchase any more. Buying
    // a piece puts the tier at level 1, and it climbs to max-level on more
    // of the SAME drop it was bought with - never a rarer one, which was
    // the explicit ask. The tier above does not open until this one is at
    // the ceiling, so the ladder is climbed rather than skipped.

    public int maxArmorLevel() {
        return Math.max(1, plugin.getConfig().getInt("armor.levels.max-level", 10));
    }

    /**
     * Derived, not stored: anybody who already owns a piece of this tier is
     * at least level 1, whether or not a level was ever written down. That
     * is what carries every account bought before V353 across without a
     * migration, and it keeps one definition of what level 1 means.
     */
    public int levelOf(PlayerData data, String tierId) {
        int stored = data.getArmorLevel(tierId);
        if (stored > 0) return Math.min(stored, maxArmorLevel());
        for (ArmorPiece piece : ArmorPiece.values()) {
            if (data.hasPurchasedArmor(tierId, piece)) return 1;
        }
        return 0;
    }

    /**
     * What a worn piece of this tier is actually worth at `level`. Level 1
     * is the tier's printed bonus and every level over it adds
     * armor.levels.bonus-per-level of that same printed bonus, so a tier
     * at 10 is worth roughly twice what it is at 1 by default.
     */
    public double levelScale(int level) {
        if (level <= 1) return 1.0;
        double per = plugin.getConfig().getDouble("armor.levels.bonus-per-level", 0.10);
        return 1.0 + per * (Math.min(level, maxArmorLevel()) - 1);
    }

    /**
     * The drop this tier levels on: the first, cheapest rarity in its
     * costs. Levelling never reaches for the rarer second one - that is
     * what the NEXT tier is for.
     */
    public Rarity levelRarity(ArmorTier tier) {
        Rarity lowest = null;
        for (Rarity rarity : tier.getCosts().keySet()) {
            if (lowest == null || rarity.ordinal() < lowest.ordinal()) lowest = rarity;
        }
        return lowest;
    }

    /**
     * How many of that drop level {@code level} to {@code level + 1} costs.
     *
     * V357, Leon's call: the ladder is pinned at the TOP rather than
     * grown from the tier's buy price. The last step, the one into
     * max-level, costs armor.levels.top-cost drops, and every step below
     * it is cost-growth cheaper than the one above. At 50 and 1.45 over
     * ten levels that is 3, 4, 6, 8, 12, 17, 25, 36, 50, which is 161 for
     * a maxed tier against 607 before, so every level costs less and the
     * last one costs fifty.
     *
     * Pinning the top also means every tier's climb is the same COUNT of
     * its own rarity. A Mythical tier asking for fifty Mythicals is
     * already far harder than a Leather one asking for fifty Commons;
     * multiplying that by the tier's price as well made the top tiers
     * unreachable.
     */
    public long levelCost(ArmorTier tier, int level) {
        Rarity rarity = levelRarity(tier);
        if (rarity == null) return 0L;
        double top = plugin.getConfig().getDouble("armor.levels.top-cost", 50.0);
        double growth = Math.max(1.01, plugin.getConfig().getDouble("armor.levels.cost-growth", 1.45));
        int last = Math.max(1, maxArmorLevel() - 1);
        return Math.max(1L, Math.round(top * Math.pow(growth, Math.max(0, level) - last)));
    }

    /** The tier before this one in config order, or null for the first. */
    public ArmorTier previousTier(String tierId) {
        ArmorTier previous = null;
        for (Map.Entry<String, ArmorTier> entry : tiers.entrySet()) {
            if (entry.getKey().equals(tierId)) return previous;
            previous = entry.getValue();
        }
        return null;
    }

    /**
     * Whether this tier can be bought at all yet. The first tier always
     * can; every other one waits for the tier below it to be maxed.
     */
    public boolean tierOpen(PlayerData data, String tierId) {
        ArmorTier previous = previousTier(tierId);
        return previous == null || levelOf(data, previous.getId()) >= maxArmorLevel();
    }

    /**
     * Spends one level's worth of drops. Returns false when the tier is
     * not owned, is already at the ceiling, or the drops are not there.
     */
    public boolean levelUp(Player player, PlayerData data, String tierId) {
        ArmorTier tier = tiers.get(tierId);
        if (tier == null) return false;
        int level = levelOf(data, tierId);
        if (level < 1 || level >= maxArmorLevel()) return false;
        Rarity rarity = levelRarity(tier);
        if (rarity == null) return false;
        long cost = levelCost(tier, level);
        if (DropWallet.total(plugin, player, data, rarity) < cost) return false;
        DropWallet.spend(plugin, player, data, rarity, cost);
        data.setArmorLevel(tierId, level + 1);
        refreshWornBonuses();
        return true;
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
        // V353: the tier below has to be at its ceiling first.
        if (!tierOpen(data, tierId)) return false;
        if (!canAfford(player, tier, piece)) return false;

        for (Map.Entry<Rarity, Long> cost : tier.costsFor(piece).entrySet()) {
            DropWallet.spend(plugin, player, data, cost.getKey(), cost.getValue());
        }

        data.markArmorPurchased(tierId, piece);
        // The first piece of a tier puts it at level 1. The other three do
        // not reset it, so buying the set in any order is safe.
        if (data.getArmorLevel(tierId) < 1) data.setArmorLevel(tierId, 1);
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
        lore.add(ChatColor.DARK_GRAY + "Scales with this set's level in /armor.");
        return lore;
    }

    /**
     * Recomputes every online player's armor Luck bonus from what they're
     * actually wearing right now - each worn piece contributes its own
     * tier's Luck bonus independently (no need to match a full set, and
     * mixing tiers across slots is fine).
     */
    /** What a full matching set adds on top, from armor.set-bonus (V351). */
    private double setBonus() {
        return Math.max(0.0, plugin.getConfig().getDouble("armor.set-bonus", 0.5));
    }

    public void refreshWornBonuses() {
        double setBonus = setBonus();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
            PlayerInventory inv = player.getInventory();

            double luck = 0.0;
            double speed = 0.0;
            int worn = 0;
            String setId = null;
            boolean matched = true;
            for (ItemStack piece : new ItemStack[]{inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()}) {
                String id = tierOf(piece);
                ArmorTier tier = tiers.get(id);
                if (tier != null) {
                    // V353: each piece pays at the level its TIER is on,
                    // so levelling up is felt without re-handing the item
                    // over and without touching armour already worn.
                    double scale = levelScale(levelOf(data, id));
                    luck += tier.getLuckBonus() * scale;
                    speed += tier.getSpeedBonus() * scale;
                    worn++;
                    if (setId == null) setId = id;
                    else if (!setId.equals(id)) matched = false;
                }
            }
            // V351: a full set of one tier is worth half again as much.
            // Mixing tiers still works and still pays for every piece, so
            // nothing is taken away; the set is the thing to aim at, which
            // is what the late game said armour was missing.
            if (worn == 4 && matched) {
                luck *= 1.0 + setBonus;
                speed *= 1.0 + setBonus;
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
        data.getArmorLevels().clear();
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
