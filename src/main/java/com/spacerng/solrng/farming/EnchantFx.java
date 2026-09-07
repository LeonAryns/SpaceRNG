package com.spacerng.solrng.farming;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * The animations behind the big farm procs.
 *
 * Player-sided, always. Every particle goes through player.spawnParticle
 * rather than the world, so a proc is a moment for the person who earned
 * it and not a fog everybody standing nearby has to look through. Twenty
 * farmers on one field would otherwise be twenty overlapping effects.
 *
 * Each animation runs on its own tick counter and cancels itself. Nothing
 * here touches blocks, entities or payouts: the caller has already done
 * the work, and this is only what it looked like.
 */
public final class EnchantFx {

    private static final Color GOLD = Color.fromRGB(255, 199, 44);
    private static final Color GEM = Color.fromRGB(64, 224, 232);
    private static final Color VOID_EDGE = Color.fromRGB(150, 90, 255);

    private EnchantFx() {
    }

    /**
     * A vortex that drags inward and collapses.
     *
     * The arms tighten as the run goes on, so the shape reads as pulling
     * rather than spinning, and the flash lands on the frame the radius
     * reaches zero.
     */
    public static void blackHole(SolRNGPlugin plugin, Player player, Location centre, double radius) {
        Location core = centre.clone().add(0.5, 1.4, 0.5);
        run(plugin, 30, frame -> {
            double p = frame / 30.0;
            double r = radius * (1.0 - ease(p));
            for (int arm = 0; arm < 6; arm++) {
                double angle = (frame * 0.45) + (arm * Math.PI / 3);
                for (double t = 0.2; t <= 1.0; t += 0.2) {
                    double rr = r * t;
                    Location at = core.clone().add(Math.cos(angle + t * 2.2) * rr,
                            Math.sin(t * Math.PI) * 0.5 * (1.0 - p), Math.sin(angle + t * 2.2) * rr);
                    dust(player, at, VOID_EDGE, (float) (0.8 + t));
                }
            }
            player.spawnParticle(Particle.SMOKE, core, 2, 0.15, 0.15, 0.15, 0.01);
            if (frame == 1) {
                player.playSound(core, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7f, 0.5f);
            }
            if (frame == 29) {
                player.spawnParticle(Particle.FLASH, core, 1);
                player.spawnParticle(Particle.EXPLOSION, core, 3, 0.3, 0.3, 0.3, 0.0);
                player.playSound(core, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.6f);
            }
        });
    }

