package com.spacerng.solrng.crate;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.aura.AuraParts;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * The burst above a crate once it has been opened.
 *
 * Two strands in the crate's own colours wind up out of the block, then
 * everything lets go at once at the top. A jackpot adds a totem shower and
 * a coloured flash. Particles are sent per viewer, never to the world, and
 * the audience is fixed at the start because the effect lasts under a
 * second.
 */
final class CrateFx {

    private static final int RISE_TICKS = 14;
    private static final double RANGE = 24.0;

    private CrateFx() {
    }

    static void burst(SolRNGPlugin plugin, Player opener, Location at, Crate crate, boolean jackpot) {
        World world = at.getWorld();
        if (world == null) return;

        Color from = colour(crate, true, Color.fromRGB(255, 196, 64));
        Color to = colour(crate, false, Color.fromRGB(255, 252, 224));
        Particle.DustTransition dust = new Particle.DustTransition(from, to, 1.3f);

        List<Player> audience = new ArrayList<>();
        for (Player viewer : world.getPlayers()) {
            if (viewer.getLocation().distanceSquared(at) <= RANGE * RANGE) audience.add(viewer);
        }
        if (audience.isEmpty()) return;

        new BukkitRunnable() {
            private int t;

            @Override
            public void run() {
                try {
                    if (t < RISE_TICKS) {
                        double p = (double) t / RISE_TICKS;
                        for (int strand = 0; strand < 2; strand++) {
                            double angle = t * 0.9 + strand * Math.PI;
                            double radius = 0.7 * (1.0 - p * 0.6);
                            Location point = at.clone().add(Math.cos(angle) * radius, p * 1.8,
                                    Math.sin(angle) * radius);
                            for (Player viewer : audience) {
                                if (viewer.isOnline()) {
                                    viewer.spawnParticle(Particle.DUST_COLOR_TRANSITION, point, 1, 0, 0, 0, 0, dust);
                                }
                            }
                        }
                        if (t % 4 == 0 && opener.isOnline()) {
                            opener.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, (float) (0.6 + p * 1.2));
                        }
                    } else if (t == RISE_TICKS) {
                        Location top = at.clone().add(0, 1.9, 0);
                        shockwave(plugin, top, from, jackpot);
                        for (Player viewer : audience) {
                            if (!viewer.isOnline()) continue;
                            viewer.spawnParticle(Particle.FIREWORK, top, 30, 0.2, 0.2, 0.2, 0.15);
                            viewer.spawnParticle(Particle.END_ROD, top, 12, 0.3, 0.3, 0.3, 0.05);
                            if (jackpot) {
                                viewer.spawnParticle(Particle.TOTEM_OF_UNDYING, top, 60, 0.3, 0.3, 0.3, 0.6);
                                viewer.spawnParticle(Particle.FLASH, top, 1, 0, 0, 0, 0, from);
                            }
                            viewer.playSound(top, jackpot ? Sound.ENTITY_FIREWORK_ROCKET_BLAST
                                    : Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.7f, jackpot ? 0.9f : 1.3f);
                        }
                    } else {
                        cancel();
                        return;
                    }
                } catch (Exception ex) {
                    cancel();
                    return;
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * A ring thrown outward from the top of the crate in its own colour,
     * and on a jackpot a column of light standing over it for a moment.
     *
     * The rest of this is particles, which do the job right on top of the
     * block and nothing at all past twenty blocks. A shape carries. A
     * jackpot in particular is worth somebody across the spawn turning
     * round for, and until now there was nothing to turn round to.
     */
    private static void shockwave(SolRNGPlugin plugin, Location top, Color colour, boolean jackpot) {
        AuraParts parts = plugin.getAuraManager().parts();
        List<Display> pieces = new ArrayList<>();
        int segments = jackpot ? 14 : 10;
        double view = jackpot ? 96.0 : 40.0;
        for (int i = 0; i < segments; i++) {
            pieces.add(parts.plate(top, colour, 230, view, false,
                    AuraParts.ringSegment(segments, i, 0.35f, 0.0, 0f, 0.14f, 0.85f)));
        }
        if (jackpot) {
            // Both faces: a column standing on a crate is walked round.
            for (boolean back : new boolean[]{false, true}) {
                pieces.add(parts.plate(top, colour, 90, view, false,
                        AuraParts.plate(0f, 2.6f, 0f, new Quaternionf(), 0.9f, 5.2f, back)));
            }
        }
        float reach = jackpot ? 4.4f : 2.6f;
        for (int i = 0; i < segments; i++) {
            AuraParts.moveTo(pieces.get(i),
                    AuraParts.ringSegment(segments, i, reach, 40.0, 0.25f, 0.05f, 0.85f), 14);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Display piece : pieces) {
                if (piece.isValid()) piece.remove();
            }
        }, jackpot ? 34L : 18L);
    }

    /** The crate's first or last gradient stop, when it has one. */
    private static Color colour(Crate crate, boolean first, Color fallback) {
        if (crate.colors().isEmpty()) return fallback;
        String hex = crate.colors().get(first ? 0 : crate.colors().size() - 1);
        if (!hex.startsWith("#") || hex.length() != 7) return fallback;
        try {
            return Color.fromRGB(Integer.parseInt(hex.substring(1), 16));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
