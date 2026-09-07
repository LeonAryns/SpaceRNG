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
 * tick growth). Everyone harvests the same field - reward is scaled per
 * player by {@link com.spacerng.solrng.player.PlayerData#getFarmTokenMultiplier()}.
 * Harvesting requires the "farming_unlock" skill tree node, which also
 * grants the {@link #createBoundHoe()} item.
 */
public class FarmingManager {

    private final SolRNGPlugin plugin;
    private final NamespacedKey boundKey;
    private final Map<Material, Long> cropTokens = new EnumMap<>(Material.class);
    private final java.util.List<HoeTier> hoeTiers = new java.util.ArrayList<>();
    private String hoeName = "Farmer's Hoe";
    private int regrowTicks = 60;

    /**
     * One rung of the tool itself. A tier is a flat bonus on the hoe
     * rather than an enchant, which is why it shows in the Information
     * block: it's what the tool IS, not something bolted onto it.
     */
    /**
     * A tier is its position and its two bonuses, nothing else.
     *
     * The material used to walk wood -> stone -> iron and so on, and the
     * display name came from config. Both are gone: the hoe is always a
     * wooden hoe and its tier is the Roman numeral of where it sits in
     * this list, so a tier can never be renamed into disagreeing with its
     * own number and an old config can't reintroduce a stone one.
     */
    public record HoeTier(String display, double tokenBonus, double speedBonus,
                          Map<com.spacerng.solrng.rarity.Rarity, Long> costs) {
    }

    public FarmingManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.boundKey = SolRNGPlugin.key( "solrng_bound_hoe");
    }

    public void load(FileConfiguration config) {
        cropTokens.clear();
        regrowTicks = config.getInt("farming.regrow-seconds", 3) * 20;

        hoeName = config.getString("farming.hoe-name", "Farmer's Hoe");
        buildLadder(config);

        ConfigurationSection section = config.getConfigurationSection("farming.crops");
        if (section == null) {
            plugin.getLogger().info("Loaded 0 farming crop types.");
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                Material material = Material.valueOf(key.toUpperCase());
                cropTokens.put(material, section.getLong(key + ".tokens", 1L));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipped unknown farming crop material '" + key + "'.");
            }
        }
        plugin.getLogger().info("Loaded " + cropTokens.size() + " farming crop types.");
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

    /**
     * Builds the tier ladder from a pattern rather than a list.
     *
     * Seventy entries written out by hand would be seventy chances to
     * mistype one, and retuning the curve would mean editing every line.
     * The shape is the config: ten tiers per rarity, the cost inside a
     * band walking 2, 4, 6 ... 20 of that rarity's drops, and each tier in
     * band N worth N times the base bonus. So a Common tier is +1% Coins
     * and a Divine tier is +7%, and the full ladder comes to +280%.
     */
    private void buildLadder(FileConfiguration config) {
        var rarities = com.spacerng.solrng.rarity.Rarity.values();
        int perBand = Math.max(1, config.getInt("farming.hoe-ladder.tiers-per-band", 10));
        int bands = Math.max(1, Math.min(rarities.length,
                config.getInt("farming.hoe-ladder.bands", 5)));
        long costStep = Math.max(1L, config.getLong("farming.hoe-ladder.cost-step", 2L));
        double coinStep = config.getDouble("farming.hoe-ladder.coin-bonus-step", 0.01);
        double speedShare = config.getDouble("farming.hoe-ladder.speed-share", 0.25);
        int blendFrom = config.getInt("farming.hoe-ladder.blend-from", 8);

        hoeTiers.clear();
        // Tier I is the hoe you are handed. Nothing was paid for it, so it
        // grants nothing, and the ladder is what you buy on top.
        hoeTiers.add(new HoeTier(roman(1), 0.0, 0.0, Map.of()));

        double coins = 0.0;
        double speed = 0.0;
        for (int band = 0; band < bands; band++) {
            var rarity = rarities[band];
            var next = band + 1 < rarities.length ? rarities[band + 1] : null;
            double perTier = coinStep * (band + 1);

            for (int step = 1; step <= perBand; step++) {
                coins += perTier;
                speed += perTier * speedShare;

                Map<com.spacerng.solrng.rarity.Rarity, Long> costs =
                        new java.util.EnumMap<>(com.spacerng.solrng.rarity.Rarity.class);
                costs.put(rarity, costStep * step);
                // The last few tiers of a band ask for a taste of the next
                // rarity, so moving up a band is a slope rather than a
                // wall. The FINAL band never blends: nothing at the top of
                // the ladder is allowed to be unreachable.
                if (next != null && band + 1 < bands && step >= blendFrom) {
                    costs.put(next, (long) (step - blendFrom + 1));
                }
                hoeTiers.add(new HoeTier(roman(hoeTiers.size() + 1), coins, speed, costs));
            }
        }
    }

    public String getHoeName() {
        return hoeName;
    }

    /** The tier a player would buy next, or null at the top of the ladder. */
    public HoeTier nextTier(com.spacerng.solrng.player.PlayerData data) {
        int index = tierIndexOf(data);
        return index + 1 < hoeTiers.size() ? hoeTiers.get(index + 1) : null;
    }

    /**
     * Buys the next tier with rolled drops.
     *
     * All or nothing: the cost is checked in full before anything is spent,
     * so a player is never left having paid for half a tier.
     */
    public boolean purchaseTier(org.bukkit.entity.Player player,
                                com.spacerng.solrng.player.PlayerData data) {
        HoeTier next = nextTier(data);
        if (next == null || next.costs().isEmpty()) return false;
        if (!canAfford(player, data, next)) return false;

        for (var cost : next.costs().entrySet()) {
            com.spacerng.solrng.player.DropWallet
                    .spend(plugin, player, data, cost.getKey(), cost.getValue());
        }
        data.setHoeTier(data.getHoeTier() + 1);
        refreshHeldHoe(player, data);
        return true;
    }

    /** Every line of the price has to be covered before any of it is spent. */
    public boolean canAfford(org.bukkit.entity.Player player,
                             com.spacerng.solrng.player.PlayerData data, HoeTier tier) {
        for (var cost : tier.costs().entrySet()) {
            long held = com.spacerng.solrng.player.DropWallet
                    .total(plugin, player, data, cost.getKey());
            if (held < cost.getValue()) return false;
        }
        return true;
    }

    /** Rewrites every bound hoe the player is carrying, so the lore is true. */
    public void refreshHeldHoe(org.bukkit.entity.Player player,
                               com.spacerng.solrng.player.PlayerData data) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isBoundHoe(contents[i])) contents[i] = createBoundHoe(data);
        }
        player.getInventory().setContents(contents);
    }

    public java.util.List<HoeTier> getHoeTiers() {
        return hoeTiers;
    }

    /** Which rung of the tool ladder this player has bought up to. */
    /**
     * Tiers bought with drops, plus any the old HOE_TIER skill nodes
     * granted.
     *
     * The ladder moved off the skill tree and onto drops, but anybody who
     * already paid Coins for those nodes keeps what they paid for rather
     * than being quietly demoted.
     */
    public int tierIndexOf(com.spacerng.solrng.player.PlayerData data) {
        if (data == null) return 0;
        int legacy = (int) Math.round(plugin.getSkillTreeManager()
                .totalOf(data, com.spacerng.solrng.player.SkillNode.Effect.HOE_TIER));
        return Math.max(0, Math.min(hoeTiers.size() - 1, data.getHoeTier() + legacy));
    }

    public HoeTier tierOf(com.spacerng.solrng.player.PlayerData data) {
        return hoeTiers.get(tierIndexOf(data));
    }

    /** Reward for unlocking "farming_unlock" - soulbound via {@link #isBoundHoe}. */
    public ItemStack createBoundHoe() {
        return createBoundHoe(null);
    }

    /**
     * The hoe, with whatever enchants the owner's farming tree currently
     * justifies written into its lore. Passing null gives the plain item -
     * the enchants are derived, never stored, so a fresh copy is always
     * accurate.
     */
    public ItemStack createBoundHoe(com.spacerng.solrng.player.PlayerData data) {
        HoeTier tier = tierOf(data);

        ItemStack hoe = new ItemStack(Material.WOODEN_HOE);
        ItemMeta meta = hoe.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + hoeName + " " + ChatColor.DARK_GRAY + "["
                + ChatColor.YELLOW + tier.display() + ChatColor.DARK_GRAY + "]");

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

        lore.add(com.spacerng.solrng.gui.Lore.section(ChatColor.GOLD, "The tool"));
        lore.add(com.spacerng.solrng.gui.Lore.stat(ChatColor.YELLOW, "Tier",
                tier.display() + ChatColor.DARK_GRAY + " / " + roman(hoeTiers.size())));
        lore.add(com.spacerng.solrng.gui.Lore.stat(ChatColor.GOLD, "Coins",
                "+" + String.format("%,.0f", tokenBonus * 100.0) + "%"));
        lore.add(com.spacerng.solrng.gui.Lore.stat(ChatColor.AQUA, "Speed",
                "+" + String.format("%,.0f", speedBonus * 100.0) + "%"));
        lore.add("");

        lore.add(com.spacerng.solrng.gui.Lore.section(ChatColor.GOLD, "Enchants"));
        boolean any = false;
        if (data != null) {
            for (var enchant : enchants.getEnchants().values()) {
                int level = enchants.levelOf(data, enchant.id());
                if (level <= 0) continue;
                lore.add(enchant.colour() + com.spacerng.solrng.gui.Lore.BULLET + " "
                        + ChatColor.GRAY + enchant.display() + ": "
                        + ChatColor.WHITE + level
                        + ChatColor.DARK_GRAY + " / " + enchants.maxLevelFor(data, enchant));
                any = true;
            }
        }
        if (!any) {
            lore.add(ChatColor.DARK_GRAY + com.spacerng.solrng.gui.Lore.BULLET + " None yet");
        }
        lore.add("");

        HoeTier next = data == null ? null : nextTier(data);
        if (next != null && !next.costs().isEmpty()) {
            lore.add(com.spacerng.solrng.gui.Lore.section(ChatColor.AQUA, "Next tier"));
            for (var cost : next.costs().entrySet()) {
                lore.add(ChatColor.AQUA + com.spacerng.solrng.gui.Lore.BULLET + " "
                        + ChatColor.WHITE + cost.getValue() + " "
                        + plugin.getRarityManager().style(cost.getKey(),
                                cost.getKey().displayName()));
            }
            lore.add("");
        }
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Right-click to upgrade");

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

    /** A real converter, because the ladder runs well past X. */
    private static String roman(int value) {
        if (value < 1) return String.valueOf(value);
        int[] steps = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] marks = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < steps.length && value > 0; i++) {
            while (value >= steps[i]) {
                out.append(marks[i]);
                value -= steps[i];
            }
        }
        return out.toString();
    }

    public boolean isBoundHoe(ItemStack item) {
        return item != null && item.getItemMeta() != null
                && item.getItemMeta().getPersistentDataContainer().has(boundKey, PersistentDataType.BYTE);
    }
}
