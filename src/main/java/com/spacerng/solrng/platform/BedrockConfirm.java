package com.spacerng.solrng.platform;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A second tap before a Bedrock player spends Credits (V223).
 *
 * On a phone or a tablet the only way to read an item's description is to
 * tap it, and Geyser sends that tap to the server as a click. So a Bedrock
 * player who opened /ranks to read what Supernova gives had already bought
 * it. For them the first tap on something that costs Credits only arms it:
 * the item turns into "Tap again to buy" and a second tap on the same one
 * within a few seconds buys it. Java players are never asked.
 */
public final class BedrockConfirm {

    private static final long WINDOW_MILLIS = 6000L;

    private static final Map<UUID, String> armed = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> armedAt = new ConcurrentHashMap<>();

    private BedrockConfirm() {
    }

    /**
     * Whether this click should go through. Always true on Java. On Bedrock
     * only for the second tap on the same {@code what} inside the window;
     * the first one relabels the tapped item and returns false.
     */
    public static boolean go(Player player, InventoryClickEvent event, String what) {
        if (!Bedrock.is(player)) return true;
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long at = armedAt.get(id);
        if (what.equals(armed.get(id)) && at != null && now - at <= WINDOW_MILLIS) {
            armed.remove(id);
            armedAt.remove(id);
            return true;
        }
        armed.put(id, what);
        armedAt.put(id, now);
        ItemStack shown = event.getCurrentItem();
        if (shown != null && shown.getItemMeta() != null && event.getClickedInventory() != null) {
            ItemStack relabelled = shown.clone();
            ItemMeta meta = relabelled.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "Tap again to buy");
            relabelled.setItemMeta(meta);
            event.getClickedInventory().setItem(event.getSlot(), relabelled);
        }
        player.sendMessage(ChatColor.YELLOW + "Tap it again to buy. " + ChatColor.GRAY
                + "Nothing has been spent yet.");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.8f);
        return false;
    }

    public static void forget(UUID id) {
        armed.remove(id);
        armedAt.remove(id);
    }
}
