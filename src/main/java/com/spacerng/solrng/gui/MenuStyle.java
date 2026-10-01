package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * The menu look Leon picked from another server's screenshots (V239):
 *
 *   title   "» [NAME] «", the name bold in a gradient, centred over the
 *           chest by leading spaces measured in font pixels
 *   frame   the outer ring in a repeating three-pane pattern of the
 *           menu's palette, the inside plain grey glass
 *   close   a barrier bottom centre that closes the menu, handled once
 *           in GuiListener for every menu that uses it
 *
 * Inventory titles take components on Paper, so a title can carry hex
 * colours; the old rule that a title is legacy codes only predates that.
 */
public final class MenuStyle {

    private MenuStyle() {
    }

    /** A frame's three panes, repeating round the edge. */
    public enum Palette {
        PURPLE(Material.LIGHT_GRAY_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE),
        BLUE(Material.LIGHT_GRAY_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE),
        CYAN(Material.LIGHT_GRAY_STAINED_GLASS_PANE, Material.CYAN_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE),
        GOLD(Material.LIGHT_GRAY_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE),
        GREEN(Material.LIGHT_GRAY_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE),
        RED(Material.LIGHT_GRAY_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE, Material.PINK_STAINED_GLASS_PANE);

        private final Material[] panes;

        Palette(Material... panes) {
            this.panes = panes;
        }
    }

    public static NamespacedKey closeKey() {
        return SolRNGPlugin.key("solrng_close");
    }

    // ---------------------------------------------------------------
    // Title
    // ---------------------------------------------------------------

    /** "» [NAME] «" in the gradient, centred. Pass the name as it should read. */
    public static Component title(String name, String... stops) {
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        Component body = Component.text("» [", NamedTextColor.WHITE)
                .append(gradient(upper, stops))
                .append(Component.text("] «", NamedTextColor.WHITE));
        int width = width("» [", false) + width(upper, true) + width("] «", false);
        // The title starts 8 pixels in on a 176 pixel wide chest; a space
        // is 4 pixels wide.
        int pad = Math.max(0, Math.round(((176 - width) / 2f - 8f) / 4f));
        return Component.text(" ".repeat(pad)).append(body);
    }

    private static Component gradient(String text, String... stops) {
        Component out = Component.empty();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            double t = n == 1 ? 0.0 : (double) i / (n - 1);
            out = out.append(Component.text(String.valueOf(text.charAt(i)), colourAt(t, stops))
                    .decoration(TextDecoration.BOLD, true));
        }
        return out;
    }

    private static TextColor colourAt(double t, String... stops) {
        if (stops.length == 0) return NamedTextColor.LIGHT_PURPLE;
        if (stops.length == 1) return TextColor.fromHexString(stops[0]);
        double scaled = t * (stops.length - 1);
        int i = Math.min(stops.length - 2, (int) Math.floor(scaled));
        double f = scaled - i;
        int a = Integer.parseInt(stops[i].replace("#", ""), 16);
        int b = Integer.parseInt(stops[i + 1].replace("#", ""), 16);
        int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * f);
        int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * f);
        int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
        return TextColor.color(r, g, bl);
    }

    /** Pixel width in Minecraft's default font, including the gap after each glyph. */
    private static int width(String text, boolean bold) {
        int total = 0;
        for (char c : text.toCharArray()) {
            int w = switch (c) {
                case ' ' -> 4;
                case 'i', '!', '.', ',', ':', ';', '|', '\'' -> 2;
                case 'l', '`' -> 3;
                case 'I', 't', '[', ']', '"', '(', ')' -> 4;
                case 'f', 'k', '<', '>', '{', '}' -> 5;
                case '«', '»' -> 7;
                default -> 6;
            };
            total += w + (bold && c != ' ' ? 1 : 0);
        }
        return total;
    }

    // ---------------------------------------------------------------
    // Frame
    // ---------------------------------------------------------------

    /**
     * Paints the edge in the palette's repeating pattern and the inside in
     * grey glass. Content goes on top afterwards.
     */
    public static void frame(Inventory inv, Palette palette) {
        int size = inv.getSize();
        int rows = size / 9;
        ItemStack inside = pane(Material.GRAY_STAINED_GLASS_PANE);
        ItemStack[] edge = new ItemStack[palette.panes.length];
        for (int i = 0; i < edge.length; i++) edge[i] = pane(palette.panes[i]);
        int step = 0;
        // Clockwise from the top left, so the pattern runs round unbroken.
        for (int slot : ring(rows)) inv.setItem(slot, edge[step++ % edge.length]);
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row > 0 && row < rows - 1 && column > 0 && column < 8) inv.setItem(slot, inside);
        }
    }

    private static List<Integer> ring(int rows) {
        List<Integer> ring = new java.util.ArrayList<>();
        for (int c = 0; c < 9; c++) ring.add(c);
        for (int r = 1; r < rows; r++) ring.add(r * 9 + 8);
        for (int c = 7; c >= 0; c--) ring.add((rows - 1) * 9 + c);
        for (int r = rows - 2; r >= 1; r--) ring.add(r * 9);
        return ring;
    }

    /** The close button, bottom centre. */
    public static void close(Inventory inv) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Close");
        meta.getPersistentDataContainer().set(closeKey(), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        inv.setItem(inv.getSize() - 5, item);
    }

    public static boolean isClose(ItemStack item) {
        return item != null && item.getItemMeta() != null
                && item.getItemMeta().getPersistentDataContainer().has(closeKey(), PersistentDataType.BYTE);
    }

    public static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        meta.setHideTooltip(true);
        item.setItemMeta(meta);
        return item;
    }
}
