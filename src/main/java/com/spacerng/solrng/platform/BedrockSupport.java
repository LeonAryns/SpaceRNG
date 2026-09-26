package com.spacerng.solrng.platform;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.holo.HoloManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/**
 * What Bedrock players are shown in place of what they cannot see (V223).
 *
 * Two jobs.
 *
 * <b>Edition twins.</b> A display tagged {@link #EDITION} "java" is hidden
 * from Bedrock players and one tagged "bedrock" is shown only to them. A
 * leaderboard wall draws its rows with a player head in front of every
 * name, and Geyser turns a head inside text into the words "[unknown
 * player head]", so the wall has a second copy without heads that only
 * Bedrock sees. The tag is read the moment an entity enters the world and
 * again for everything already standing when a Bedrock player joins, so
 * nothing depends on who was online when it was drawn.
 *
 * <b>Crates.</b> A crate is a big head floating over an invisible barrier,
 * and Bedrock has no item displays, so to a Bedrock player it was an empty
 * spot they could bump into. They get an ender chest sent on that block,
 * redrawn every two seconds the same way farm plots are, because a chunk
 * sent again would otherwise wipe it. The barrier underneath is untouched,
 * so a click on the chest reaches the crate exactly like a click on the
 * head does.
 */
public final class BedrockSupport implements Listener {

    /** "java" or "bedrock": which edition a display is drawn for. */
    public static final NamespacedKey EDITION = SolRNGPlugin.key("solrng_edition");
    public static final String JAVA = "java";
    public static final String BEDROCK = "bedrock";

    private static final long CRATE_EVERY = 40L;
    private static final double CRATE_RANGE = 96.0;

    private final SolRNGPlugin plugin;
    private BukkitTask task;

    public BedrockSupport(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) task.cancel();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> drawCrates(), 40L, CRATE_EVERY);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    /** Marks a display as drawn for one edition only. */
    public static void tag(Entity entity, String edition) {
        entity.getPersistentDataContainer().set(EDITION, PersistentDataType.STRING, edition);
        if (BEDROCK.equals(edition) && entity instanceof Display display) display.setVisibleByDefault(false);
    }

    /**
     * Tags a display that was already spawned and sorts it out for everyone
     * online now. The add event has fired by then, before the tag was on.
     */
    public void claim(Display display, String edition) {
        tag(display, edition);
        for (Player player : Bukkit.getOnlinePlayers()) {
            apply(player, display, Bedrock.is(player));
        }
    }

    /**
     * Everything a player needs for their edition, on join, after a reload
     * and when an admin switches Bedrock mode. Safe to call for Java players:
     * it gives them back anything a forced Bedrock mode hid.
     */
    public void applyTo(Player player) {
        boolean bedrock = Bedrock.is(player);
        for (World world : Bukkit.getWorlds()) {
            for (Display display : world.getEntitiesByClass(Display.class)) {
                apply(player, display, bedrock);
            }
        }
        if (bedrock) drawCrates(player);
        plugin.getAuraManager().refreshVisibility(player);
    }

    @EventHandler
    public void onAdd(EntityAddToWorldEvent event) {
        if (!(event.getEntity() instanceof Display display)) return;
        if (!display.getPersistentDataContainer().has(EDITION, PersistentDataType.STRING)) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            apply(player, display, Bedrock.is(player));
        }
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        if (Bedrock.is(event.getPlayer())) drawCrates(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Bedrock.forget(event.getPlayer().getUniqueId());
        BedrockConfirm.forget(event.getPlayer().getUniqueId());
    }

    private void apply(Player player, Display display, boolean bedrock) {
        String edition = display.getPersistentDataContainer().get(EDITION, PersistentDataType.STRING);
        if (edition == null) return;
        boolean wanted = bedrock ? BEDROCK.equals(edition) : JAVA.equals(edition);
        if (wanted) player.showEntity(plugin, display);
        else player.hideEntity(plugin, display);
    }

    private void drawCrates() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (Bedrock.is(player)) drawCrates(player);
        }
    }

    /** An ender chest on every crate block near a Bedrock player, facing where the crate faces. */
    private void drawCrates(Player player) {
        HoloManager holo = plugin.getHoloManager();
        if (holo == null) return;
        World world = player.getWorld();
        Location feet = player.getLocation();
        for (HoloManager.Spot spot : holo.list()) {
            if (spot.kind() != HoloManager.Kind.CRATE) continue;
            Location at = spot.at();
            if (at.getWorld() == null || !at.getWorld().equals(world)) continue;
            if (at.distanceSquared(feet) > CRATE_RANGE * CRATE_RANGE) continue;
            if (!world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) continue;
            Location block = at.getBlock().getLocation();
            if (block.getBlock().getType() != Material.BARRIER) continue;
            player.sendBlockChange(block, chest(spot.yaw()));
        }
    }

    private static BlockData chest(float yaw) {
        BlockData data = Material.ENDER_CHEST.createBlockData();
        if (data instanceof Directional directional) {
            directional.setFacing(facing(yaw));
        }
        return data;
    }

    /** The side a crate faces, from the yaw it was placed with. */
    private static BlockFace facing(float yaw) {
        float y = ((yaw % 360f) + 360f) % 360f;
        if (y >= 45f && y < 135f) return BlockFace.WEST;
        if (y >= 135f && y < 225f) return BlockFace.NORTH;
        if (y >= 225f && y < 315f) return BlockFace.EAST;
        return BlockFace.SOUTH;
    }
}
