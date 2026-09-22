package com.spacerng.solrng.roll;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.rarity.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import com.spacerng.solrng.aura.AuraParts;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The beat in front of a shiny roll. The shiny is decided before the roll
 * starts, so instead of hiding it until the end the roll holds for a moment
 * and says so: a pale aqua double helix climbs the player while a chime
 * ladder rises, everything goes quiet for a breath, then a flash. Only
 * after that does the normal reel, or a big drop's aura, begin.
 *
 * Aqua and pale, never gold: gold is Legendary's colour in RollAura, and a
 * shiny Epic that opened in gold would read as a Legendary.
 *
 * Driven frame by frame from the roll timer, which steps every 2 ticks, so
 * there is no task of its own that could outlive the roll. Bystanders
 * within 10 blocks see and hear it unless they switched even the Epic aura
 * off in /options, the lowest spectacle toggle there is.
 */
public final class ShinyPreRoll {

    /** Length of the pre-roll in ticks. The roll timer steps in twos, so keep it even. */
    public static final long TICKS = 24L;

    private static final double VIEW_RANGE = 10.0;
    // From here to the last frame is the hush before the flash.
    private static final double HUSH_FROM = 0.75;
    private static final int RING_SEGMENTS = 10;
    private static final Color AQUA = Color.fromRGB(85, 255, 255);
    // Warm near-white: a pure 255,255,255 dust cloud reads as a rendering glitch.
    private static final Color PALE = Color.fromRGB(235, 255, 252);

    private final SolRNGPlugin plugin;
    private final Player player;
    private final Particle.DustTransition strand = new Particle.DustTransition(AQUA, PALE, 1.1f);
    private final Particle.DustOptions bright = new Particle.DustOptions(PALE, 2.2f);
    private final List<Player> audience = new ArrayList<>();
    private final List<Display> ring = new ArrayList<>();
    private int frames = 0;
    private boolean cast = false;
    private boolean failed = false;

