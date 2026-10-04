package com.spacerng.solrng.realm;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Finding a secret, drawn in grey (V329, Leon's call).
 *
 * Grey is the whole identity here. Every other spectacle in the plugin is
 * a colour that belongs to a rarity, so the one thing that belongs to no
 * rarity gets the one palette none of them use: ash, stone and bone, with
 * nothing warm in it. It reads as something being uncovered rather than
 * something being won.
 *
 * Three phases on one tick counter, the shape the aura skill sets out:
 * a vortex winding inward, one bright frame, then rings settling out.
 * Everything goes out per viewer, within RANGE, so nobody across the
 * realm pays for it.
 */
public final class SecretFx {

    private static final int DURATION = 36;
    private static final double CAST_AT = 0.55;
    private static final double RANGE = 24.0;

    // Ash, stone and bone. Never pure white: a 255,255,255 dust cloud
    // reads as a rendering fault rather than as light.
    private static final Color ASH = Color.fromRGB(120, 120, 128);
    private static final Color STONE = Color.fromRGB(168, 168, 176);
    private static final Color BONE = Color.fromRGB(228, 226, 216);

    private final SolRNGPlugin plugin;
    private final Player owner;
    private final Location centre;
    private BukkitTask task;
    private int elapsed;

    private SecretFx(SolRNGPlugin plugin, Player owner) {
        this.plugin = plugin;
        this.owner = owner;
        Location at = owner.getLocation().clone().add(0, 1.0, 0);
        at.setYaw(0f);
        at.setPitch(0f);
        this.centre = at;
    }

    /** Plays it once, where the player stands. */
    public static void reveal(SolRNGPlugin plugin, Player player) {
        new SecretFx(plugin, player).start();
    }

    private void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!owner.isOnline()) {
                stop();
                return;
            }
            try {
                double progress = (double) elapsed / DURATION;
                if (progress < CAST_AT) {
                    charge(progress / CAST_AT);
                } else if (elapsed == (int) Math.round(DURATION * CAST_AT)) {
                    cast();
                } else {
                    settle((progress - CAST_AT) / (1.0 - CAST_AT));
                }
            } catch (RuntimeException ex) {
                // One bad frame cancels the effect rather than throwing
                // sixty times a second forever.
                stop();
                return;
            }
            if (++elapsed > DURATION) stop();
        }, 0L, 1L);
    }

    private void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    /** Who can see it: anybody nearby, the finder included. */
    private List<Player> audience() {
        List<Player> out = new ArrayList<>();
        for (Player player : centre.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(centre) <= RANGE * RANGE) out.add(player);
        }
        return out;
    }

    /**
     * A vortex: a ring whose radius shrinks and whose height climbs, so
     * the dust is visibly being pulled into one point over the player.
     */
    private void charge(double t) {
        double radius = 2.2 * (1.0 - t) + 0.25;
        double y = t * 1.3;
        int points = 10;
        List<Player> audience = audience();
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 / points) * i + elapsed * 0.35;
            Location at = centre.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            for (Player viewer : audience) {
                viewer.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 1, 0.02, 0.02, 0.02, 0.0,
                        new Particle.DustTransition(ASH, STONE, 1.1f));
            }
        }
        if (elapsed % 4 == 0) {
            owner.playSound(centre, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 0.6f + (float) t * 1.2f);
        }
    }

    /** One frame, the loudest: a sphere, a shock ring and a grey flash. */
    private void cast() {
        List<Player> audience = audience();
        int rings = 5;
        for (int ring = 1; ring <= rings; ring++) {
            double phi = Math.PI * ring / (rings + 1);
            double y = Math.cos(phi) * 1.4 + 1.0;
            double r = Math.sin(phi) * 1.4;
            for (int i = 0; i < 12; i++) {
                double angle = (Math.PI * 2 / 12) * i;
                Location at = centre.clone().add(Math.cos(angle) * r, y - 1.0, Math.sin(angle) * r);
                for (Player viewer : audience) {
                    viewer.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0,
                            new Particle.DustOptions(BONE, 1.6f));
                }
            }
        }
        for (Player viewer : audience) {
            // FLASH takes a Colour since 1.21.11 and throws on null.
            viewer.spawnParticle(Particle.FLASH, centre.clone().add(0, 1.2, 0), 1, 0, 0, 0, 0.0, STONE);
            viewer.spawnParticle(Particle.WHITE_ASH, centre.clone().add(0, 2.0, 0), 40, 0.9, 0.6, 0.9, 0.02);
            viewer.spawnParticle(Particle.SCULK_SOUL, centre.clone().add(0, 1.0, 0), 12, 0.4, 0.4, 0.4, 0.04);
            viewer.playSound(centre, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.6f, 1.4f);
            viewer.playSound(centre, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 0.7f);
        }
    }

    /** Rings settling out, and ash falling through them. */
    private void settle(double t) {
        if (elapsed % 2 != 0) return;
        double radius = 0.6 + t * 3.0;
        int points = 16;
        List<Player> audience = audience();
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 / points) * i;
            Location at = centre.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
            for (Player viewer : audience) {
                viewer.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0,
                        new Particle.DustOptions(ASH, (float) (1.4 - t)));
            }
        }
        for (Player viewer : audience) {
            viewer.spawnParticle(Particle.ASH, centre.clone().add(0, 2.2, 0), 6, 1.2, 0.4, 1.2, 0.0);
        }
    }
}
