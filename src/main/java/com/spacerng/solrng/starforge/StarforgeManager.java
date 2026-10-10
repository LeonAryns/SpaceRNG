package com.spacerng.solrng.starforge;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.DropWallet;
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
 * The Starforge - the item players right-click to roll and left-click to
 * toggle Auto Roll. It can't be dropped. Its tier is what sets
 * a player's BASE Luck; the skill tree and armor add on top, and the
 * index multiplier scales the whole thing.
 *
 * The tier a player has FORGED is stored on PlayerData, so losing or
 * stashing the physical item never costs them the ladder they paid for.
 * The item itself is tagged with a PersistentDataContainer key carrying
 * its own tier id, so any tier's item is recognised.
 *
 * V325: a player keeps every Starforge they have forged, Leon's call.
 * Buying a tier hands over a new item and leaves the old ones alone, and
 * the tier that actually pays is the one IN YOUR HAND. That is what makes
 * owning several worth anything: the ladder forks into Luck tiers and
 * Speed tiers, and carrying both means picking one per job rather than
 * per account. An owned tier whose item got lost is taken back out of the
 * /starforge menu with a click.
 */
public class StarforgeManager {

    public static final String DEFAULT_TIER = "STARTER";

    private final SolRNGPlugin plugin;
    private final NamespacedKey itemKey;
    private final Map<String, StarforgeTier> tiers = new LinkedHashMap<>();

