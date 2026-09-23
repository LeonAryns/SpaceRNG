package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.cosmetic.CosmeticManager;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rank.RankTier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /cosmetics: everything a player wears that changes nothing.
 *
 * Three screens on one holder. The hub says what you are wearing and
 * opens the three pickers; titles are given out; name colours belong to
 * whichever rank carries rgb-name.
 *
 * Locked things are drawn, never hidden. Somebody with no rank should be
 * able to open this and see exactly what a rank would give them, which is
 * the only honest way to sell one.
 */
public class CosmeticsGui {

    private static final int SIZE = 45;
    private static final int SELF_SLOT = 4;
    private static final int AURA_SLOT = 20;
    private static final int TITLE_SLOT = 22;
    private static final int COLOUR_SLOT = 24;
    private static final int RANK_SLOT = 40;

    // The picker screens fill the three interior rows, left to right.
    private static final int[] PICK_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int NONE_SLOT = 39;
    private static final int BACK_SLOT = 41;

    public static int auraSlot() {
        return AURA_SLOT;
    }

    public static int titleSlot() {
        return TITLE_SLOT;
    }

    public static int colourSlot() {
        return COLOUR_SLOT;
    }

    public static int noneSlot() {
        return NONE_SLOT;
    }

    public static int backSlot() {
        return BACK_SLOT;
    }

    public static NamespacedKey pickKey() {
        return SolRNGPlugin.key("solrng_cosmetic");
    }

    /** The cosmetic id on a clicked item, or null. */
    public static String clicked(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(pickKey(), PersistentDataType.STRING);
    }

