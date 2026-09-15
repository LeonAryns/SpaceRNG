package com.spacerng.solrng.rarity;

import org.bukkit.Color;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The colour of a drop's block or item, for drops that have no colours of
 * their own in config. The name is drawn as a soft gradient from that
 * colour to a lighter shade of it.
 *
 * Hand-picked for every drop in the default table, because a block's map
 * colour is often not what the eye calls its colour (an ore reads as its
 * gem, not as stone). Anything missing falls back to the map colour, and
 * everything is kept readable: never darker than a mid grey on a tooltip,
 * never as bright as white next to player names in the tab list.
 */
public final class BlockColours {

    private static final Map<Material, int[]> COLOURS = new EnumMap<>(Material.class);

    private static final String[] TABLE = {
            "DIRT", "#8B6446", "COBBLESTONE", "#8A8A8A", "OAK_LOG", "#8C6A3E", "GRAVEL", "#8E8683",
            "SAND", "#DBD3A0", "GRASS_BLOCK", "#7CBD6B", "PODZOL", "#8A6534", "ANDESITE", "#8A8A8E",
            "DIORITE", "#C9C9C9", "GRANITE", "#A86F5C", "OAK_PLANKS", "#B08A55", "BIRCH_LOG", "#CFC8AE",
            "SPRUCE_LOG", "#6E5230", "JUNGLE_LOG", "#7A6230", "ACACIA_LOG", "#8A8272", "DARK_OAK_LOG", "#6A5234",
            "MANGROVE_LOG", "#7A4A38", "CHERRY_LOG", "#7A4A58", "STICK", "#9A7644", "STRING", "#DADADA",
            "FEATHER", "#E4E4E4", "FLINT", "#6A6A6A", "BONE", "#E3DDC5", "ROTTEN_FLESH", "#A5623F",
            "LEATHER", "#C0643A", "WHEAT_SEEDS", "#6BAA35", "WHEAT", "#D9B75B", "CARROT", "#F28C1F",
            "POTATO", "#D8A847", "BEETROOT", "#B8323A", "SUGAR_CANE", "#9ACD62", "BAMBOO", "#6FAA35",
            "KELP", "#5A9A38", "CACTUS", "#5E9A32", "CLAY_BALL", "#A3A8B8", "SNOWBALL", "#EEF4F4",
            "EGG", "#E7D8B1", "INK_SAC", "#5A5A78", "CHARCOAL", "#6A5A48", "TERRACOTTA", "#A8674A",
            "RED_SAND", "#C8702A", "SANDSTONE", "#D8CB9B", "MOSS_BLOCK", "#6A8E34", "MUD", "#5E5860",
            "CALCITE", "#E0E1DC", "TUFF", "#7A7B72", "DRIPSTONE_BLOCK", "#957766", "POINTED_DRIPSTONE", "#957A66",
            "GLOWSTONE_DUST", "#FFD36B", "SUGAR", "#EDEDED", "NETHERRACK", "#8A3E3E", "BASALT", "#6A6A70",
            "BLACKSTONE", "#4E4450", "SOUL_SAND", "#6A5242", "ICE", "#91B7FD", "PACKED_ICE", "#8DB4FA",
            "DEAD_BUSH", "#9A7440", "SEAGRASS", "#3F9A32", "VINE", "#4A8E24", "LILY_PAD", "#2E9A3E",
            "BROWN_MUSHROOM", "#A87E60", "RED_MUSHROOM", "#D8342C", "MELON_SLICE", "#E5463C", "PUMPKIN", "#E38A1D",
            "APPLE", "#D8342C", "BREAD", "#C0904A", "PAPER", "#E6E6DA", "COCOA_BEANS", "#8A5428",
            "SPONGE", "#C9C64E", "SUSPICIOUS_SAND", "#D7CB97", "PETRIFIED_OAK_SLAB", "#B08A55", "COAL", "#5A5A5A",
            "REDSTONE", "#E0261A", "LAPIS_LAZULI", "#3F66D6", "COPPER_INGOT", "#E07A56", "IRON_INGOT", "#D8D8D8",
            "RAW_IRON", "#D8AF93", "RAW_COPPER", "#C9704F", "RAW_GOLD", "#E8B830", "IRON_NUGGET", "#C8C8C8",
            "GOLD_NUGGET", "#F5D33C", "HONEYCOMB", "#E5A52A", "HONEY_BOTTLE", "#F0A22A", "SLIME_BALL", "#76BE6D",
            "MAGMA_CREAM", "#E87E1F", "GUNPOWDER", "#7A7A7A", "SPIDER_EYE", "#B02E44", "FERMENTED_SPIDER_EYE", "#A6546A",
            "BLAZE_POWDER", "#F5A623", "GLOW_INK_SAC", "#4FE0C8", "PHANTOM_MEMBRANE", "#BFC7C8", "RABBIT_FOOT", "#C49A6C",
            "RABBIT_HIDE", "#B08C62", "TURTLE_SCUTE", "#4FA64A", "TROPICAL_FISH", "#F28A2E", "PRISMARINE_SHARD", "#5BA396",
            "PUFFERFISH", "#E3C43D", "OBSIDIAN", "#6A4E9A", "CRYING_OBSIDIAN", "#8A4AE0", "GLOWSTONE", "#F0C36B",
            "SEA_LANTERN", "#B8D8CF", "REDSTONE_BLOCK", "#C8261A", "COAL_BLOCK", "#5A5A5A", "IRON_BLOCK", "#DCDCDC",
            "COPPER_BLOCK", "#C8704F", "LAPIS_BLOCK", "#3A5FC8", "ARROW", "#A8977A", "BOOK", "#A0683A",
            "LEATHER_HELMET", "#C0643A", "FISHING_ROD", "#9A7644", "SHEARS", "#C8C8C8", "COMPASS", "#C8483A",
            "CLOCK", "#E8C44A", "SPYGLASS", "#C8804A", "BUCKET", "#C8C8C8", "SADDLE", "#9A5A32",
            "LEAD", "#AE8A62", "NAME_TAG", "#D8C8A0", "TRIPWIRE_HOOK", "#A8A8A8", "REPEATER", "#C8605A",
            "COMPARATOR", "#C8605A", "FLINT_AND_STEEL", "#A8A8A8", "CAULDRON", "#7A7A82", "BREWING_STAND", "#D9B34A",
            "HOPPER", "#7A7A82", "PISTON", "#B89E70", "OBSERVER", "#8A8A8A", "TARGET", "#D85A4A",
            "BELL", "#F0C43A", "LANTERN", "#E8A84A", "SOUL_LANTERN", "#5AD8E0", "IRON_CHAIN", "#7A8090",
            "ANVIL", "#7A7A7A", "AMETHYST_CLUSTER", "#B98CE8", "CHAINMAIL_HELMET", "#A8A8A8", "NAUTILUS_SHELL", "#E0C8B0",
            "BEE_NEST", "#D8A847", "SNIFFER_EGG", "#B85A3A", "GOLD_INGOT", "#F5D33C", "QUARTZ", "#E6DCCF",
            "AMETHYST_SHARD", "#A77BE0", "EMERALD", "#3FD66F", "PRISMARINE_CRYSTALS", "#A8E0D0", "GOLD_BLOCK", "#F7D23E",
            "DIAMOND_ORE", "#5DE3D8", "EMERALD_ORE", "#3FD66F", "ENDER_PEARL", "#3A9E8C", "BLAZE_ROD", "#F2B33D",
            "GHAST_TEAR", "#CFE8EE", "SHULKER_SHELL", "#A876AC", "DIAMOND_HORSE_ARMOR", "#5DE3D8", "ECHO_SHARD", "#2E8AA0",
            "MUSIC_DISC_13", "#E8C44A", "TRIDENT", "#4FA89A", "CROSSBOW", "#9A7644", "SPECTRAL_ARROW", "#F0D060",
            "ENCHANTED_BOOK", "#B455E0", "EXPERIENCE_BOTTLE", "#9BE34A", "AMETHYST_BLOCK", "#9A6BD0",
            "BUDDING_AMETHYST", "#8A5EC4", "RECOVERY_COMPASS", "#3A8A9A", "BRUSH", "#C8A070", "ARMOR_STAND", "#B08A55",
            "NETHERITE_SCRAP", "#7A625A", "ANCIENT_DEBRIS", "#8A6A5E", "WITHER_ROSE", "#6A5A56", "HEART_OF_THE_SEA", "#3F6FE0",
            "BEACON", "#7FE3E3", "NETHERITE_INGOT", "#6E6468", "ENCHANTED_GOLDEN_APPLE", "#F5C542",
            "SCULK_CATALYST", "#2A8A9A", "ZOMBIE_SPAWN_EGG", "#4F9A3A", "SKELETON_SPAWN_EGG", "#C8C8C8",
            "CREEPER_SPAWN_EGG", "#3FAF3A", "SPIDER_SPAWN_EGG", "#8A4A3A", "ENDERMAN_SPAWN_EGG", "#A060D8",
            "WITCH_SPAWN_EGG", "#8A4AA0", "BLAZE_SPAWN_EGG", "#F2B33D", "PIGLIN_SPAWN_EGG", "#D8A07A",
            "GUARDIAN_SPAWN_EGG", "#5BA396", "SLIME_SPAWN_EGG", "#76BE6D", "DRAGON_EGG", "#7A4AB0",
            "WITHER_SKELETON_SPAWN_EGG", "#6A6A6A", "RAVAGER_SPAWN_EGG", "#7A766C", "ELDER_GUARDIAN_SPAWN_EGG", "#C8C0B0",
            "EVOKER_SPAWN_EGG", "#7A8484", "NETHER_STAR", "#E4ECEC", "SCULK_SHRIEKER", "#4A7A7A", "ELYTRA", "#9A9AC0",
            "CONDUIT", "#C8A878",
    };