    public StarforgeManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = SolRNGPlugin.key( "solrng_starforge");
    }

    public NamespacedKey getItemKey() {
        return itemKey;
    }

    public void load(FileConfiguration config) {
        tiers.clear();
        ConfigurationSection section = config.getConfigurationSection("starforge.tiers");
        if (section == null) {
            plugin.getLogger().warning("No starforge.tiers configured.");
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
                        plugin.getLogger().warning("Unknown rarity '" + rarityKey + "' in starforge tier " + id);
                    }
                }
            }

            ConfigurationSection a = t.getConfigurationSection("ability");
            StarforgeTier.Ability ability = a == null ? null : new StarforgeTier.Ability(
                    id.toLowerCase(),
                    a.getString("display", "Ability"),
                    a.getLong("duration-seconds", 30L),
                    a.getLong("cooldown-seconds", 3600L),
                    a.getDouble("speed-multiplier", 1.0),
                    a.getDouble("luck-multiplier", 1.0),
                    a.getString("description", ""));

            tiers.put(id, new StarforgeTier(
                    id,
                    t.getString("display", id),
                    t.getDouble("luck-bonus", 0.0),
                    t.getDouble("speed-bonus", 0.0),
                    ability,
                    costs,
                    order++,
                    plugin.getRarityManager().buildStyle(
                            t.getStringList("colors"),
                            t.getBoolean("bold", false),
                            t.getBoolean("underline", false),
                            t.getBoolean("strikethrough", false))));
        }
        plugin.getLogger().info("Loaded " + tiers.size() + " Starforge tiers.");
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
    /**
     * Falls back to whatever the config lists FIRST, not to a hardcoded
     * name. The default tier moved when Starter was added in front of
     * Basic, and a constant would have kept handing out the wrong one.
     */
    public StarforgeTier tierOf(PlayerData data) {
        StarforgeTier tier = tiers.get(data.getStarforgeTier());
        if (tier != null) return tier;
        StarforgeTier named = tiers.get(DEFAULT_TIER);
        if (named != null) return named;
        return tiers.values().stream().findFirst().orElse(null);
    }

    public double luckBonusOf(PlayerData data) {
        StarforgeTier tier = tierOf(data);
        return tier == null ? 0.0 : tier.getLuckBonus();
    }

    /** How long a Starforge may be out of both hands before Auto Roll stops. */
    private static final long AWAY_GRACE_MS = 1000L;
    private final Map<java.util.UUID, Long> awaySince = new java.util.concurrent.ConcurrentHashMap<>();

    /** True while a Starforge is in either hand - main or off, both count. */
    public boolean isHolding(Player player) {
        return isStarforge(player.getInventory().getItemInMainHand())
                || isStarforge(player.getInventory().getItemInOffHand());
    }

    /** The tier id written on a Starforge item, or null when it is not one. */
    public String tierIdOf(ItemStack item) {
        if (!isStarforge(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    /**
     * The tier of the Starforge actually in a player's hand (V325), main
     * hand first. Null when they are holding none, which is also when a
     * Starforge pays nothing at all.
     *
     * An item carrying a tier id that no longer exists in config falls
     * back to what they have forged, so a renamed tier never silently
     * takes somebody's Luck away.
     */
    public StarforgeTier heldTier(Player player, PlayerData data) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        String id = isStarforge(main) ? tierIdOf(main) : isStarforge(off) ? tierIdOf(off) : null;
        if (id == null) return null;
        StarforgeTier tier = tiers.get(id);
        return tier != null ? tier : tierOf(data);
    }

    /** Whether this tier is one the player has forged. */
    public boolean owns(PlayerData data, StarforgeTier tier) {
        StarforgeTier current = tierOf(data);
        return current != null && tier.getOrder() <= current.getOrder();
    }

    /**
     * Hands over one item of a tier the player already owns, for when the
     * item itself was lost or a tier was forged before V325 let anybody
     * keep the one below it.
     */
    public boolean takeOut(Player player, PlayerData data, StarforgeTier tier) {
        if (!owns(data, tier)) return false;
        // V364, Leon: one Starforge at a time. Picking a tier in
        // /starforge swaps the one you carry for it.
        replaceHeldStarforge(player, tier);
        return true;
    }

    /**
     * Recomputes every online player's Starforge Luck from whether they're
     * actually holding it, and switches Auto Roll off for anyone who's put
     * theirs away. Same live-recompute pattern as worn armor.
     */
    public void refreshHeldBonuses() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
            // V325: the tier in hand, not the best one forged. Somebody
            // carrying a Speed fork and a Luck fork gets whichever one
            // they are actually holding.
            StarforgeTier held = heldTier(player, data);
            data.setStarforgeLuckBonus(held != null ? held.getLuckBonus() : 0.0);
            data.setStarforgeSpeedBonus(held != null ? held.getSpeedBonus() : 0.0);
            boolean holding = held != null;

            // Moving the Starforge to the off hand through the inventory
            // carries it on the cursor for a moment, in neither hand, and
            // this check runs four times a second, so Auto Roll used to
            // switch off on the way (V250). On the cursor counts as still
            // held, and it has to be away for a whole second before Auto
            // Roll stops.
            if (holding || isStarforge(player.getItemOnCursor())) {
                awaySince.remove(player.getUniqueId());
            } else if (data.isAutoRollEnabled()) {
                long now = System.currentTimeMillis();
                long since = awaySince.computeIfAbsent(player.getUniqueId(), id -> now);
                if (now - since < AWAY_GRACE_MS) continue;
                awaySince.remove(player.getUniqueId());
                data.setAutoRollEnabled(false);
                player.sendActionBar(net.kyori.adventure.text.serializer.legacy
                        .LegacyComponentSerializer.legacySection().deserialize(
                                ChatColor.RED + "" + ChatColor.BOLD + "Auto Roll OFF "
                                        + ChatColor.RESET + ChatColor.GRAY + "(Starforge put away)"));
            }
        }
    }

    /**
     * Spendable drops of a rarity: rolled items in the inventory plus
     * whatever the player has banked through /convert.
     */
    public long countHeld(Player player, Rarity rarity) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        return DropWallet.total(plugin, player, data, rarity);
    }

    public boolean canAfford(Player player, StarforgeTier tier) {
        for (Map.Entry<Rarity, Long> cost : tier.getCosts().entrySet()) {
            if (countHeld(player, cost.getKey()) < cost.getValue()) return false;
        }
        return true;
    }

    /**
     * Buys a tier with rolled drops. Only upgrades - you can't buy a tier
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
            DropWallet.spend(plugin, player, data, cost.getKey(), cost.getValue());
        }

        data.setStarforgeTier(target.getId());
        // V364, Leon: a new Starforge replaces the one you carry. V325 let
        // the old ones pile up so both forks could be walked; that still
        // holds, because every tier you own can be equipped again from
        // /starforge, it just is not a second item in the inventory.
        replaceHeldStarforge(player, target);
        return true;
    }

    /**
     * Swaps every Starforge in the player's inventory for the new tier's
     * item, or just gives them one if they'd lost it.
     */
    public void replaceHeldStarforge(Player player, StarforgeTier tier) {
        // V364: the new one takes the slot of the one in hand if there is
        // one, else the first Starforge found; every other Starforge goes,
        // so the player ends up carrying exactly one.
        ItemStack[] contents = player.getInventory().getContents();
        int keep = -1;
        int hand = player.getInventory().getHeldItemSlot();
        if (hand < contents.length && isStarforge(contents[hand])) keep = hand;
        for (int i = 0; i < contents.length && keep < 0; i++) {
            if (isStarforge(contents[i])) keep = i;
        }
        boolean replacedAny = keep >= 0;
        for (int i = 0; i < contents.length; i++) {
            if (!isStarforge(contents[i])) continue;
            contents[i] = i == keep ? create(tier) : null;
        }
        player.getInventory().setContents(contents);
        if (isStarforge(player.getItemOnCursor())) player.setItemOnCursor(null);
        if (!replacedAny) {
            com.spacerng.solrng.player.Stash.give(plugin, player, create(tier));
        }
    }

    /**
     * Fires the held Starforge's ability.
     *
     * Returns the seconds still to wait when it is on cooldown, 0 on a
     * successful cast, and -1 when this tier has no ability at all, so the
     * caller can say something useful for each case.
     */
    public long activateAbility(Player player, PlayerData data) {
        // V325: the ability belongs to the Starforge in your hand.
        StarforgeTier tier = heldTier(player, data);
        if (tier == null) tier = tierOf(data);
        if (tier == null || tier.getAbility() == null) return -1L;

        StarforgeTier.Ability ability = tier.getAbility();
        long now = System.currentTimeMillis();
        long ready = data.getAbilityReadyAt();
        if (ready > now) return (ready - now + 999L) / 1000L;

        long millis = ability.durationSeconds() * 1000L;
        if (ability.speedMultiplier() != 1.0) {
            data.applyBoost("SPEED", ability.speedMultiplier(), millis);
        }
        if (ability.luckMultiplier() != 1.0) {
            data.applyBoost("LUCK", ability.luckMultiplier(), millis);
        }
        data.setAbilityReadyAt(now + ability.cooldownSeconds() * 1000L);

        player.sendMessage("");
        player.sendMessage(tier.getStyle().apply(ability.display())
                + ChatColor.RESET + ChatColor.GRAY + "  " + ability.description());
        player.sendMessage(ChatColor.DARK_GRAY + com.spacerng.solrng.gui.Lore.BULLET + " "
                + ChatColor.GRAY + "Runs for " + ChatColor.WHITE + ability.durationSeconds() + "s"
                + ChatColor.GRAY + ", back in " + ChatColor.WHITE
                + (ability.cooldownSeconds() / 60) + "m" + ChatColor.GRAY + ".");
        player.sendMessage("");
        player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_TRIDENT_THUNDER, 1.0f, 1.4f);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.8f);
        plugin.getScoreboardManager().update(player);
        return 0L;
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
        meta.setDisplayName(tier.styledDisplay());
        meta.setLore(statLines(tier));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, tier.getId());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * The shared top half of the tooltip - stats and controls. The shop
     * icon appends a price block below this; the held item stops here.
     */
    /**
     * What a tier does, shown the same way whether you own it or not.
     *
     * A locked tier hiding its Speed and its ability is the reason nobody
     * knows the ladder forks: you cannot aim at a trade you cannot see.
     */
    public List<String> statLines(StarforgeTier tier) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(com.spacerng.solrng.gui.Lore.section(ChatColor.AQUA, "While held"));
        lore.add(com.spacerng.solrng.gui.Lore.stat(ChatColor.GREEN, "Luck",
                "+" + formatPercent(tier.getLuckBonus()) + "%"));

        double speed = tier.getSpeedBonus();
        lore.add((speed < 0 ? ChatColor.RED : ChatColor.AQUA)
                + com.spacerng.solrng.gui.Lore.BULLET + " " + ChatColor.GRAY + "Speed: "
                + (speed < 0 ? ChatColor.RED : ChatColor.WHITE)
                // Speed is a stat, as on the sidebar (0.50 is +50 Speed),
                // not a percent (V245).
                + (speed < 0 ? "-" : "+") + Math.round(Math.abs(speed) * 100.0) + " Speed");

        StarforgeTier.Ability ability = tier.getAbility();
        lore.add("");
        if (ability != null) {
            lore.add(com.spacerng.solrng.gui.Lore.section(ChatColor.LIGHT_PURPLE, "Ability"));
            lore.add(ChatColor.LIGHT_PURPLE + com.spacerng.solrng.gui.Lore.BULLET + " "
                    + ChatColor.WHITE + ability.display());
            if (ability.luckMultiplier() != 1.0) {
                lore.add(com.spacerng.solrng.gui.Lore.stat(ChatColor.GREEN, "Luck",
                        trimTimes(ability.luckMultiplier())));
            }
            if (ability.speedMultiplier() != 1.0) {
                lore.add(com.spacerng.solrng.gui.Lore.stat(ChatColor.AQUA, "Speed",
                        trimTimes(ability.speedMultiplier())));
            }
            lore.add(com.spacerng.solrng.gui.Lore.stat(ChatColor.YELLOW, "Lasts",
                    ability.durationSeconds() + "s"));
            lore.add(com.spacerng.solrng.gui.Lore.stat(ChatColor.YELLOW, "Cooldown",
                    (ability.cooldownSeconds() / 60) + "m"));
            lore.add(com.spacerng.solrng.gui.Lore.footnote("Shift + right-click to fire it"));
        } else {
            lore.add(com.spacerng.solrng.gui.Lore.footnote("No ability. The last tiers have one."));
        }

        lore.add("");
        lore.add(com.spacerng.solrng.gui.Lore.footnote("Left-click toggles Auto Roll"));
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Right-click to roll");
        return lore;
    }

    private static String trimTimes(double value) {
        String text = String.format("%.2f", value);
        if (text.endsWith(".00")) text = text.substring(0, text.length() - 3);
        else if (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        return text + "x";
    }

    public static String formatPercent(double luckBonus) {
        return String.valueOf(Math.round(luckBonus * 100));
    }
}
