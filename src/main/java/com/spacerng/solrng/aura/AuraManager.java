package com.spacerng.solrng.aura;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.roll.RollAura;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Worn auras: display entities mounted straight onto the player as
 * passengers, the same way the floating tag is. The client carries
 * passengers with their vehicle every frame, so an aura follows its player
 * with no lag and no server-side movement at all. An optional particle
 * accent is drawn on top, per viewer.
 *
 * A player wearing a tag of Epic or rarer wears the aura configured for
 * that rarity under auras.tag; /rngadmin auratest puts on a test aura that
 * overrides it until switched off.
 *
 * Cleanup is layered so nothing can be left floating in the world:
 * pieces are non-persistent, so a chunk never saves them; they are removed
 * on quit, on disable, when a tag comes off and when an aura is swapped; a
 * death or a teleport drops passengers, and the next tick notices and
 * rebuilds the aura on the player; and a startup sweep removes anything
 * tagged that a crash left.
 */
public final class AuraManager {

    // Accent particles reach this far; past it they are cost with no one watching.
    private static final double ACCENT_RANGE = 32.0;

    // What each tag rarity wears when config says nothing: concept, accent.
    private static final Map<Rarity, String[]> DEFAULT_TAG_AURAS = new EnumMap<>(Map.of(
            Rarity.EPIC, new String[]{"runes", "none"},
            Rarity.LEGENDARY, new String[]{"celestial", "sparkle"},
            Rarity.MYTHICAL, new String[]{"cosmos", "trails"},
            Rarity.DIVINE, new String[]{"seraph", "all"}));

    private static final class Worn {
        final String key;
        final AuraConcept concept;
        final Rarity rarity;
        final Color color;
        final AuraAccent accent;
        final boolean test;
        List<Display> displays = List.of();
        long frame = 0L;
        boolean paused = false;

        Worn(String key, AuraConcept concept, Rarity rarity, Color color, AuraAccent accent, boolean test) {
            this.key = key;
            this.concept = concept;
            this.rarity = rarity;
            this.color = color;
            this.accent = accent;
            this.test = test;
        }
    }

    private final SolRNGPlugin plugin;
    private final NamespacedKey tag;
    private final AuraParts parts;
    private final Map<UUID, Worn> worn = new HashMap<>();
    private final Random random = new Random();
    private BukkitTask task;
    private long ticks = 0L;

