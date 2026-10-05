package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * A drop is a trophy, not a tool (V345, Leon's call).
 *
 * Every drop is a real Minecraft item carrying the roll's NBT, and a few
 * of those items DO something when you right click them: a trident flies,
 * an experience bottle throws, an ender pearl teleports, a bucket empties,
 * an egg hatches. Each of those is a drop leaving the player's inventory
 * for good, and some of them (the trident especially) could be picked up
 * by somebody else, which turns a 1 in 7.5 billion item into a thing you
 * can lose to the floor.
 *
 * So nothing a drop is made of may be used, eaten, thrown, shot or
 * placed. Breaking blocks with one still works, and so does dropping or
 * storing it: this is about the item's own behaviour, not about owning it.
 */
public class DropUseListener implements Listener {

    private final SolRNGPlugin plugin;
    private final NamespacedKey rollNameKey = SolRNGPlugin.key("solrng_roll_name");

    public DropUseListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    /** True for an item carrying a roll's name, which is every drop. */
    private boolean isDrop(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        var meta = stack.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(rollNameKey, PersistentDataType.STRING);
    }

    private void refuse(Player player) {
        // The action bar, not chat: it fires on a misclick as often as on
        // a real attempt, and a chat line per misclick is noise.
        com.spacerng.solrng.gui.ActionBar.send(player,
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .deserialize(ChatColor.RED + "That is a drop. " + ChatColor.GRAY
                                + "Keep it, wear it as a tag or convert it."),
                1500L);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;
        if (!isDrop(event.getItem())) return;
        // Only the item's own use is cancelled. Right clicking a chest or
        // a crate while holding a drop still opens it.
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        if (event.getClickedBlock() == null) {
            event.setCancelled(true);
            refuse(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!isDrop(event.getItem())) return;
        event.setCancelled(true);
        refuse(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!isDrop(event.getItemInHand())) return;
        event.setCancelled(true);
        refuse(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (isDrop(event.getBow()) || isDrop(event.getConsumable())) {
            event.setCancelled(true);
            if (event.getEntity() instanceof Player player) refuse(player);
        }
    }

    /** Armour stands and item frames take an item straight out of the hand. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack held = event.getPlayer().getInventory().getItem(event.getHand());
        if (!isDrop(held)) return;
        event.setCancelled(true);
        refuse(event.getPlayer());
    }
}
