package com.spacerng.solrng.aura;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.roll.RollAura;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Worn auras: display entities mounted straight onto the player as
 * passengers, the same way the floating tag is. The client carries
 * passengers with their vehicle every frame, so an aura follows its player
 * with no lag and no server-side movement at all.
 *
 * Cleanup is layered so nothing can be left floating in the world:
 * pieces are non-persistent, so a chunk never saves them; they are removed
 * on quit, on disable and when an aura is swapped; a death or a teleport
 * drops passengers, and the next tick notices and rebuilds the aura on the
 * player; and a startup sweep removes anything tagged that a crash left.
 */
public final class AuraManager {

    private static final class Worn {
        final AuraConcept concept;
        List<Display> displays = List.of();
        long frame = 0L;

        Worn(AuraConcept concept) {
            this.concept = concept;
        }
    }

    private final SolRNGPlugin plugin;
    private final NamespacedKey tag;
    private final AuraParts parts;
    private final Map<UUID, Worn> worn = new HashMap<>();
    private BukkitTask task;

    public AuraManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.tag = SolRNGPlugin.key("solrng_aura");
        this.parts = new AuraParts(tag);
    }

    public void start() {
        sweep();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (Worn aura : worn.values()) despawn(aura);
        worn.clear();
    }

    /** Puts a concept on a player in a rarity's colours. False if the concept doesn't exist. */
    public boolean show(Player player, String conceptKey, Rarity rarity) {
        AuraConcept concept = AuraConcepts.create(conceptKey, RollAura.colorFor(rarity));
        if (concept == null) return false;
        hide(player.getUniqueId());
        Worn aura = new Worn(concept);
        worn.put(player.getUniqueId(), aura);
        mount(player, aura);
        return true;
    }

    public void hide(UUID uuid) {
        Worn aura = worn.remove(uuid);
        if (aura != null) despawn(aura);
    }

    private void tick() {
        Iterator<Map.Entry<UUID, Worn>> it = worn.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Worn> entry = it.next();
            Worn aura = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                despawn(aura);
                it.remove();
                continue;
            }
            if (player.isDead()) {
                despawn(aura);
                continue;
            }
            try {
                if (!mounted(player, aura)) mount(player, aura);
                aura.concept.tick(aura.displays, aura.frame++);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Aura on " + player.getName() + " failed and was removed: " + ex);
                despawn(aura);
                it.remove();
            }
        }
    }

    private boolean mounted(Player player, Worn aura) {
        if (aura.displays.isEmpty()) return false;
        List<Entity> riders = player.getPassengers();
        for (Display display : aura.displays) {
            if (!display.isValid() || !riders.contains(display)) return false;
        }
        return true;
    }

    /** Rebuilds every piece at the player and mounts it; also how an aura comes back after a death or teleport. */
    private void mount(Player player, Worn aura) {
        despawn(aura);
        List<Display> displays = aura.concept.spawn(player, parts);
        for (Display display : displays) {
            player.addPassenger(display);
        }
        aura.displays = displays;
    }

    private void despawn(Worn aura) {
        for (Display display : aura.displays) {
            if (display.isValid()) display.remove();
        }
        aura.displays = List.of();
    }

    private void sweep() {
        for (World world : Bukkit.getWorlds()) {
            for (Display display : world.getEntitiesByClass(Display.class)) {
                if (display.getPersistentDataContainer().has(tag, PersistentDataType.BYTE)) display.remove();
            }
        }
    }
}
