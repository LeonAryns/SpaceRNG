package com.spacerng.solrng.nova;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.aura.AuraParts;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * What a forge looks like.
 *
 * Forging a Nova Core is the biggest single gamble in the plugin: it eats
 * a Core, it can take a player back to their last checkpoint, and until
 * now the whole of it was one sound and a line of chat. Three outcomes
 * that matter that much deserve to be told apart with your eyes shut and
 * from across the room.
 *
 * A forge happens with a menu open, so everything here is drawn round the
 * player's feet and over their head, where the inventory panel is not.
 *
 * The three read as three different events on purpose:
 *
 *   climbed    a ring snaps outward and sparks rise. A checkpoint sends
 *              it twice as far and rings like a beacon.
 *   anchored   the ring pulls in and holds, one flat thud. Nothing is
 *              thrown, because nothing was gained.
 *   shattered  the ring breaks apart, the pieces fall away, glass.
 */
public final class NovaForgeFx {

    /** The Nova Core's own pink, the colour it wears everywhere else. */
    private static final Color CORE = Color.fromRGB(0xF4, 0x8F, 0xB1);
    private static final Color PALE = Color.fromRGB(0xFF, 0xE0, 0xEC);
    private static final int SEGMENTS = 12;
    private static final double VIEW = 40.0;

    private NovaForgeFx() {
    }

    /** A tier gained. A checkpoint is the same thing, louder and wider. */
    public static void climbed(SolRNGPlugin plugin, Player player, boolean checkpoint) {
        List<Display> ring = ring(plugin, player, CORE, 235, 0.4f);
        throwOut(ring, checkpoint ? 4.6f : 2.8f, 12);
        remove(plugin, ring, 22L);

        Location base = player.getLocation();
        Location core = base.clone().add(0, 1.2, 0);
        player.spawnParticle(Particle.DUST, core, 40, 0.5, 0.7, 0.5, 0.0,
                new Particle.DustOptions(CORE, 1.8f));
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            player.spawnParticle(Particle.END_ROD, base.clone().add(Math.cos(a) * 0.9, 0.1, Math.sin(a) * 0.9),
                    0, 0.0, 1.0, 0.0, 0.5 + (checkpoint ? 0.4 : 0.0));
        }
        if (checkpoint) {
            player.spawnParticle(Particle.FLASH, core, 1, 0.0, 0.0, 0.0, 0.0, CORE);
            player.spawnParticle(Particle.TOTEM_OF_UNDYING, core, 70, 0.6, 0.8, 0.6, 0.4);
        }
        // Low, middle, high on one frame, far enough apart in pitch to stay
        // three sounds rather than read as clipping.
        player.playSound(base, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, checkpoint ? 0.8f : 1.4f);
        player.playSound(base, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, checkpoint ? 1.2f : 1.7f);
        if (checkpoint) player.playSound(base, Sound.ITEM_TOTEM_USE, 0.7f, 1.6f);
    }

    /** Core Anchor held it. The climb still failed, so nothing is thrown outward. */
    public static void anchored(SolRNGPlugin plugin, Player player) {
        List<Display> ring = ring(plugin, player, PALE, 200, 2.4f);
        throwOut(ring, 0.9f, 8);
        remove(plugin, ring, 20L);

        Location base = player.getLocation();
        player.spawnParticle(Particle.DUST, base.clone().add(0, 0.9, 0), 25, 0.8, 0.5, 0.8, 0.0,
                new Particle.DustOptions(PALE, 1.4f));
        player.playSound(base, Sound.BLOCK_ANVIL_LAND, 0.6f, 1.6f);
        player.playSound(base, Sound.BLOCK_CONDUIT_DEACTIVATE, 0.7f, 1.3f);
    }

    /** It fell. The ring comes apart and the pieces drop away. */
    public static void shattered(SolRNGPlugin plugin, Player player) {
        List<Display> ring = ring(plugin, player, CORE, 225, 1.8f);
        Location base = player.getLocation();
        for (int i = 0; i < ring.size(); i++) {
            Display piece = ring.get(i);
            if (!piece.isValid()) continue;
            // Each shard falls its own way, tipped out of the circle it was
            // part of, so it reads as breaking rather than as shrinking.
            double a = Math.toRadians(360.0 / SEGMENTS * i);
            float radius = 2.4f;
            Quaternionf tip = new Quaternionf()
                    .rotateY((float) a + AuraParts.rad(90))
                    .rotateX(AuraParts.rad(-90 + 55 + (i % 3) * 14));
            AuraParts.moveTo(piece, AuraParts.plate((float) (radius * Math.cos(a)), -1.1f,
                    (float) (-radius * Math.sin(a)), tip, 0.5f, 0.06f), 16);
        }
        remove(plugin, ring, 22L);

        player.spawnParticle(Particle.DUST, base.clone().add(0, 1.0, 0), 30, 0.7, 0.6, 0.7, 0.0,
                new Particle.DustOptions(CORE, 1.6f));
        player.playSound(base, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
        player.playSound(base, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.6f);
    }

    /** A ring of plates round the player's feet, drawn at {@code radius} to start with. */
    private static List<Display> ring(SolRNGPlugin plugin, Player player, Color colour, int alpha, float radius) {
        AuraParts parts = plugin.getAuraManager().parts();
        Location at = player.getLocation();
        List<Display> pieces = new ArrayList<>();
        for (int i = 0; i < SEGMENTS; i++) {
            pieces.add(parts.plate(at, colour, alpha, VIEW, false,
                    AuraParts.ringSegment(SEGMENTS, i, radius, 0.0, 0.06f, 0.12f, 0.8f)));
        }
        return pieces;
    }

    private static void throwOut(List<Display> ring, float radius, int ticks) {
        for (int i = 0; i < ring.size(); i++) {
            Display piece = ring.get(i);
            if (piece.isValid()) {
                AuraParts.moveTo(piece,
                        AuraParts.ringSegment(SEGMENTS, i, radius, 30.0, 0.06f, 0.05f, 0.8f), ticks);
            }
        }
    }

    private static void remove(SolRNGPlugin plugin, List<Display> ring, long after) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Display piece : ring) {
                if (piece.isValid()) piece.remove();
            }
        }, after);
    }
}