    // ---------------------------------------------------------------- the hub

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        Inventory inv = frame(CosmeticsHolder.HUB, "Cosmetics");
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        inv.setItem(SELF_SLOT, selfIcon(plugin, player, data));
        inv.setItem(AURA_SLOT, auraDoor(plugin, data));
        inv.setItem(TITLE_SLOT, titleDoor(plugin, data));
        inv.setItem(COLOUR_SLOT, colourDoor(plugin, data));
        inv.setItem(RANK_SLOT, rankNote(plugin, data));
        return inv;
    }

    private static ItemStack selfIcon(SolRNGPlugin plugin, Player player, PlayerData data) {
        CosmeticManager cosmetics = plugin.getCosmeticManager();
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) skull.setOwningPlayer(player);
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "What you wear"));

        CosmeticManager.Title title = cosmetics.title(data.getWornCosmeticTag());
        CosmeticManager.NameColour colour = cosmetics.nameColour(data.getNameColour());
        String aura = data.getAuraChoice();

        List<String> lore = new ArrayList<>();
        lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Aura",
                aura == null || aura.isEmpty() ? "whatever your tag gives" : auraLabel(aura)));
        lore.add(Lore.stat(ChatColor.AQUA, "Title",
                title == null ? "none" : cosmetics.styled(title)));
        lore.add(Lore.stat(ChatColor.GOLD, "Name colour", colour == null
                ? (cosmetics.canPickNameColour(data) ? "rainbow" : "your rank's")
                : cosmetics.styled(colour, colour.display())));
        lore.add("");
        lore.add(Lore.footnote("None of this changes a number."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack auraDoor(SolRNGPlugin plugin, PlayerData data) {
        boolean ranked = plugin.getRankManager().rankOf(data) != null;
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Auras"));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "The rings and the light you wear,");
        lore.add(ChatColor.GRAY + "and how big your rank draws them.");
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "How you get one"));
        lore.add(Lore.line(ChatColor.AQUA, "Find a drop of a rarity to wear its"));
        lore.add(Lore.line(ChatColor.AQUA, "aura, and a shiny for the shiny one."));
        lore.add("");
        if (ranked) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to pick one");
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(Lore.line(ChatColor.GRAY, "Link your Discord with /discord link."));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack titleDoor(SolRNGPlugin plugin, PlayerData data) {
        List<CosmeticManager.Title> all = plugin.getCosmeticManager().titles();
        int owned = 0;
        for (CosmeticManager.Title title : all) {
            if (data.hasCosmeticTag(title.id())) owned++;
        }
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.AQUA, "Titles"));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "A word in front of your name,");
        lore.add(ChatColor.GRAY + "in chat and in the player list.");
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "Yours", owned + " of " + all.size()));
        lore.add(Lore.line(ChatColor.AQUA, "Titles are given out, never sold."));
        lore.add("");
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to pick one");
        meta.setLore(lore);
        if (owned > 0) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack colourDoor(SolRNGPlugin plugin, PlayerData data) {
        boolean allowed = plugin.getCosmeticManager().canPickNameColour(data);
        ItemStack item = new ItemStack(allowed ? Material.FIREWORK_STAR : Material.STONE_BUTTON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GOLD, "Name Colour"));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "The gradient your name is painted");
        lore.add(ChatColor.GRAY + "in, everywhere anybody sees it.");
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "Choices",
                String.valueOf(plugin.getCosmeticManager().nameColours().size())));
        lore.add("");
        if (allowed) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to pick one");
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(Lore.line(ChatColor.GRAY, "Needs " + topRankName(plugin) + ". See /ranks."));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** The rank that carries rgb-name, so a locked line can name the way in. */
    private static String topRankName(SolRNGPlugin plugin) {
        for (RankTier tier : plugin.getRankManager().tiers()) {
            if (tier.rgbName()) return tier.display();
        }
        return "the top rank";
    }

    private static ItemStack rankNote(SolRNGPlugin plugin, PlayerData data) {
        RankTier tier = plugin.getRankManager().rankOf(data);
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "What a rank adds"));
        List<String> lore = new ArrayList<>();
        lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Your rank", plugin.getRankManager().styled(tier)));
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "Cosmetic only"));
        for (RankTier each : plugin.getRankManager().tiers()) {
            lore.add(Lore.stat(ChatColor.AQUA, each.display(),
                    String.format("%.2f", plugin.getAuraManager().rankScale(each.id())) + "x aura"
                            + (each.rgbName() ? ChatColor.DARK_GRAY + ", own name colour" : "")));
        }
        lore.add("");
        lore.add(Lore.footnote("Ranks are in /ranks."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ------------------------------------------------------------- the titles

    public static Inventory buildTitles(SolRNGPlugin plugin, Player player) {
        Inventory inv = frame(CosmeticsHolder.TITLES, "Titles");
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        List<CosmeticManager.Title> titles = plugin.getCosmeticManager().titles();

        for (int i = 0; i < PICK_SLOTS.length && i < titles.size(); i++) {
            inv.setItem(PICK_SLOTS[i], titleIcon(plugin, data, titles.get(i)));
        }
        inv.setItem(NONE_SLOT, noneIcon("No title", data.getWornCosmeticTag() == null));
        inv.setItem(BACK_SLOT, backIcon());
        return inv;
    }

    private static ItemStack titleIcon(SolRNGPlugin plugin, PlayerData data, CosmeticManager.Title title) {
        boolean owned = data.hasCosmeticTag(title.id());
        boolean worn = title.id().equals(data.getWornCosmeticTag());
        ItemStack item = new ItemStack(owned ? Material.NAME_TAG : Material.STONE_BUTTON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.getCosmeticManager().styled(title));
        List<String> lore = new ArrayList<>();
        if (!title.description().isEmpty()) {
            lore.add(ChatColor.GRAY + title.description());
            lore.add("");
        }
        lore.add(Lore.stat(ChatColor.AQUA, "Reads as",
                ChatColor.DARK_GRAY + "[" + plugin.getCosmeticManager().styled(title)
                        + ChatColor.DARK_GRAY + "] " + ChatColor.WHITE + "your name"));
        lore.add("");
        if (worn) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Worn");
        } else if (owned) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to wear");
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(Lore.line(ChatColor.GRAY, "Handed out, never bought."));
        }
        meta.setLore(lore);
        if (worn) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(pickKey(), PersistentDataType.STRING, title.id());
        item.setItemMeta(meta);
        return item;
    }

    // ------------------------------------------------------- the name colours

    public static Inventory buildColours(SolRNGPlugin plugin, Player player) {
        Inventory inv = frame(CosmeticsHolder.COLOURS, "Name Colour");
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        List<CosmeticManager.NameColour> colours = plugin.getCosmeticManager().nameColours();
        boolean allowed = plugin.getCosmeticManager().canPickNameColour(data);

        for (int i = 0; i < PICK_SLOTS.length && i < colours.size(); i++) {
            inv.setItem(PICK_SLOTS[i], colourIcon(plugin, data, colours.get(i), allowed, player.getName()));
        }
        inv.setItem(NONE_SLOT, noneIcon("Your rank's own", data.getNameColour() == null));
        inv.setItem(BACK_SLOT, backIcon());
        return inv;
    }

    private static ItemStack colourIcon(SolRNGPlugin plugin, PlayerData data,
                                        CosmeticManager.NameColour colour, boolean allowed, String name) {
        boolean worn = colour.id().equals(data.getNameColour());
        ItemStack item = new ItemStack(allowed ? Material.FIREWORK_STAR : Material.STONE_BUTTON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.getCosmeticManager().styled(colour, colour.display()));
        List<String> lore = new ArrayList<>();
        // The point of the item IS the colour, so the lore is the player's
        // own name painted in it rather than a description of it.
        lore.add(Lore.stat(ChatColor.AQUA, "Looks like",
                plugin.getCosmeticManager().styled(colour, name)));
        lore.add("");
        if (worn) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Worn");
        } else if (allowed) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to wear");
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(Lore.line(ChatColor.GRAY, "Needs " + topRankName(plugin) + ". See /ranks."));
        }
        meta.setLore(lore);
        if (worn) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(pickKey(), PersistentDataType.STRING, colour.id());
        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------- the frame

    private static Inventory frame(String section, String name) {
        CosmeticsHolder holder = new CosmeticsHolder();
        holder.setSection(section);
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + name);
        holder.setInventory(inv);
        ItemStack rail = pane(Material.MAGENTA_STAINED_GLASS_PANE);
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, i < 9 || i >= 36 ? rail : filler);
        return inv;
    }

    private static ItemStack noneIcon(String label, boolean current) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GRAY, label));
        meta.setLore(List.of(
                ChatColor.GRAY + "Wear nothing here.",
                "",
                current ? ChatColor.GREEN + "" + ChatColor.BOLD + "Worn"
                        : ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to clear"));
        if (current) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack backIcon() {
        ItemStack item = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.YELLOW, "Back"));
        meta.setLore(List.of(ChatColor.GRAY + "To your cosmetics."));
        item.setItemMeta(meta);
        return item;
    }

    /** "Divine shiny" out of an aura choice id. */
    private static String auraLabel(String choice) {
        String[] parts = choice.split(":");
        String rarity = parts[0].isEmpty() ? choice
                : parts[0].charAt(0) + parts[0].substring(1).toLowerCase(Locale.ROOT);
        return parts.length > 1 ? rarity + " shiny" : rarity;
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }
}
