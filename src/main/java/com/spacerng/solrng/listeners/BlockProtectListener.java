package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Nobody breaks the world except the farm (V251). With
 * {@code protection.block-break} on, a break is refused unless the block is
 * a farm plot or a farm crop, which their own listeners handle. Someone
 * with solrng.admin in creative still builds as normal. Switched with
 * /rngadmin protect on|off.
 */
public class BlockProtectListener implements Listener {

    private final SolRNGPlugin plugin;

    public BlockProtectListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!guarded(event.getPlayer())) return;
        Block block = event.getBlock();
        if (plugin.getFarmPlotManager().isPlot(block.getLocation())) return;
        if (plugin.getFarmingManager().isCrop(block.getType())) return;
        event.setCancelled(true);
    }

    /** Whether this player is held to the protection right now. */
    private boolean guarded(Player player) {
        if (!plugin.getConfig().getBoolean("protection.block-break", true)) return false;
        return !(player.getGameMode() == GameMode.CREATIVE && player.hasPermission("solrng.admin"));
    }

    /**
     * Nobody drops items (V262); /trash is the way to get rid of one.
     * solrng.admin in creative still can.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission("solrng.admin")) return;
        event.setCancelled(true);
    }

    /** No placing either (V256), with the same admin-in-creative exception. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        if (guarded(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
        if (guarded(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketFill(org.bukkit.event.player.PlayerBucketFillEvent event) {
        if (guarded(event.getPlayer())) event.setCancelled(true);
    }

    /** Paintings and item frames are blocks to a player, so they are kept too. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingBreak(org.bukkit.event.hanging.HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player && guarded(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingPlace(org.bukkit.event.hanging.HangingPlaceEvent event) {
        if (event.getPlayer() != null && guarded(event.getPlayer())) event.setCancelled(true);
    }

    /**
     * No vanilla workbenches or containers (V355, Leon's ask).
     *
     * Everything in this plugin is a command or a menu, so a crafting
     * table, a chest or an anvil is only ever a way around something: a
     * chest is storage the plugin does not know about, a crafting table
     * turns drops into blocks, an enchanting table competes with the hoe.
     * The list is in protection.blocked-blocks so Leon can open one up
     * without a jar.
     *
     * This cancels the interaction rather than the inventory, so the
     * plugin's own menus - which are created inventories with no block
     * under them - are never touched.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player player = event.getPlayer();
        if (!guarded(player)) return;
        if (!blocked(block.getType())) return;
        event.setCancelled(true);
        // One message, not one per click: a held right click fires this
        // several times a second and a chat line each time is unreadable.
        com.spacerng.solrng.gui.ActionBar.send(player,
                net.kyori.adventure.text.Component.text("That is not used here. Everything is a command or a menu.",
                        net.kyori.adventure.text.format.NamedTextColor.RED),
                1500L);
    }

    /** The blocked set, read from config once and cached per reload. */
    private java.util.Set<org.bukkit.Material> blockedCache;
    private int blockedStamp = -1;

    private boolean blocked(org.bukkit.Material material) {
        int stamp = System.identityHashCode(plugin.getConfig());
        if (blockedCache == null || blockedStamp != stamp) {
            java.util.Set<org.bukkit.Material> set = java.util.EnumSet.noneOf(org.bukkit.Material.class);
            for (String raw : plugin.getConfig().getStringList("protection.blocked-blocks")) {
                org.bukkit.Material found = org.bukkit.Material.matchMaterial(raw);
                if (found != null) {
                    set.add(found);
                } else {
                    plugin.getLogger().warning("protection.blocked-blocks: no such block '" + raw + "'");
                }
            }
            blockedCache = set;
            blockedStamp = stamp;
        }
        return blockedCache.contains(material);
    }

    /**
     * No vanilla advancements (V256). The data pack route from
     * /rngadmin advancements needs a reload and a format the server
     * accepts; this stops every minecraft: criterion from being granted at
     * all, so there is no toast and no chat line, from the moment the
     * plugin loads. advancements.block in config, on by default.
     */
    @EventHandler(ignoreCancelled = true)
    public void onAdvancement(com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent event) {
        if (!plugin.getConfig().getBoolean("advancements.block", true)) return;
        if (event.getAdvancement().getKey().getNamespace().equals("minecraft")) event.setCancelled(true);
    }

    /**
     * Bought armor never wears out (V253). New pieces are marked
     * unbreakable; this covers the pieces handed out before that.
     */
    @EventHandler(ignoreCancelled = true)
    public void onArmorDamage(org.bukkit.event.player.PlayerItemDamageEvent event) {
        if (plugin.getArmorManager().isPluginArmor(event.getItem())) event.setCancelled(true);
    }
}
