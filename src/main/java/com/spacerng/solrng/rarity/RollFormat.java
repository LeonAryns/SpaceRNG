package com.spacerng.solrng.rarity;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared formatting for rolled items: the "Rolled X (Rarity)" display name,
 * the Rarity/Chance lore lines, and the chat/broadcast wording. Kept in one
 * place so the item itself, chat messages, and broadcasts always agree.
 */
public final class RollFormat {

    private RollFormat() {
    }

    /**
     * The item's name in its OWN colors (per-item "colors" in config —
     * gradient/bold/etc), wrapped in the obfuscated flair for Epic-and-up
     * rarities. Falls back to the material's flat natural color for items
     * that don't define their own. Used everywhere the item is named.
     */
    public static String displayName(SolRNGPlugin plugin, RollableItem item) {
        return plugin.getRarityManager().styleItemName(item);
    }

    // Legacy chat only has 16 colors, so this is an approximation of each
    // material's real-world look rather than an exact match.
    private static final Map<Material, ChatColor> NATURAL_COLORS = Map.ofEntries(
            Map.entry(Material.ACACIA_LOG, ChatColor.GOLD),
            Map.entry(Material.AMETHYST_BLOCK, ChatColor.LIGHT_PURPLE),
            Map.entry(Material.AMETHYST_SHARD, ChatColor.LIGHT_PURPLE),
            Map.entry(Material.ANCIENT_DEBRIS, ChatColor.DARK_RED),
            Map.entry(Material.ANDESITE, ChatColor.GRAY),
            Map.entry(Material.ARMOR_STAND, ChatColor.GRAY),
            Map.entry(Material.ARROW, ChatColor.GRAY),
            Map.entry(Material.BAMBOO, ChatColor.GREEN),
            Map.entry(Material.BEACON, ChatColor.AQUA),
            Map.entry(Material.BEETROOT, ChatColor.DARK_RED),
            Map.entry(Material.BIRCH_LOG, ChatColor.WHITE),
            Map.entry(Material.BLAZE_POWDER, ChatColor.GOLD),
            Map.entry(Material.BLAZE_ROD, ChatColor.GOLD),
            Map.entry(Material.BONE, ChatColor.WHITE),
            Map.entry(Material.BOOK, ChatColor.WHITE),
            Map.entry(Material.BRUSH, ChatColor.GOLD),
            Map.entry(Material.BUCKET, ChatColor.GRAY),
            Map.entry(Material.BUDDING_AMETHYST, ChatColor.LIGHT_PURPLE),
            Map.entry(Material.CACTUS, ChatColor.GREEN),
            Map.entry(Material.CALCITE, ChatColor.WHITE),
            Map.entry(Material.CARROT, ChatColor.GOLD),
            Map.entry(Material.CHARCOAL, ChatColor.DARK_GRAY),
            Map.entry(Material.CHERRY_LOG, ChatColor.LIGHT_PURPLE),
            Map.entry(Material.CLAY_BALL, ChatColor.GRAY),
            Map.entry(Material.CLOCK, ChatColor.GOLD),
            Map.entry(Material.COAL, ChatColor.DARK_GRAY),
            Map.entry(Material.COAL_BLOCK, ChatColor.BLACK),
            Map.entry(Material.COBBLESTONE, ChatColor.GRAY),
            Map.entry(Material.COMPARATOR, ChatColor.GRAY),
            Map.entry(Material.COMPASS, ChatColor.GOLD),
            Map.entry(Material.CONDUIT, ChatColor.AQUA),
            Map.entry(Material.COPPER_BLOCK, ChatColor.GOLD),
            Map.entry(Material.COPPER_INGOT, ChatColor.GOLD),
            Map.entry(Material.CROSSBOW, ChatColor.GRAY),
            Map.entry(Material.CRYING_OBSIDIAN, ChatColor.LIGHT_PURPLE),
            Map.entry(Material.DARK_OAK_LOG, ChatColor.DARK_GRAY),
            Map.entry(Material.DIAMOND, ChatColor.AQUA),
            Map.entry(Material.DIAMOND_HORSE_ARMOR, ChatColor.AQUA),
            Map.entry(Material.DIAMOND_ORE, ChatColor.AQUA),
            Map.entry(Material.DIORITE, ChatColor.WHITE),
            Map.entry(Material.DIRT, ChatColor.GOLD),
            Map.entry(Material.DRAGON_BREATH, ChatColor.LIGHT_PURPLE),
            Map.entry(Material.DRAGON_EGG, ChatColor.DARK_PURPLE),
            Map.entry(Material.DRIPSTONE_BLOCK, ChatColor.GOLD),
            Map.entry(Material.ECHO_SHARD, ChatColor.DARK_AQUA),
            Map.entry(Material.EGG, ChatColor.WHITE),
            Map.entry(Material.ELYTRA, ChatColor.DARK_PURPLE),
            Map.entry(Material.EMERALD, ChatColor.GREEN),
            Map.entry(Material.EMERALD_ORE, ChatColor.GREEN),
            Map.entry(Material.ENCHANTED_BOOK, ChatColor.LIGHT_PURPLE),
            Map.entry(Material.ENDER_EYE, ChatColor.DARK_GREEN),
            Map.entry(Material.ENDER_PEARL, ChatColor.DARK_AQUA),
            Map.entry(Material.END_CRYSTAL, ChatColor.LIGHT_PURPLE),
            Map.entry(Material.EXPERIENCE_BOTTLE, ChatColor.GREEN),
            Map.entry(Material.FEATHER, ChatColor.WHITE),
            Map.entry(Material.FERMENTED_SPIDER_EYE, ChatColor.DARK_RED),
            Map.entry(Material.FISHING_ROD, ChatColor.GOLD),
            Map.entry(Material.FLINT, ChatColor.DARK_GRAY),
            Map.entry(Material.GHAST_TEAR, ChatColor.WHITE),
            Map.entry(Material.GLOWSTONE, ChatColor.YELLOW),
            Map.entry(Material.GLOWSTONE_DUST, ChatColor.YELLOW),
            Map.entry(Material.GLOW_INK_SAC, ChatColor.AQUA),
            Map.entry(Material.GOLDEN_APPLE, ChatColor.YELLOW),
            Map.entry(Material.GOLD_BLOCK, ChatColor.YELLOW),
            Map.entry(Material.GOLD_INGOT, ChatColor.YELLOW),
            Map.entry(Material.GOLD_NUGGET, ChatColor.YELLOW),
            Map.entry(Material.GRANITE, ChatColor.RED),
            Map.entry(Material.GRASS_BLOCK, ChatColor.GREEN),
            Map.entry(Material.GRAVEL, ChatColor.GRAY),
            Map.entry(Material.GUNPOWDER, ChatColor.DARK_GRAY),
            Map.entry(Material.HEART_OF_THE_SEA, ChatColor.AQUA),
            Map.entry(Material.HONEYCOMB, ChatColor.GOLD),
            Map.entry(Material.HONEY_BOTTLE, ChatColor.GOLD),
            Map.entry(Material.INK_SAC, ChatColor.BLACK),
            Map.entry(Material.IRON_BLOCK, ChatColor.WHITE),
            Map.entry(Material.IRON_INGOT, ChatColor.WHITE),
            Map.entry(Material.IRON_NUGGET, ChatColor.WHITE),
            Map.entry(Material.JUNGLE_LOG, ChatColor.GOLD),
            Map.entry(Material.KELP, ChatColor.DARK_GREEN),
            Map.entry(Material.LAPIS_BLOCK, ChatColor.BLUE),
            Map.entry(Material.LAPIS_LAZULI, ChatColor.BLUE),
            Map.entry(Material.LEAD, ChatColor.GRAY),
            Map.entry(Material.LEATHER, ChatColor.GOLD),
            Map.entry(Material.LEATHER_HELMET, ChatColor.GOLD),
            Map.entry(Material.MAGMA_CREAM, ChatColor.RED),
            Map.entry(Material.MANGROVE_LOG, ChatColor.DARK_RED),
            Map.entry(Material.MOSS_BLOCK, ChatColor.DARK_GREEN),
            Map.entry(Material.MUD, ChatColor.DARK_GRAY),
            Map.entry(Material.MUSIC_DISC_13, ChatColor.BLACK),
            Map.entry(Material.NAME_TAG, ChatColor.WHITE),
            Map.entry(Material.NAUTILUS_SHELL, ChatColor.WHITE),
            Map.entry(Material.NETHERITE_BLOCK, ChatColor.DARK_GRAY),
            Map.entry(Material.NETHERITE_INGOT, ChatColor.DARK_GRAY),
            Map.entry(Material.NETHERITE_SCRAP, ChatColor.DARK_GRAY),
            Map.entry(Material.NETHER_STAR, ChatColor.WHITE),
            Map.entry(Material.OAK_LOG, ChatColor.GOLD),
            Map.entry(Material.OAK_PLANKS, ChatColor.GOLD),
            Map.entry(Material.OBSIDIAN, ChatColor.DARK_PURPLE),
            Map.entry(Material.PHANTOM_MEMBRANE, ChatColor.GRAY),
            Map.entry(Material.PODZOL, ChatColor.DARK_RED),
            Map.entry(Material.POINTED_DRIPSTONE, ChatColor.GOLD),
            Map.entry(Material.POTATO, ChatColor.GOLD),
            Map.entry(Material.PRISMARINE_CRYSTALS, ChatColor.AQUA),
            Map.entry(Material.PRISMARINE_SHARD, ChatColor.AQUA),
            Map.entry(Material.PUFFERFISH, ChatColor.YELLOW),
            Map.entry(Material.QUARTZ, ChatColor.WHITE),
            Map.entry(Material.RABBIT_FOOT, ChatColor.WHITE),
            Map.entry(Material.RABBIT_HIDE, ChatColor.GOLD),
            Map.entry(Material.RAW_COPPER, ChatColor.GOLD),
            Map.entry(Material.RAW_GOLD, ChatColor.YELLOW),
            Map.entry(Material.RAW_IRON, ChatColor.WHITE),
            Map.entry(Material.RECOVERY_COMPASS, ChatColor.DARK_PURPLE),
            Map.entry(Material.REDSTONE, ChatColor.RED),
            Map.entry(Material.REDSTONE_BLOCK, ChatColor.RED),
            Map.entry(Material.RED_SAND, ChatColor.RED),
            Map.entry(Material.REPEATER, ChatColor.GRAY),
            Map.entry(Material.ROTTEN_FLESH, ChatColor.DARK_GREEN),
            Map.entry(Material.SADDLE, ChatColor.GOLD),
            Map.entry(Material.SAND, ChatColor.YELLOW),
            Map.entry(Material.SANDSTONE, ChatColor.YELLOW),
            Map.entry(Material.SEA_LANTERN, ChatColor.AQUA),
            Map.entry(Material.SHEARS, ChatColor.GRAY),
            Map.entry(Material.SHULKER_BOX, ChatColor.LIGHT_PURPLE),
            Map.entry(Material.SHULKER_SHELL, ChatColor.LIGHT_PURPLE),
            Map.entry(Material.SLIME_BALL, ChatColor.GREEN),
            Map.entry(Material.SNOWBALL, ChatColor.WHITE),
            Map.entry(Material.SPECTRAL_ARROW, ChatColor.YELLOW),
            Map.entry(Material.SPIDER_EYE, ChatColor.RED),
            Map.entry(Material.SPRUCE_LOG, ChatColor.DARK_RED),
            Map.entry(Material.SPYGLASS, ChatColor.GRAY),
            Map.entry(Material.STICK, ChatColor.GOLD),
            Map.entry(Material.STRING, ChatColor.WHITE),
            Map.entry(Material.SUGAR, ChatColor.WHITE),
            Map.entry(Material.SUGAR_CANE, ChatColor.GREEN),
            Map.entry(Material.TERRACOTTA, ChatColor.RED),
            Map.entry(Material.TOTEM_OF_UNDYING, ChatColor.YELLOW),
            Map.entry(Material.TRIDENT, ChatColor.AQUA),
            Map.entry(Material.TRIPWIRE_HOOK, ChatColor.GRAY),
            Map.entry(Material.TROPICAL_FISH, ChatColor.YELLOW),
            Map.entry(Material.TUFF, ChatColor.DARK_GRAY),
            Map.entry(Material.TURTLE_SCUTE, ChatColor.GREEN),
            Map.entry(Material.WHEAT, ChatColor.YELLOW),
            Map.entry(Material.WHEAT_SEEDS, ChatColor.GOLD),

            // Epic (mobs) / Legendary (eggs) / Mythical (bosses) items.
            Map.entry(Material.ZOMBIE_SPAWN_EGG, ChatColor.GREEN),
            Map.entry(Material.SKELETON_SPAWN_EGG, ChatColor.WHITE),
            Map.entry(Material.CREEPER_SPAWN_EGG, ChatColor.DARK_GREEN),
            Map.entry(Material.SPIDER_SPAWN_EGG, ChatColor.DARK_GRAY),
            Map.entry(Material.ENDERMAN_SPAWN_EGG, ChatColor.DARK_PURPLE),
            Map.entry(Material.WITCH_SPAWN_EGG, ChatColor.LIGHT_PURPLE),
            Map.entry(Material.BLAZE_SPAWN_EGG, ChatColor.GOLD),
            Map.entry(Material.PIGLIN_SPAWN_EGG, ChatColor.GOLD),
            Map.entry(Material.GUARDIAN_SPAWN_EGG, ChatColor.AQUA),
            Map.entry(Material.SLIME_SPAWN_EGG, ChatColor.GREEN),
            Map.entry(Material.WITHER_SKELETON_SPAWN_EGG, ChatColor.DARK_GRAY),
            Map.entry(Material.RAVAGER_SPAWN_EGG, ChatColor.RED),
            Map.entry(Material.ELDER_GUARDIAN_SPAWN_EGG, ChatColor.DARK_AQUA),
            Map.entry(Material.EVOKER_SPAWN_EGG, ChatColor.DARK_GRAY)
    );