    public AuraManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.tag = SolRNGPlugin.key("solrng_aura");
        this.parts = new AuraParts(tag);
    }

    public void start() {
        sweep();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
        // Anyone already online when the plugin (re)loads gets their tag's aura back.
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyTag(player);
        }
    }

    public void stop() {
        if (task != null) task.cancel();
        for (Worn aura : worn.values()) despawn(aura);
        worn.clear();
    }

    // ---------------------------------------------------------------- wearing

    /** A test aura, overriding the tag's until {@link #endTest}. False if the concept doesn't exist. */
    public boolean show(Player player, String conceptKey, Rarity rarity, AuraAccent accent) {
        return wear(player, conceptKey, rarity, accent, true);
    }

    /** Takes a test aura off and goes back to whatever the tag says. */
    public void endTest(Player player) {
        hide(player.getUniqueId());
        applyTag(player);
    }

    /**
     * Wears the aura configured for the player's equipped tag, or none if
     * the tag is below Epic, auras are off, or the tag has no aura set. A
     * test aura in progress is left alone, and the same aura already worn
     * is kept rather than rebuilt.
     */
    public void applyTag(Player player) {
        Worn current = worn.get(player.getUniqueId());
        if (current != null && current.test) return;

        Rarity rarity = tagRarity(player);
        String[] look = rarity == null || !plugin.getConfig().getBoolean("auras.enabled", true)
                ? null : lookFor(rarity);
        if (look == null) {
            hide(player.getUniqueId());
            return;
        }
        AuraAccent accent = AuraAccent.parse(look[1]);
        if (accent == null) accent = AuraAccent.NONE;
        if (current != null && current.rarity == rarity && current.key.equals(look[0]) && current.accent == accent) {
            return;
        }
        if (!wear(player, look[0], rarity, accent, false)) {
            plugin.getLogger().warning("auras.tag." + rarity.name() + " names an unknown concept: " + look[0]);
        }
    }

    /** The tag came off: take its aura off too, unless a test aura is being worn. */
    public void clearTag(UUID uuid) {
        Worn current = worn.get(uuid);
        if (current != null && !current.test) hide(uuid);
    }

    public void hide(UUID uuid) {
        Worn aura = worn.remove(uuid);
        if (aura != null) despawn(aura);
    }

    private boolean wear(Player player, String conceptKey, Rarity rarity, AuraAccent accent, boolean test) {
        Color color = RollAura.colorFor(rarity);
        AuraConcept concept = AuraConcepts.create(conceptKey, rarity, color);
        if (concept == null) return false;
        hide(player.getUniqueId());
        Worn aura = new Worn(conceptKey, concept, rarity, color, accent == null ? AuraAccent.NONE : accent, test);
        worn.put(player.getUniqueId(), aura);
        mount(player, aura);
        return true;
    }

    private Rarity tagRarity(Player player) {
        String raw = plugin.getPlayerDataManager().get(player.getUniqueId()).getEquippedTagRarity();
        if (raw == null) return null;
        try {
            return Rarity.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Concept and accent for a tag rarity, from config with the defaults underneath. Null means no aura. */
    private String[] lookFor(Rarity rarity) {
        String path = "auras.tag." + rarity.name();
        String[] fallback = DEFAULT_TAG_AURAS.get(rarity);
        String concept = plugin.getConfig().getString(path + ".concept", fallback == null ? null : fallback[0]);
        if (concept == null || concept.equalsIgnoreCase("none")) return null;
        String accent = plugin.getConfig().getString(path + ".accent", fallback == null ? "none" : fallback[1]);
        return new String[]{concept.toLowerCase(Locale.ROOT), accent};
    }

    // ------------------------------------------------------------- visibility

    /** Hides or shows every worn aura for one viewer, following their /options switch. */
    public void refreshVisibility(Player viewer) {
        boolean visible = visibleTo(viewer);
        for (Worn aura : worn.values()) {
            for (Display display : aura.displays) {
                if (visible) viewer.showEntity(plugin, display);
                else viewer.hideEntity(plugin, display);
            }
        }
    }

    private boolean visibleTo(Player viewer) {
        return plugin.getPlayerDataManager().get(viewer.getUniqueId()).isWornAurasVisible();
    }

    // ------------------------------------------------------------------- tick

    private void tick() {
        // The floating tag rides the same way and is dropped by the same teleports.
        if (ticks++ % 10 == 0) plugin.getTagManager().keepMounted();

        double pauseRadius = plugin.getConfig().getDouble("auras.farm-pause-radius", 10.0);
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
                long frame = aura.frame++;
                // Over the farm the aura steps aside: a field of players each
                // wearing one is exactly where it would cost the most.
                if (frame % 10 == 0) {
                    aura.paused = pauseRadius > 0
                            && plugin.getFarmPlotManager().isNearPlot(player.getLocation(), pauseRadius);
                }
                if (aura.paused) {
                    if (!aura.displays.isEmpty()) despawn(aura);
                    continue;
                }
                if (!mounted(player, aura)) mount(player, aura);
                aura.concept.tick(aura.displays, frame);
                if (aura.accent != AuraAccent.NONE) {
                    List<Player> audience = audience(player, aura.rarity);
                    if (!audience.isEmpty()) {
                        aura.accent.play(new AuraFx(player, audience, aura.color), aura.concept, frame, random);
                    }
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Aura on " + player.getName() + " failed and was removed: " + ex);
                despawn(aura);
                it.remove();
            }
        }
    }

    /** Players close enough to see the accent, who show worn auras and haven't switched this rarity's off. */
    private List<Player> audience(Player wearer, Rarity rarity) {
        List<Player> viewers = new ArrayList<>();
        Location at = wearer.getLocation();
        double rangeSq = ACCENT_RANGE * ACCENT_RANGE;
        for (Player viewer : wearer.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(at) > rangeSq) continue;
            var data = plugin.getPlayerDataManager().get(viewer.getUniqueId());
            if (!data.isWornAurasVisible() || !data.isAuraEnabled(rarity)) continue;
            viewers.add(viewer);
        }
        return viewers;
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
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (visibleTo(viewer)) continue;
            for (Display display : displays) {
                viewer.hideEntity(plugin, display);
            }
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
