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
    /**
     * A tier multiplies, it does not add.
     *
     * "1.14x Coins" is a number a player can reason about against every
     * other multiplier they own; "+14% Coins" disappears into a pile of
     * additive percentages where nobody can tell what it did.
     */
    // Tiers per band of the ladder, which is also when the hoe's material changes.
    private int tiersPerBand = 10;

    public record HoeTier(String display, double coinMultiplier, double procMultiplier,
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
        this.tiersPerBand = perBand;
        int bands = Math.max(1, Math.min(rarities.length,
                config.getInt("farming.hoe-ladder.bands", 5)));
        long costStep = Math.max(1L, config.getLong("farming.hoe-ladder.cost-step", 2L));
        double coinStep = config.getDouble("farming.hoe-ladder.coin-bonus-step", 0.01);
        // Enchant proc climbs far slower than Coins on purpose: it lifts
        // every enchant at once, so the same step would be worth many
        // times more.
        double procShare = config.getDouble("farming.hoe-ladder.proc-share", 0.04);
        int blendFrom = config.getInt("farming.hoe-ladder.blend-from", 8);

        hoeTiers.clear();
        // Tier I is the hoe you are handed. Nothing was paid for it, so it
        // grants nothing, and the ladder is what you buy on top.
        hoeTiers.add(new HoeTier(roman(1), 1.0, 1.0, Map.of()));

        double coins = 0.0;
        double proc = 0.0;
        for (int band = 0; band < bands; band++) {
            var rarity = rarities[band];
            var next = band + 1 < rarities.length ? rarities[band + 1] : null;
            double perTier = coinStep * (band + 1);

            // The free base hoe IS the first tier of the first band, so
            // that band sells nine and the ladder comes to fifty rather
            // than fifty one.
            for (int step = band == 0 ? 2 : 1; step <= perBand; step++) {
                coins += perTier;
                proc += perTier * procShare;

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
                hoeTiers.add(new HoeTier(roman(hoeTiers.size() + 1),
                        1.0 + coins, 1.0 + proc, costs));
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

        ItemStack hoe = new ItemStack(hoeMaterial(tierIndexOf(data)));
        ItemMeta meta = hoe.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + hoeName + " " + ChatColor.DARK_GRAY + "["
                + ChatColor.YELLOW + tier.display() + ChatColor.DARK_GRAY + "]");

        java.util.List<String> lore = hoeLore(data, plugin.getConfig().getString("hoe-style", "classic"));

        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(boundKey, PersistentDataType.BYTE, (byte) 1);
        hoe.setItemMeta(meta);
        return hoe;
    }

    /** The looks the hoe's tooltip can take, picked with hoe-style. */
    public static final java.util.List<String> HOE_STYLES = java.util.List.of("classic", "card", "compact", "ladder");

    /**
     * The hoe's tooltip in one style. Every style carries the same facts:
     * tier, the Coin and proc multipliers, owned enchants and what the next
     * tier costs. /rngadmin hoestyles shows them side by side.
     */
    public java.util.List<String> hoeLore(com.spacerng.solrng.player.PlayerData data, String style) {
        HoeTier tier = tierOf(data);
        int index = tierIndexOf(data);
        var enchants = plugin.getHoeEnchantManager();
        // The tool's own tier and its Coin Greed both raise Coins. They're
        // shown together because the tooltip answers "what is this hoe
        // worth", not "where did each percent come from".
        double tokenBonus = data == null ? 0.0 : enchants.powerOf(data, "TOKEN_GREED");
        String coins = String.format("%.2f", tier.coinMultiplier()) + "x";
        String proc = String.format("%.2f", tier.procMultiplier()) + "x";
        String fromEnchants = "+" + String.format("%,.0f", tokenBonus * 100.0) + "%";
        String tierText = tier.display() + ChatColor.DARK_GRAY + " / " + roman(hoeTiers.size());

        record Owned(String colour, String name, int level, int cap) {
        }
        java.util.List<Owned> owned = new java.util.ArrayList<>();
        if (data != null) {
            for (var enchant : enchants.getEnchants().values()) {
                int level = enchants.levelOf(data, enchant.id());
                if (level > 0) {
                    owned.add(new Owned(String.valueOf(enchant.colour()), enchant.display(), level,
                            enchants.maxLevelFor(data, enchant)));
                }
            }
        }
        java.util.List<String> costs = new java.util.ArrayList<>();
        HoeTier next = data == null ? null : nextTier(data);
        if (next != null) {
            for (var cost : next.costs().entrySet()) {
                costs.add(ChatColor.WHITE + "" + cost.getValue() + " "
                        + plugin.getRarityManager().style(cost.getKey(), cost.getKey().displayName()));
            }
        }
        String bullet = com.spacerng.solrng.gui.Lore.BULLET;
        String footer = ChatColor.YELLOW + "" + ChatColor.BOLD + "Right-click to upgrade";

        java.util.List<String> lore = new java.util.ArrayList<>();
        switch (style == null ? "" : style.toLowerCase(java.util.Locale.ROOT)) {
            case "card" -> {
                String title = "  " + ChatColor.GOLD + ChatColor.BOLD + "Farming tool"
                        + ChatColor.DARK_GRAY + "  Tier " + ChatColor.YELLOW + tierText;
                java.util.List<String> rows = new java.util.ArrayList<>();
                rows.add(ChatColor.GOLD + bullet + " " + ChatColor.GRAY + "Coins  " + ChatColor.WHITE + coins
                        + ChatColor.DARK_GRAY + "  " + fromEnchants + " from enchants");
                rows.add(ChatColor.LIGHT_PURPLE + bullet + " " + ChatColor.GRAY + "Proc  " + ChatColor.WHITE + proc);
                for (Owned o : owned) {
                    rows.add(o.colour() + bullet + " " + ChatColor.GRAY + o.name() + "  " + ChatColor.WHITE + o.level()
                            + ChatColor.DARK_GRAY + "/" + o.cap());
                }
                if (!costs.isEmpty()) {
                    rows.add(ChatColor.AQUA + bullet + " " + ChatColor.GRAY + "Next  "
                            + String.join(ChatColor.DARK_GRAY + ", ", costs));
                }
                int widest = com.spacerng.solrng.rarity.LoreStyle.pixelWidth(title);
                for (String row : rows) widest = Math.max(widest, com.spacerng.solrng.rarity.LoreStyle.pixelWidth(row));
                String rule = ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + " ".repeat(widest / 4 + 2);
                lore.add(rule);
                lore.add(title);
                lore.add(rule);
                lore.addAll(rows);
                lore.add(rule);
                lore.add(footer);
            }
            case "compact" -> {
                lore.add(ChatColor.YELLOW + "Tier " + tierText + ChatColor.DARK_GRAY + "  ·  " + ChatColor.GOLD + coins
                        + ChatColor.GRAY + " Coins" + ChatColor.DARK_GRAY + "  ·  " + ChatColor.LIGHT_PURPLE + proc
                        + ChatColor.GRAY + " proc");
                if (owned.isEmpty()) {
                    lore.add(ChatColor.DARK_GRAY + "No enchants yet");
                }
                for (int i = 0; i < owned.size(); i += 2) {
                    StringBuilder row = new StringBuilder();
                    for (int j = i; j < Math.min(i + 2, owned.size()); j++) {
                        Owned o = owned.get(j);
                        if (j > i) row.append(ChatColor.DARK_GRAY).append("  ·  ");
                        row.append(o.colour()).append(o.name()).append(" ").append(ChatColor.WHITE).append(o.level());
                    }
                    lore.add(row.toString());
                }
                if (!costs.isEmpty()) {
                    lore.add(ChatColor.AQUA + "Next: " + String.join(ChatColor.DARK_GRAY + ", ", costs));
                }
                lore.add("");
                lore.add(footer);
            }
            case "ladder" -> {
                lore.add(ChatColor.YELLOW + "Tier " + tierText);
                lore.add(com.spacerng.solrng.gui.Lore.bar(hoeTiers.size() <= 1 ? 1.0
                        : (double) index / (hoeTiers.size() - 1)));
                lore.add("");
                lore.add(com.spacerng.solrng.gui.Lore.stat(ChatColor.GOLD, "Coins",
                        coins + ChatColor.DARK_GRAY + "  " + fromEnchants + " from enchants"));
                lore.add(com.spacerng.solrng.gui.Lore.stat(ChatColor.LIGHT_PURPLE, "Enchant proc", proc));
                lore.add("");
                for (Owned o : owned) {
                    int filled = o.cap() <= 0 ? 0 : (int) Math.round(10.0 * o.level() / o.cap());
                    lore.add(o.colour() + "▬".repeat(filled) + ChatColor.DARK_GRAY + "▬".repeat(10 - filled)
                            + " " + ChatColor.GRAY + o.name() + " " + ChatColor.WHITE + o.level());
                }
                if (owned.isEmpty()) lore.add(ChatColor.DARK_GRAY + "No enchants yet");
                if (!costs.isEmpty()) {
                    lore.add("");
                    lore.add(ChatColor.AQUA + "Next tier: " + String.join(ChatColor.DARK_GRAY + ", ", costs));
                }
                lore.add("");
                lore.add(footer);
            }
            default -> {
                lore.add(ChatColor.DARK_GRAY + "Farming Tool");
                lore.add("");
                lore.add(com.spacerng.solrng.gui.Lore.section(ChatColor.GOLD, "The tool"));
                lore.add(com.spacerng.solrng.gui.Lore.stat(ChatColor.YELLOW, "Tier", tierText));
                lore.add(com.spacerng.solrng.gui.Lore.stat(ChatColor.GOLD, "Coins",
                        coins + ChatColor.DARK_GRAY + "  " + fromEnchants + " from enchants"));
                lore.add(com.spacerng.solrng.gui.Lore.stat(ChatColor.LIGHT_PURPLE, "Enchant proc", proc));
                lore.add("");
                lore.add(com.spacerng.solrng.gui.Lore.section(ChatColor.GOLD, "Enchants"));
                for (Owned o : owned) {
                    lore.add(o.colour() + bullet + " " + ChatColor.GRAY + o.name() + ": " + ChatColor.WHITE + o.level()
                            + ChatColor.DARK_GRAY + " / " + o.cap());
                }
                if (owned.isEmpty()) lore.add(ChatColor.DARK_GRAY + bullet + " None yet");
                lore.add("");
                if (!costs.isEmpty()) {
                    lore.add(com.spacerng.solrng.gui.Lore.section(ChatColor.AQUA, "Next tier"));
                    for (String cost : costs) lore.add(ChatColor.AQUA + bullet + " " + cost);
                    lore.add("");
                }
                lore.add(footer);
            }
        }
        return lore;
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

    /** The hoe's look climbs every band of tiers: wood, stone, gold, diamond, then netherite. */
    public Material hoeMaterial(int tierIndex) {
        return switch (tierIndex / tiersPerBand) {
            case 0 -> Material.WOODEN_HOE;
            case 1 -> Material.STONE_HOE;
            case 2 -> Material.GOLDEN_HOE;
            case 3 -> Material.DIAMOND_HOE;
            default -> Material.NETHERITE_HOE;
        };
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
