package com.spacerng.solrng.crate;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

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
