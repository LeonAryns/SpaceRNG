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
import java.util.Iterator;
import java.util.LinkedHashMap;
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
            Rarity.EPIC, new String[]{"sigil", "none"},
            Rarity.LEGENDARY, new String[]{"ember", "embers"},
            Rarity.MYTHICAL, new String[]{"eclipse", "trails"},
            Rarity.DIVINE, new String[]{"ascend", "all"}));

    private static final class Worn {
        final String key;
        // The look asked for; differs from key while a heavy look waits for room.
        final String wanted;
        final AuraConcept concept;
        final Rarity rarity;
        final Color color;
        final AuraAccent accent;
        final boolean test;
        // What the pet slots held when this was built. A pet put on or
        // taken off has to rebuild the pieces, and nothing else does.
        final String petKey;
        List<Display> displays = List.of();
        long frame = 0L;
        boolean paused = false;
        float lastYaw = Float.NaN;
        // How often the pieces had to be put back this minute. Once a spawn
        // is normal; many means something keeps taking the passengers off.
        int remounts = 0;
        long remountWindow = 0L;

        Worn(String key, String wanted, AuraConcept concept, Rarity rarity, Color color, AuraAccent accent,
             boolean test, String petKey) {
            this.petKey = petKey;
            this.key = key;
            this.wanted = wanted;
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
    // In the order auras were put on, so a heavy look stays with whoever wore it first.
    private final Map<UUID, Worn> worn = new LinkedHashMap<>();
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

        com.spacerng.solrng.player.PlayerData data =
                plugin.getPlayerDataManager().get(player.getUniqueId());

        // Pets ride in the same pieces but are not a Linked perk and do
        // not need a tag, so they are worked out before the aura and can
        // end up being the only thing worn.
        String petKey = plugin.getPetManager().signature(data);
        PetOrbit pets = plugin.getPetManager().orbit(data);

        Rarity rarity = null;
        String[] look = null;
        // Auras are a Linked perk: no Discord link, no aura.
        boolean linked = !plugin.getConfig().getBoolean("auras.require-linked", true)
                || plugin.getRankManager().rankOf(data) != null;
        if (linked) {
            rarity = tagRarity(player);
            look = rarity == null || !plugin.getConfig().getBoolean("auras.enabled", true)
                    ? null : lookFor(rarity);
            // A look picked in /aura wins over the one the tag would give.
            String choice = data.getAuraChoice();
            if (choice != null && !choice.isEmpty() && owns(data, choice)) {
                Rarity chosen = rarityOf(choice);
                if (chosen != null) {
                    rarity = chosen;
                    look = choice.endsWith(":shiny") ? shinyLookFor(chosen) : lookFor(chosen);
                }
            }
        }
        if (look == null && pets == null) {
            hide(player.getUniqueId());
            return;
        }
        AuraAccent accent = look == null ? AuraAccent.NONE : AuraAccent.parse(look[1]);
        if (accent == null) accent = AuraAccent.NONE;
        String wanted = look == null ? "" : look[0];
        if (current != null && current.rarity == rarity && current.wanted.equals(wanted)
                && current.accent == accent && current.petKey.equals(petKey)) {
            return;
        }
        if (!wear(player, wanted, wanted, rarity, accent, false, pets, petKey)) {
            plugin.getLogger().warning("auras.tag." + (rarity == null ? "?" : rarity.name())
                    + " names an unknown concept: " + wanted);
        }
    }

    /** The rarity part of an /aura choice, or null when it does not parse. */
    public static Rarity rarityOf(String choice) {
        if (choice == null || choice.isEmpty()) return null;
        try {
            return Rarity.valueOf(choice.split(":")[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Whether a player has earned an /aura choice: the plain one comes with
     * finding any drop of that rarity, the shiny one with finding a shiny of
     * it.
     */
    public boolean owns(com.spacerng.solrng.player.PlayerData data, String choice) {
        Rarity rarity = rarityOf(choice);
        if (rarity == null) return false;
        boolean shiny = choice.endsWith(":shiny");
        if (shiny && shinyLookFor(rarity) == null) return false;
        if (!shiny && lookFor(rarity) == null) return false;
        return plugin.getRarityManager().foundIn(data, rarity, shiny) > 0;
    }

    /** The shiny look for a rarity, from auras.shiny in config. Null means there is none. */
    public String[] shinyLookFor(Rarity rarity) {
        String path = "auras.shiny." + rarity.name();
        String concept = plugin.getConfig().getString(path + ".concept");
        if (concept == null || concept.equalsIgnoreCase("none")) return null;
        return new String[]{concept.toLowerCase(Locale.ROOT), plugin.getConfig().getString(path + ".accent", "sparkle")};
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
        return wear(player, conceptKey, conceptKey, rarity, accent, test);
    }

    private boolean wear(Player player, String conceptKey, String wanted, Rarity rarity, AuraAccent accent,
                         boolean test) {
        return wear(player, conceptKey, wanted, rarity, accent, test, null, "");
    }

    private boolean wear(Player player, String conceptKey, String wanted, Rarity rarity, AuraAccent accent,
                         boolean test, PetOrbit pets, String petKey) {
        // Somebody wearing pets and no aura still needs a colour for the
        // accents that will never run, so Common stands in.
        Color color = RollAura.colorFor(rarity == null ? Rarity.COMMON : rarity);
        AuraConcept look = conceptKey == null || conceptKey.isEmpty()
                ? null : AuraConcepts.create(conceptKey, rarity, color);
        if (look == null && conceptKey != null && !conceptKey.isEmpty()) return false;
        AuraConcept concept = pets == null ? look : PetOrbit.with(look, pets);
        if (concept == null) return false;
        hide(player.getUniqueId());
        Worn aura = new Worn(conceptKey, wanted, concept, rarity, color,
                accent == null ? AuraAccent.NONE : accent, test, petKey);
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
    public String[] lookFor(Rarity rarity) {
        String path = "auras.tag." + rarity.name();
        String[] fallback = DEFAULT_TAG_AURAS.get(rarity);
        String concept = plugin.getConfig().getString(path + ".concept", fallback == null ? null : fallback[0]);
        if (concept == null || concept.equalsIgnoreCase("none")) return null;
        String accent = plugin.getConfig().getString(path + ".accent", fallback == null ? "none" : fallback[1]);
        return new String[]{concept.toLowerCase(Locale.ROOT), accent};
    }

    // ------------------------------------------------------------- visibility

    /**
     * Hides or shows every worn aura for one viewer, following their
     * /options settings: Worn Auras for everyone's, and Your Own Aura for
     * the pieces of their own.
     */
    public void refreshVisibility(Player viewer) {
        for (Map.Entry<UUID, Worn> entry : worn.entrySet()) {
            boolean own = entry.getKey().equals(viewer.getUniqueId());
            Worn aura = entry.getValue();
            for (int i = 0; i < aura.displays.size(); i++) {
                Display display = aura.displays.get(i);
                if (own ? ownerSees(viewer, aura, i) : visibleTo(viewer)) viewer.showEntity(plugin, display);
                else viewer.hideEntity(plugin, display);
            }
        }
    }

    /**
     * Whether a wearer sees piece {@code index} of their own aura. Ground
     * only, the default, keeps just the pieces at the feet, so nothing
     * orbits across their view in first person.
     */
    private boolean ownerSees(Player owner, Worn aura, int index) {
        // A test is for looking at, so the tester sees every piece whatever
        // their own view is set to (V172). With the default ground-only view
        // an auratest on yourself hid everything floating around you.
        if (aura.test) return true;
        var data = plugin.getPlayerDataManager().get(owner.getUniqueId());
        if (!data.isWornAurasVisible()) return false;
        return switch (data.getOwnAuraView()) {
            case "full" -> true;
            case "hidden" -> false;
            default -> aura.concept instanceof AuraConcepts.Combined combined
                    ? combined.lowAt(index) : aura.concept.lowToGround();
        };
    }

    private boolean visibleTo(Player viewer) {
        return plugin.getPlayerDataManager().get(viewer.getUniqueId()).isWornAurasVisible();
    }

    // ------------------------------------------------------------------- tick

    private void tick() {
        // The floating tag rides the same way and is dropped by the same teleports.
        if (ticks++ % 10 == 0) plugin.getTagManager().keepMounted();
        if (ticks % 20 == 0) balanceHeavy();

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
                    // A test aura is never paused. Standing anywhere near
                    // the field would otherwise make every /rngadmin
                    // auratest silently show nothing.
                    aura.paused = !aura.test && pauseRadius > 0
                            && plugin.getFarmPlotManager().isNearPlot(player.getLocation(), pauseRadius);
                }
                if (aura.paused) {
                    if (!aura.displays.isEmpty()) despawn(aura);
                    continue;
                }
                if (!mounted(player, aura)) {
                    noteRemount(player, aura);
                    // Pieces that are only no longer riding go straight back
                    // on; rebuilding the whole look for them threw away every
                    // piece's place in its orbit and made the aura jump.
                    if (remountable(player, aura)) {
                        for (Display display : aura.displays) player.addPassenger(display);
                    } else {
                        mount(player, aura);
                        // Rebuilt pieces stand in the pose spawn() draws,
                        // which is frame 0, so the count starts over with them.
                        frame = 0;
                        aura.frame = 1;
                    }
                }
                aura.concept.tick(aura.displays, frame);
                // A look that hangs off the back turns with the body. A mounted
                // display keeps its own yaw, so it is set whenever the body has
                // turned a little; the pieces' teleport duration glides it.
                if (aura.concept.followsBody()) {
                    float yaw = player.getBodyYaw();
                    float turned = ((yaw - aura.lastYaw) % 360f + 540f) % 360f - 180f;
                    if (Float.isNaN(aura.lastYaw) || Math.abs(turned) > 4f) {
                        for (Display display : aura.displays) {
                            display.setRotation(yaw, 0f);
                        }
                        aura.lastYaw = yaw;
                    }
                }
                if (aura.accent != AuraAccent.NONE) {
                    List<Player> audience = audience(player, aura.rarity);
                    if (!audience.isEmpty()) {
                        AuraConcept drawn = aura.concept.followsBody() && !Float.isNaN(aura.lastYaw)
                                ? turnedStars(aura.concept, aura.lastYaw) : aura.concept;
                        aura.accent.play(new AuraFx(player, audience, aura.color), drawn, frame, random);
                    }
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Aura on " + player.getName() + " failed and was removed: " + ex);
                despawn(aura);
                it.remove();
            }
        }
    }

    /**
     * Heavy looks cost the most packets, so only auras.heavy.max-nearby of
     * them run within auras.heavy.radius blocks of each other. Whoever put
     * one on first keeps it; anyone past the limit wears the fallback look
     * and gets the heavy one back once there is room. Getting it back needs
     * a few blocks more than losing it, so a player standing right on the
     * edge doesn't flip between the two every check.
     */
    private void balanceHeavy() {
        int max = Math.max(0, plugin.getConfig().getInt("auras.heavy.max-nearby", 1));
        double radius = plugin.getConfig().getDouble("auras.heavy.radius", 32.0);
        String fallback = plugin.getConfig().getString("auras.heavy.fallback", "galaxy-grand").toLowerCase(Locale.ROOT);
        if (MassiveConcepts.isHeavy(fallback)) fallback = "galaxy-grand";

        record Swap(Player player, Worn aura, boolean heavy) {
        }
        List<Location> running = new ArrayList<>();
        List<Swap> swaps = new ArrayList<>();
        for (Map.Entry<UUID, Worn> entry : worn.entrySet()) {
            Worn aura = entry.getValue();
            if (!MassiveConcepts.isHeavy(aura.wanted) || aura.paused) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || player.isDead()) continue;
            Location at = player.getLocation();
            boolean heavyNow = aura.key.equals(aura.wanted);
            double reach = heavyNow ? radius : radius + 8.0;
            int near = 0;
            for (Location other : running) {
                if (other.getWorld().equals(at.getWorld()) && other.distanceSquared(at) <= reach * reach) near++;
            }
            boolean allowed = near < max;
            if (allowed) running.add(at);
            if (allowed != heavyNow) swaps.add(new Swap(player, aura, allowed));
        }
        for (Swap swap : swaps) {
            Worn aura = swap.aura();
            wear(swap.player(), swap.heavy() ? aura.wanted : fallback, aura.wanted, aura.rarity, aura.accent, aura.test);
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

    /**
     * Counts remounts and names the problem in the console (V172). A worn
     * aura is rebuilt after a death or a teleport, a handful a minute at
     * most. Dozens mean another plugin keeps removing the player's
     * passengers, which shows as pieces that never settle and a tag that
     * stutters, and no amount of re-adding fixes that from this side.
     */
    private void noteRemount(Player player, Worn aura) {
        long now = System.currentTimeMillis();
        if (now - aura.remountWindow > 60_000L) {
            aura.remountWindow = now;
            aura.remounts = 0;
        }
        if (++aura.remounts == 20) {
            List<String> riders = new ArrayList<>();
            for (Entity rider : player.getPassengers()) {
                if (!rider.getPersistentDataContainer().has(tag, PersistentDataType.BYTE)) {
                    riders.add(rider.getType().name());
                }
            }
            plugin.getLogger().warning("The aura on " + player.getName() + " had to be put back 20 times"
                    + " in a minute. Something else is taking the player's passengers off"
                    + (riders.isEmpty() ? "." : "; other riders now: " + riders + "."));
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

    /**
     * Whether the pieces only fell off and can be put straight back on. A
     * teleport and another plugin clearing passengers both leave every piece
     * alive and standing where it was, and re-adding it carries it back to
     * the player without disturbing the look. Only a piece that is gone, or
     * in another world, forces the look to be built again.
     */
    private boolean remountable(Player player, Worn aura) {
        if (aura.displays.isEmpty()) return false;
        for (Display display : aura.displays) {
            if (!display.isValid() || !display.getWorld().equals(player.getWorld())) return false;
        }
        return true;
    }

    /** Rebuilds every piece at the player and mounts it; also how an aura comes back after a death or teleport. */
    private void mount(Player player, Worn aura) {
        despawn(aura);
        List<Display> displays = aura.concept.spawn(player, parts);
        // Only a look that hangs off the body is sent turns of its own, and
        // only those want them smoothed. Every other piece leaves teleport
        // duration at 0 so it sits exactly where the wearer is.
        boolean follows = aura.concept.followsBody();
        for (Display display : displays) {
            if (follows) display.setTeleportDuration(3);
            player.addPassenger(display);
        }
        aura.displays = displays;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean own = viewer.equals(player);
            for (int i = 0; i < displays.size(); i++) {
                if (!(own ? ownerSees(viewer, aura, i) : visibleTo(viewer))) {
                    viewer.hideEntity(plugin, displays.get(i));
                }
            }
        }
        aura.lastYaw = Float.NaN;
    }

    /**
     * The same look, with its star positions turned by the body yaw the
     * pieces are drawn at, so accents land on pieces that follow the body.
     * A display at yaw y is turned by -y about the vertical axis.
     */
    private static AuraConcept turnedStars(AuraConcept look, float yaw) {
        double phi = Math.toRadians(-yaw);
        double cos = Math.cos(phi);
        double sin = Math.sin(phi);
        return new AuraConcept() {
            @Override
            public List<Display> spawn(Player player, AuraParts parts) {
                return List.of();
            }

            @Override
            public void tick(List<Display> displays, long frame) {
            }

            @Override
            public void stars(long frame, List<org.bukkit.util.Vector> out) {
                List<org.bukkit.util.Vector> local = new ArrayList<>();
                look.stars(frame, local);
                for (org.bukkit.util.Vector v : local) {
                    out.add(new org.bukkit.util.Vector(v.getX() * cos + v.getZ() * sin, v.getY(),
                            -v.getX() * sin + v.getZ() * cos));
                }
            }
        };
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