    public ShinyPreRoll(SolRNGPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    /**
     * One frame. {@code progress} climbs from just above 0 to exactly 1.0
     * on the last frame, which is the flash.
     */
    public void frame(double progress, boolean showTitle) {
        if (failed || cast || !player.isOnline()) return;
        try {
            refreshAudience();
            if (frames++ == 0) open(showTitle);
            if (progress >= 1.0) {
                flash();
                cast = true;
            } else if (progress < HUSH_FROM) {
                charge(progress / HUSH_FROM);
            } else {
                hush((progress - HUSH_FROM) / (1.0 - HUSH_FROM));
            }
        } catch (RuntimeException ex) {
            // One bad frame ends the effect instead of throwing on every
            // frame after it. The roll itself carries on regardless.
            failed = true;
            clearRing();
            plugin.getLogger().warning("Shiny pre-roll failed: " + ex);
        }
    }

    private void refreshAudience() {
        audience.clear();
        double rangeSq = VIEW_RANGE * VIEW_RANGE;
        Location origin = player.getLocation();
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(origin) > rangeSq) continue;
            if (!plugin.getPlayerDataManager().get(nearby.getUniqueId()).isAuraEnabled(Rarity.EPIC)) continue;
            audience.add(nearby);
        }
    }

    private void open(boolean showTitle) {
        if (showTitle) {
            Component title = Component.text("✦ Shiny ✦", NamedTextColor.AQUA, TextDecoration.BOLD);
            Component sub = Component.text("Something is shining", NamedTextColor.GRAY);
            player.showTitle(Title.title(title, sub, Title.Times.times(
                    Duration.ofMillis(100), Duration.ofMillis(TICKS * 50L + 200L), Duration.ofMillis(150))));
        }
        sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7f, 1.0f);
        sound(Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.8f);
        openRing();
    }

    /**
     * A ring of pale aqua on the ground that closes in while the helix
     * climbs, then is thrown out on the flash.
     *
     * The helix is the shiny's signature and it stays; what it never had
     * was anything that holds a shape for the people watching. It rides
     * the player as passengers, so a roller who walks takes it with them.
     */
    private void openRing() {
        AuraParts parts = plugin.getAuraManager().parts();
        for (int i = 0; i < RING_SEGMENTS; i++) {
            Display piece = parts.plate(player, AQUA, 215,
                    AuraParts.ringSegment(RING_SEGMENTS, i, 1.6f, 0.0, AuraParts.FEET + 0.02f, 0.09f, 0.7f));
            ring.add(piece);
            player.addPassenger(piece);
        }
        // Whatever happens to the roll, the pieces go. A roll abandoned
        // halfway simply stops calling frame(), and nothing else here would
        // ever come round to tidy up.
        plugin.getServer().getScheduler().runTaskLater(plugin, this::clearRing, TICKS + 40L);
    }

    private void drawRing(float radius, double spin, int ticks) {
        for (int i = 0; i < ring.size(); i++) {
            Display piece = ring.get(i);
            if (!piece.isValid()) continue;
            AuraParts.moveTo(piece, AuraParts.ringSegment(RING_SEGMENTS, i, radius, spin,
                    AuraParts.FEET + 0.02f, 0.09f, 0.7f), ticks);
        }
    }

    private void clearRing() {
        for (Display piece : ring) {
            if (piece.isValid()) piece.remove();
        }
        ring.clear();
    }

    /** A double helix tightening as it climbs, with a chime ladder rising underneath. */
    private void charge(double p) {
        Location base = player.getLocation();
        double radius = 1.1 - 0.45 * p;
        double top = 0.2 + 2.0 * p;
        for (int s = 0; s < 2; s++) {
            for (int k = 0; k < 3; k++) {
                double angle = frames * 0.9 + s * Math.PI - k * 0.28;
                double y = Math.max(0.1, top - k * 0.12);
                transition(base.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius));
            }
        }
        drawRing((float) (1.6 - 0.7 * p), frames * 6.0, 4);
        if (frames % 2 == 1) {
            puff(Particle.END_ROD, base.clone().add(0, top, 0), 1, 0.05, 0.05, 0.05, 0.0);
            sound(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, (float) (0.8 + 1.1 * p));
        }
    }

    /** Silence, and the helix pulled into one point just above the head. */
    private void hush(double p) {
        dust(player.getLocation().add(0, 2.3, 0), 5, 0.25 * (1.0 - p), bright);
        drawRing((float) (0.9 - 0.55 * p), frames * (6.0 + 10.0 * p), 4);
    }

    /** Three sounds low to high on one frame, a burst at the core and a ring on the ground. */
    private void flash() {
        Location base = player.getLocation();
        Location core = base.clone().add(0, 1.4, 0);
        for (Player viewer : audience) {
            viewer.spawnParticle(Particle.FLASH, core, 1, 0.0, 0.0, 0.0, 0.0, AQUA);
        }
        dust(core, 40, 0.7, bright);
        puff(Particle.END_ROD, core, 18, 0.4, 0.5, 0.4, 0.08);
        for (int i = 0; i < 24; i++) {
            double angle = (Math.PI * 2 / 24) * i;
            transition(base.clone().add(Math.cos(angle) * 1.6, 0.1, Math.sin(angle) * 1.6));
        }
        sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.7f);
        sound(Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
        sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
        drawRing(3.2f, 0.0, 8);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::clearRing, 10L);
    }

    private void transition(Location at) {
        for (Player viewer : audience) {
            viewer.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 1, 0.0, 0.0, 0.0, 0.0, strand);
        }
    }

    private void dust(Location at, int count, double spread, Particle.DustOptions options) {
        for (Player viewer : audience) {
            viewer.spawnParticle(Particle.DUST, at, count, spread, spread, spread, 0.0, options);
        }
    }

    private void puff(Particle particle, Location at, int count, double sx, double sy, double sz, double extra) {
        for (Player viewer : audience) {
            viewer.spawnParticle(particle, at, count, sx, sy, sz, extra);
        }
    }

    private void sound(Sound sound, float volume, float pitch) {
        Location at = player.getLocation();
        for (Player viewer : audience) {
            viewer.playSound(at, sound, volume, pitch);
        }
    }
}