    /** A meteor that falls, then splashes outward where it lands. */
    public static void meteor(SolRNGPlugin plugin, Player player, Location impact, double radius) {
        Location target = impact.clone().add(0.5, 0.0, 0.5);
        run(plugin, 26, frame -> {
            if (frame <= 14) {
                double height = 18.0 * (1.0 - frame / 14.0);
                Location at = target.clone().add(0, height + 1.0, 0);
                player.spawnParticle(Particle.FLAME, at, 6, 0.25, 0.25, 0.25, 0.01);
                player.spawnParticle(Particle.LARGE_SMOKE, at, 3, 0.2, 0.4, 0.2, 0.0);
                if (frame == 1) player.playSound(target, Sound.ENTITY_BLAZE_SHOOT, 0.9f, 0.6f);
            } else if (frame == 15) {
                player.spawnParticle(Particle.EXPLOSION_EMITTER, target, 1);
                player.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.8f);
                player.playSound(target, Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.7f);
            } else {
                double p = (frame - 15) / 11.0;
                ring(player, target, radius * p, GOLD, (float) (1.6 * (1.0 - p)));
            }
        });
    }

    /** Gold raining down around the player, for a Coin window. */
    public static void coinStorm(SolRNGPlugin plugin, Player player, int ticks) {
        run(plugin, ticks, frame -> {
            Location base = player.getLocation();
            for (int i = 0; i < 4; i++) {
                double angle = Math.random() * Math.PI * 2;
                double dist = Math.random() * 4.0;
                Location at = base.clone().add(Math.cos(angle) * dist, 4.5, Math.sin(angle) * dist);
                // count 0 turns the offset into a direction: straight down.
                player.spawnParticle(Particle.DUST, at, 0, 0, -1, 0, 0.35,
                        new Particle.DustOptions(GOLD, 1.4f));
            }
            if (frame % 20 == 1) {
                player.playSound(base, Sound.ENTITY_ITEM_PICKUP, 0.5f, 0.8f + (frame % 5) * 0.1f);
            }
        });
    }

    /** Aqua shards orbiting the player, for a Gem window. */
    public static void gemRush(SolRNGPlugin plugin, Player player, int ticks) {
        run(plugin, ticks, frame -> {
            Location base = player.getLocation().add(0, 1.1, 0);
            for (int i = 0; i < 3; i++) {
                double angle = (frame * 0.25) + (i * Math.PI * 2 / 3);
                Location at = base.clone().add(Math.cos(angle) * 1.3,
                        Math.sin(frame * 0.15 + i) * 0.4, Math.sin(angle) * 1.3);
                dust(player, at, GEM, 1.3f);
                player.spawnParticle(Particle.END_ROD, at, 1, 0.0, 0.0, 0.0, 0.0);
            }
            if (frame == 1) player.playSound(base, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
        });
    }

    /** A visible arc from one crop to the next, for a chaining proc. */
    public static void arc(Player player, Location from, Location to, boolean gem) {
        Location a = from.clone().add(0.5, 0.6, 0.5);
        Location b = to.clone().add(0.5, 0.6, 0.5);
        var step = b.toVector().subtract(a.toVector()).multiply(1.0 / 12.0);
        Location cursor = a.clone();
        for (int i = 0; i < 12; i++) {
            dust(player, cursor, gem ? GEM : GOLD, 1.0f);
            cursor.add(step);
        }
    }

    /** Gold turning into aqua on the spot, for a conversion proc. */
    public static void transmute(SolRNGPlugin plugin, Player player, Location centre) {
        Location core = centre.clone().add(0.5, 1.0, 0.5);
        run(plugin, 20, frame -> {
            double p = frame / 20.0;
            for (int i = 0; i < 5; i++) {
                double angle = (frame * 0.4) + (i * Math.PI * 2 / 5);
                Location at = core.clone().add(Math.cos(angle) * 0.9, p * 1.2, Math.sin(angle) * 0.9);
                player.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 1, 0.0, 0.0, 0.0, 0.0,
                        new Particle.DustTransition(GOLD, GEM, 1.4f));
            }
            if (frame == 1) player.playSound(core, Sound.BLOCK_BREWING_STAND_BREW, 0.9f, 1.3f);
            if (frame == 20) player.playSound(core, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.7f);
        });
    }

    /** A ring that expands and fades, for anything paid out on the spot. */
    public static void pulse(SolRNGPlugin plugin, Player player, Location centre, boolean gem) {
        Location core = centre.clone().add(0.5, 0.4, 0.5);
        run(plugin, 12, frame -> {
            double p = frame / 12.0;
            ring(player, core, 0.4 + p * 2.2, gem ? GEM : GOLD, (float) (1.5 * (1.0 - p)));
        });
    }

    /** The big one: a shell, a pillar, and a flash you cannot miss. */
    public static void supernova(SolRNGPlugin plugin, Player player, Location centre) {
        Location core = centre.clone().add(0.5, 1.4, 0.5);
        run(plugin, 34, frame -> {
            if (frame == 1) {
                player.spawnParticle(Particle.FLASH, core, 1);
                player.playSound(core, Sound.ITEM_TRIDENT_THUNDER, 1.0f, 1.2f);
                player.playSound(core, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.9f, 0.7f);
            }
            if (frame <= 18) {
                double p = frame / 18.0;
                double r = 1.0 + ease(p) * 9.0;
                int rings = p < 0.5 ? 6 : 4;
                for (int i = 1; i <= rings; i++) {
                    double phi = Math.PI * i / (rings + 1);
                    ring(player, core.clone().add(0, Math.cos(phi) * r, 0),
                            Math.sin(phi) * r, GOLD, (float) (2.0 * (1.0 - p)));
                }
            }
            double life = 1.0 - (frame / 34.0);
            for (double y = 0.0; y < 14.0; y += 1.0) {
                double sway = Math.sin((y * 0.4) + (frame * 0.3)) * 0.6 * life;
                dust(player, centre.clone().add(0.5 + sway, y, 0.5), GEM, 1.6f);
            }
        });
    }

    // ------------------------------------------------------------ helpers

    private static void ring(Player player, Location centre, double radius, Color colour, float size) {
        if (size <= 0.1f) return;
        int points = Math.max(8, (int) (radius * 8));
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 / points) * i;
            dust(player, centre.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius),
                    colour, size);
        }
    }

    private static void dust(Player player, Location at, Color colour, float size) {
        player.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0,
                new Particle.DustOptions(colour, size));
    }

    private static double ease(double p) {
        return 1.0 - Math.pow(1.0 - p, 3);
    }

    /**
     * Runs a frame callback until it has fired `length` times.
     *
     * Wrapped so one bad frame cancels the effect rather than throwing
     * every tick forever, and so an animation cannot outlive the player
     * who triggered it.
     */
    private static void run(SolRNGPlugin plugin, int length, java.util.function.IntConsumer frame) {
        final int[] tick = {0};
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            tick[0]++;
            if (tick[0] > length) {
                task[0].cancel();
                return;
            }
            try {
                frame.accept(tick[0]);
            } catch (Exception ex) {
                task[0].cancel();
                plugin.getLogger().warning("Enchant effect cancelled: " + ex.getMessage());
            }
        }, 0L, 1L);
    }
}