    /**
     * Best-effort match to how the material actually looks — legacy chat
     * only has 16 colors, so this is an approximation, not exact.
     */
    public static ChatColor naturalColor(Material material) {
        return NATURAL_COLORS.getOrDefault(material, ChatColor.WHITE);
    }

    /**
     * Lore lines showing the item's rarity and its odds, both as a chat
     * message suffix and as stat lines on the item itself.
     */
    public static List<String> lore(SolRNGPlugin plugin, RollableItem item) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Rarity: " + plugin.getRarityManager().style(item.getRarity(), item.getRarity().displayName()));
        lore.add(ChatColor.GRAY + "Chance: " + plugin.getRarityManager().style(item.getRarity(), chance(item.getOdds())));
        return lore;
    }

    /**
     * "1 in 500" style odds string, used in item lore.
     */
    public static String chance(long odds) {
        return "1 in " + odds;
    }

    /**
     * Compact "1/5.1M" style odds, used in chat/broadcast messages where
     * a full number would be too long to read at a glance.
     */
    public static String compactOdds(long odds) {
        return "1/" + abbreviate(odds);
    }

    /** "1234" -> "1.2K", "5100000" -> "5.1M", etc. — also used for wallet balances. */
    public static String abbreviate(long n) {
        if (n < 1_000) return String.valueOf(n);
        if (n < 1_000_000) return trimZero(n / 1_000.0) + "K";
        if (n < 1_000_000_000L) return trimZero(n / 1_000_000.0) + "M";
        return trimZero(n / 1_000_000_000.0) + "B";
    }

    private static String trimZero(double value) {
        String formatted = String.format("%.1f", value);
        return formatted.endsWith(".0") ? formatted.substring(0, formatted.length() - 2) : formatted;
    }

    /**
     * The chat line every player sees for their own roll, e.g.
     * "⚡ You rolled Sand [COMMON] (1/67)".
     */
    public static String personalRollLine(SolRNGPlugin plugin, RollableItem item) {
        return ChatColor.AQUA + "⚡ " + ChatColor.WHITE + "You rolled " + displayName(plugin, item) + " "
                + ChatColor.GRAY + "[" + plugin.getRarityManager().style(item.getRarity(), item.getRarity().name()) + ChatColor.GRAY + "] "
                + ChatColor.GRAY + "(" + compactOdds(item.getOdds()) + ")";
    }

    /**
     * The server-wide banner for a rare drop, e.g.
     * "✦ LEGENDARY DROP ✦" / "Player just found Fallen Star [LEGENDARY]" /
     * "Odds: 1/1M"
     */
    public static String broadcastBanner(SolRNGPlugin plugin, String playerName, RollableItem item) {
        Rarity rarity = item.getRarity();
        String rarityWord = rarity.name();
        return plugin.getRarityManager().style(rarity, "✦ " + rarityWord + " DROP ✦") + "\n"
                + ChatColor.WHITE + playerName + ChatColor.GRAY + " just found " + displayName(plugin, item) + " "
                + ChatColor.GRAY + "[" + plugin.getRarityManager().style(rarity, rarityWord) + ChatColor.GRAY + "]" + "\n"
                + ChatColor.GRAY + "Odds: " + ChatColor.WHITE + compactOdds(item.getOdds());
    }
}
