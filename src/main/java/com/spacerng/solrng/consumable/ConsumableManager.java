package com.spacerng.solrng.consumable;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Lore;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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
 * Redeemables: the potions, the banked 10x roll, and the permanent Luck
 * grants that milestones, the Battle Pass and crates hand out.
 *
 * They're real items rather than an invisible balance on purpose. A
 * reward you can hold, look at and choose when to use is worth more than
 * the same number applied silently - and it means a crate plugin, a
 * command block or a milestone can all deliver the same thing without
 * knowing anything about how it works.
 *
 * A timed boost is stored on the player as a multiplier and an expiry, so
 * drinking a second one of the same kind either extends it or upgrades
 * it, never stacks it into something absurd.
 */
public class ConsumableManager {

    private final SolRNGPlugin plugin;
    private final NamespacedKey idKey;
    private final Map<String, Consumable> consumables = new LinkedHashMap<>();

    public ConsumableManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.idKey = SolRNGPlugin.key( "solrng_consumable");
    }

    public void load(FileConfiguration config) {
        consumables.clear();
        ConfigurationSection section = config.getConfigurationSection("consumables");
        if (section == null) {
            plugin.getLogger().info("No consumables configured.");
            return;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection c = section.getConfigurationSection(id);
            if (c == null) continue;
            try {
                Material material = Material.matchMaterial(c.getString("material", "POTION"));
                if (material == null) material = Material.POTION;
                consumables.put(id.toLowerCase(), new Consumable(
                        id.toLowerCase(),
                        c.getString("display", id),
                        material,
                        c.getStringList("colors"),
                        c.getDouble("luck", 0.0),
                        c.getDouble("speed", 0.0) / 100.0,
                        c.getLong("rolls", 0L),
                        c.getDouble("coin-multiplier", 1.0),
                        c.getDouble("enchant-multiplier", 1.0),
                        c.getLong("duration-seconds", 0L),
                        c.getDouble("roll-luck-multiplier", 1.0),
                        c.getLong("charges", 0L),
                        c.getDouble("permanent-luck", 0.0),
                        c.getLong("free-skills", 0L),
                        c.getLong("nova-tiers", 0L),
                        parseCosts(c.getConfigurationSection("costs")),
                        c.getString("description", "")));
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipped malformed consumable '" + id + "': " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + consumables.size() + " consumables.");
    }

    /**
     * A potion's price in rolled drops. An empty map means it isn't for
     * sale at all - some of these are only ever handed out.
     */
    private Map<com.spacerng.solrng.rarity.Rarity, Long> parseCosts(ConfigurationSection section) {
        Map<com.spacerng.solrng.rarity.Rarity, Long> costs =
                new java.util.EnumMap<>(com.spacerng.solrng.rarity.Rarity.class);
        if (section == null) return costs;
        for (String key : section.getKeys(false)) {
            try {
                costs.put(com.spacerng.solrng.rarity.Rarity.valueOf(key.toUpperCase()), section.getLong(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return costs;
    }

    /**
     * Buys one, spending the drops. All-or-nothing: the affordability
     * check runs over every rarity before a single drop is taken, so a
     * half-paid purchase can't leave somebody short and empty-handed.
     */
    public boolean purchase(Player player, PlayerData data, Consumable consumable, int amount) {
        if (consumable == null || consumable.costs().isEmpty() || amount <= 0) return false;

        for (Map.Entry<com.spacerng.solrng.rarity.Rarity, Long> cost : consumable.costs().entrySet()) {
            long needed = cost.getValue() * amount;
            if (com.spacerng.solrng.player.DropWallet.total(plugin, player, data, cost.getKey()) < needed) {
                return false;
            }
        }
        for (Map.Entry<com.spacerng.solrng.rarity.Rarity, Long> cost : consumable.costs().entrySet()) {
            com.spacerng.solrng.player.DropWallet.spend(plugin, player, data,
                    cost.getKey(), cost.getValue() * amount);
        }
        give(player, consumable, amount);
        return true;
    }

    public Map<String, Consumable> getAll() {
        return consumables;
    }

    public Consumable get(String id) {
        return id == null ? null : consumables.get(id.toLowerCase());
    }

    public NamespacedKey idKey() {
        return idKey;
    }

    // ------------------------------------------------------------ the item

    public ItemStack build(Consumable consumable, int amount) {
        ItemStack item = new ItemStack(consumable.material(), Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(styledName(consumable));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.AQUA, "What it does"));
        if (!consumable.description().isEmpty()) {
            lore.add(Lore.line(ChatColor.AQUA, consumable.description()));
        }
        lore.addAll(describe(consumable));
        lore.add("");
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD
                + ("nova_core".equals(consumable.id()) ? "Right click to forge"
                        : plugin.getCrateManager() != null
                                && plugin.getCrateManager().crateForKey(consumable.id()) != null
                                ? "Right click a crate to open" : "Right click to use"));

        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, consumable.id());
        item.setItemMeta(meta);
        return item;
    }

    /** The consumable's name in its own gradient, as its item shows it. */
    public String styledName(Consumable consumable) {
        return consumable.colors().isEmpty()
                ? ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + consumable.display()
                : plugin.getRarityManager().buildStyle(consumable.colors(), true, false, false)
                        .apply(consumable.display());
    }

    /**
     * The stat block. A draught can carry several lines at once, and a
     * minus is written as a minus rather than hidden - the trade IS the
     * item.
     */
    public java.util.List<String> describe(Consumable consumable) {
        java.util.List<String> lines = new ArrayList<>();
        if (consumable.luck() != 0.0) {
            lines.add(Lore.stat(consumable.luck() > 0 ? ChatColor.GREEN : ChatColor.RED,
                    "Luck", signed(consumable.luck() * 100) + "%"));
        }
        if (consumable.speed() != 0.0) {
            lines.add(Lore.stat(consumable.speed() > 0 ? ChatColor.YELLOW : ChatColor.RED,
                    "Speed", signed(consumable.speed() * 100)));
        }
        if (consumable.rolls() > 0) {
            lines.add(Lore.stat(ChatColor.AQUA, "Lasts", String.format("%,d", consumable.rolls()) + " rolls"));
        }
        if (consumable.coinMultiplier() > 1.0) {
            lines.add(Lore.stat(ChatColor.GOLD, "Coins",
                    trim(consumable.coinMultiplier()) + "x for " + consumable.durationText()));
        }
        if (consumable.enchantMultiplier() > 1.0) {
            lines.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Enchant chance",
                    trim(consumable.enchantMultiplier()) + "x for " + consumable.durationText()));
        }
        if (consumable.isCharge()) {
            lines.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Next roll",
                    trim(consumable.rollLuckMultiplier()) + "x Luck"
                            + (consumable.charges() > 1 ? " x" + consumable.charges() : "")));
        }
        if (consumable.isPermanent()) {
            lines.add(Lore.stat(ChatColor.GREEN, "Luck",
                    signed(consumable.permanentLuck() * 100) + "% permanently"));
        }
        return lines;
    }

    /** "+50" / "-25" - the sign is the point, so it's never dropped. */
    public static String signed(double value) {
        String number = value == Math.rint(value)
                ? String.valueOf((long) value) : String.format("%.1f", value);
        return value > 0 ? "+" + number : number;
    }

    public boolean isConsumable(ItemStack item) {
        return item != null && item.getItemMeta() != null
                && item.getItemMeta().getPersistentDataContainer().has(idKey, PersistentDataType.STRING);
    }

    public Consumable from(ItemStack item) {
        if (!isConsumable(item)) return null;
        return get(item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING));
    }

    // ----------------------------------------------------------- redeeming

    /** Uses one. Returns false only when the id no longer exists in config. */
    public boolean redeem(Player player, PlayerData data, Consumable consumable) {
        if (consumable == null) return false;

        if (consumable.isPermanent()) {
            data.addBonusLuck(consumable.permanentLuck());
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "Permanent Luck "
                    + ChatColor.RESET + ChatColor.GRAY + signed(consumable.permanentLuck() * 100)
                    + "%, and that one never runs out.");
        }
        if (consumable.isNovaCore()) {
            data.setNovaTier(data.getNovaTier() + (int) consumable.novaTiers());
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD
                    + "+" + consumable.novaTiers() + " Nova tier"
                    + (consumable.novaTiers() == 1 ? "" : "s")
                    + ChatColor.RESET + ChatColor.GRAY + "  straight up the climb.");
        }
        if (consumable.isFreeSkill()) {
            data.addFreeSkills((int) consumable.freeSkills());
            player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD
                    + "+" + consumable.freeSkills() + " free skill"
                    + (consumable.freeSkills() == 1 ? "" : "s")
                    + ChatColor.RESET + ChatColor.GRAY
                    + "  Your next purchase in /skilltree costs nothing.");
        }
        if (consumable.isCharge()) {
            data.addRollCharges(consumable.charges(), consumable.rollLuckMultiplier());
            player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Charged "
                    + ChatColor.RESET + ChatColor.GRAY + "your next "
                    + (data.getRollCharges() == 1 ? "roll rolls" : data.getRollCharges() + " rolls roll")
                    + " at " + ChatColor.LIGHT_PURPLE + trim(data.getRollChargeMultiplier()) + "x"
                    + ChatColor.GRAY + " Luck.");
        }
        if (consumable.isDraught()) {
            // One draught at a time: a second replaces the first outright
            // rather than stacking, so a minus column can't be dodged by
            // drinking something else on top of it.
            data.setPotion(consumable.luck(), consumable.speed(), consumable.rolls());
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + consumable.display()
                    + ChatColor.RESET + ChatColor.GRAY + "  "
                    + (consumable.luck() != 0
                            ? (consumable.luck() > 0 ? ChatColor.GREEN : ChatColor.RED)
                                    + signed(consumable.luck() * 100) + "% Luck" + ChatColor.GRAY + "  "
                            : "")
                    + (consumable.speed() != 0
                            ? (consumable.speed() > 0 ? ChatColor.YELLOW : ChatColor.RED)
                                    + signed(consumable.speed() * 100) + " Speed" + ChatColor.GRAY + "  "
                            : "")
                    + ChatColor.AQUA + String.format("%,d", consumable.rolls()) + " rolls");
        }
        if (consumable.isTimed()) {
            if (consumable.coinMultiplier() > 1.0) {
                data.applyBoost("TOKENS", consumable.coinMultiplier(), consumable.durationSeconds() * 1000L);
            }
            if (consumable.enchantMultiplier() > 1.0) {
                data.applyBoost("ENCHANT_PROC", consumable.enchantMultiplier(),
                        consumable.durationSeconds() * 1000L);
            }
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + consumable.display()
                    + ChatColor.RESET + ChatColor.GRAY + " for " + consumable.durationText() + ".");
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
        plugin.getScoreboardManager().update(player);
        return true;
    }

    /** Hands one over, dropping the overflow rather than eating it. */
    public void give(Player player, Consumable consumable, int amount) {
        if (consumable == null || amount <= 0) return;
        ItemStack item = build(consumable, amount);
        player.getInventory().addItem(item).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    /** "2x", "1.5x" - whole numbers without a pointless ".00". */
    public static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.2f", value);
    }
}