    static {
        for (int i = 0; i + 1 < TABLE.length; i += 2) {
            Material material = Material.matchMaterial(TABLE[i]);
            if (material != null) COLOURS.put(material, parse(TABLE[i + 1]));
        }
    }

    private BlockColours() {
    }

    /** Two gradient stops for a name: the colour, and a lighter shade of it. */
    public static List<String> gradientFor(Material material) {
        int[] base = readable(base(material));
        int[] light = mix(base, new int[]{255, 250, 235}, 0.35);
        return List.of(hex(base), hex(light));
    }

    private static int[] base(Material material) {
        int[] known = COLOURS.get(material);
        if (known != null) return known;
        if (material.isBlock()) {
            try {
                Color map = material.createBlockData().getMapColor();
                return new int[]{map.getRed(), map.getGreen(), map.getBlue()};
            } catch (RuntimeException ignored) {
                // Some blocks have no map colour; fall through to the neutral one.
            }
        }
        return new int[]{190, 190, 180};
    }

    private static int[] readable(int[] rgb) {
        double lum = 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2];
        if (lum < 95.0) return mix(rgb, new int[]{255, 255, 255}, (95.0 - lum) / (255.0 - lum));
        if (lum > 215.0) {
            double scale = 215.0 / lum;
            return new int[]{(int) (rgb[0] * scale), (int) (rgb[1] * scale), (int) (rgb[2] * scale)};
        }
        return rgb;
    }

    private static int[] mix(int[] a, int[] b, double t) {
        return new int[]{
                (int) Math.round(a[0] + (b[0] - a[0]) * t),
                (int) Math.round(a[1] + (b[1] - a[1]) * t),
                (int) Math.round(a[2] + (b[2] - a[2]) * t)};
    }

    private static int[] parse(String hex) {
        return new int[]{
                Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16),
                Integer.parseInt(hex.substring(5, 7), 16)};
    }

    private static String hex(int[] rgb) {
        return String.format("#%02X%02X%02X", clamp(rgb[0]), clamp(rgb[1]), clamp(rgb[2]));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
