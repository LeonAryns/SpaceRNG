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
 * the same number applied silently — and it means a crate plugin, a
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
        this.idKey = new NamespacedKey(plugin, "solrng_consumable");
    }

    public void load(FileConfiguration config) {
        consumables.clear();
        ConfigurationSection section = config.getConfigurationSection("consumables");
        if (section == null) {
            plugin.getLogger().info("[SolRNG] No consumables configured.");
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
                        Consumable.Effect.valueOf(c.getString("effect", "LUCK").toUpperCase()),
                        c.getDouble("magnitude", 1.0),
                        c.getLong("duration-seconds", 0L),
                        c.getLong("charges", 1L),
                        c.getString("description", "")));
            } catch (Exception ex) {
                plugin.getLogger().warning("[SolRNG] Skipped malformed consumable '" + id + "': " + ex.getMessage());
            }
        }
        plugin.getLogger().info("[SolRNG] Loaded " + consumables.size() + " consumables.");
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

        String name = consumable.colors().isEmpty()
                ? ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + consumable.display()
                : plugin.getRarityManager().buildStyle(consumable.colors(), true, false, false)
                        .apply(consumable.display());
        meta.setDisplayName(name);

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.AQUA, "What it does"));
        if (!consumable.description().isEmpty()) {
            lore.add(Lore.line(ChatColor.AQUA, consumable.description()));
        }
        lore.add(describe(consumable));
        lore.add("");
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "RIGHT CLICK TO USE");

        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, consumable.id());
        item.setItemMeta(meta);
        return item;
    }

    /** The one line that says what the magnitude actually is. */
    public String describe(Consumable consumable) {
        return switch (consumable.effect()) {
            case LUCK -> Lore.stat(ChatColor.GREEN, "Luck",
                    trim(consumable.magnitude()) + "x for " + consumable.durationText());
            case SPEED -> Lore.stat(ChatColor.YELLOW, "Speed",
                    trim(consumable.magnitude()) + "x for " + consumable.durationText());
            case TOKENS -> Lore.stat(ChatColor.GREEN, "Tokens",
                    trim(consumable.magnitude()) + "x for " + consumable.durationText());
            case ENCHANT_PROC -> Lore.stat(ChatColor.LIGHT_PURPLE, "Enchant chance",
                    trim(consumable.magnitude()) + "x for " + consumable.durationText());
            case ROLL_CHARGE -> Lore.stat(ChatColor.LIGHT_PURPLE, "Next roll",
                    trim(consumable.magnitude()) + "x Luck"
                            + (consumable.charges() > 1 ? " x" + consumable.charges() : ""));
            case PERMANENT_LUCK -> Lore.stat(ChatColor.GREEN, "Luck",
                    "+" + Math.round(consumable.magnitude() * 100) + "% permanently");
        };
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

        switch (consumable.effect()) {
            case PERMANENT_LUCK -> {
                data.addBonusLuck(consumable.magnitude());
                player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "PERMANENT LUCK "
                        + ChatColor.RESET + ChatColor.GRAY + "+"
                        + Math.round(consumable.magnitude() * 100) + "% — that one never runs out.");
            }
            case ROLL_CHARGE -> {
                data.addRollCharges(consumable.charges(), consumable.magnitude());
                player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "CHARGED "
                        + ChatColor.RESET + ChatColor.GRAY + "your next "
                        + (data.getRollCharges() == 1 ? "roll rolls" : data.getRollCharges() + " rolls roll")
                        + " at " + ChatColor.LIGHT_PURPLE + trim(data.getRollChargeMultiplier()) + "x"
                        + ChatColor.GRAY + " Luck.");
            }
            default -> {
                data.applyBoost(consumable.effect().key(), consumable.magnitude(),
                        consumable.durationSeconds() * 1000L);
                player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + consumable.display().toUpperCase()
                        + ChatColor.RESET + ChatColor.GRAY + " — "
                        + trim(consumable.magnitude()) + "x for " + consumable.durationText() + ".");
            }
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

    /** "2x", "1.5x" — whole numbers without a pointless ".00". */
    public static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.2f", value);
    }
}
