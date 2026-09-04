package com.spacerng.solrng.roll;

import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * The "something big is coming" effect for Epic-and-up rolls.
 *
 * It runs on its own 1-tick task rather than piggybacking on the roll
 * timer — the roll ticks every 2 ticks, which is too coarse for the
 * strands to read as continuous trails instead of dotted arcs.
 *
 * Three phases, sized and timed by rarity:
 *   GATHER  a wide ring of strands collapses inward toward the player
 *   CHARGE  a tight, fast vortex climbs the player, ground rings pulse out
 *   FLARE   the whole thing blows back outward just before the reveal
 *
 * Everything scales with rarity — Mythical is both wider and four times
 * longer than Epic, so the tier is readable from across the map before
 * anyone knows what the drop is. Sound is a timed score of cues rather
 * than one burst at the end, and the loud cues use volume above 1.0,
 * which in Minecraft extends the audible RADIUS (16 blocks per 1.0)
 * rather than making the sample clip.
 */
public final class RollAura {

    /** One scheduled sound in a rarity's score, fired when progress passes `at`. */
    private record Cue(double at, Sound sound, float volume, float pitch) {
    }

    // ---------------------------------------------------------- per-rarity

    private static long durationFor(Rarity rarity) {
        return switch (rarity) {
            case MYTHICAL -> 200L; // 10s
            case LEGENDARY -> 100L; // 5s
            default -> 60L;         // 3s, Epic
        };
    }

    private static double maxRadiusFor(Rarity rarity) {
        return switch (rarity) {
            case MYTHICAL -> 7.0;
            case LEGENDARY -> 4.5;
            default -> 2.5; // Epic
        };
    }

    private static int strandsFor(Rarity rarity) {
        return switch (rarity) {
            case MYTHICAL -> 7;
            case LEGENDARY -> 5;
            default -> 3; // Epic
        };
    }

    private static Color colorFor(Rarity rarity) {
        return switch (rarity) {
            case MYTHICAL -> Color.fromRGB(255, 60, 60);
            case LEGENDARY -> Color.fromRGB(255, 170, 0);
            default -> Color.fromRGB(168, 85, 247); // Epic
        };
    }

    private static Particle accentFor(Rarity rarity) {
        return switch (rarity) {
            case MYTHICAL -> Particle.DRAGON_BREATH;
            case LEGENDARY -> Particle.FLAME;
            default -> Particle.END_ROD; // Epic
        };
    }

    /**
     * The audio score. Epic is a short chime; Legendary adds a horn and a
     * launch; Mythical is a full ten-second build — dragon, wither, curse,
     * thunder — spaced so nothing lands on top of anything else.
     */
    private static List<Cue> scoreFor(Rarity rarity) {
        List<Cue> cues = new ArrayList<>();
        switch (rarity) {
            case MYTHICAL -> {
                cues.add(new Cue(0.00, Sound.ENTITY_ENDER_DRAGON_GROWL, 4.0f, 0.6f));
                cues.add(new Cue(0.12, Sound.BLOCK_PORTAL_TRIGGER, 2.0f, 0.5f));
                cues.add(new Cue(0.28, Sound.ENTITY_WITHER_SPAWN, 3.0f, 0.7f));
                cues.add(new Cue(0.45, Sound.ENTITY_ENDER_DRAGON_GROWL, 4.0f, 0.8f));
                cues.add(new Cue(0.60, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 2.5f, 1.2f));
                cues.add(new Cue(0.72, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 4.0f, 0.7f));
                cues.add(new Cue(0.84, Sound.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 1.0f));
                cues.add(new Cue(0.93, Sound.BLOCK_BEACON_POWER_SELECT, 3.0f, 0.6f));
            }
            case LEGENDARY -> {
                cues.add(new Cue(0.00, Sound.BLOCK_BEACON_ACTIVATE, 2.5f, 0.8f));
                cues.add(new Cue(0.25, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2.5f, 0.8f));
                cues.add(new Cue(0.50, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 2.5f, 0.9f));
                cues.add(new Cue(0.75, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2.5f, 0.8f));
                cues.add(new Cue(0.92, Sound.BLOCK_CONDUIT_ACTIVATE, 2.5f, 1.4f));
            }
            default -> {
                cues.add(new Cue(0.00, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.4f));
                cues.add(new Cue(0.55, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 1.2f));
            }
        }
        return cues;
    }

    // ---------------------------------------------------------------- state

    private final Player player;
    private final Rarity rarity;
    private final long durationTicks;
    private final double maxRadius;
    private final int strands;
    private final Particle.DustOptions dust;
    private final Particle.DustOptions dustBright;
    private final Particle accent;
    private final List<Cue> score;

    private int nextCue = 0;
    private long elapsed = 0L;
    private BukkitTask task;
    private boolean finished = false;

    private RollAura(Player player, Rarity rarity) {
        this.player = player;
        this.rarity = rarity;
        this.durationTicks = durationFor(rarity);
        this.maxRadius = maxRadiusFor(rarity);
        this.strands = strandsFor(rarity);
        Color color = colorFor(rarity);
        this.dust = new Particle.DustOptions(color, 1.3f);
        this.dustBright = new Particle.DustOptions(color, 2.2f);
        this.accent = accentFor(rarity);
        this.score = scoreFor(rarity);
    }

    // ------------------------------------------------------------- lifecycle

    /** Epic and up get the aura; anything below rolls quietly. */
    public static boolean isBigDrop(Rarity rarity) {
        return rarity != null && rarity.ordinal() >= Rarity.EPIC.ordinal();
    }

    /**
     * How long this rarity's build-up wants to run. The roll stretches to
     * at least this long so the effect isn't cut off halfway.
     */
    public static long durationTicks(Rarity rarity) {
        return isBigDrop(rarity) ? durationFor(rarity) : 0L;
    }

    /**
     * Starts the build-up on its own task. Returns null for anything below
     * Epic, so callers can just null-check instead of branching on rarity.
     */
    public static RollAura start(Plugin plugin, Player player, Rarity rarity) {
        if (!isBigDrop(rarity)) return null;

        RollAura aura = new RollAura(player, rarity);
        aura.task = plugin.getServer().getScheduler().runTaskTimer(plugin, aura::tick, 0L, 1L);
        return aura;
    }

    /** Stops the build-up without a burst — used when a roll is abandoned. */
    public void cancel() {
        finished = true;
        if (task != null) task.cancel();
    }

    // ---------------------------------------------------------------- frames

    private void tick() {
        if (finished) return;
        if (!player.isOnline()) {
            cancel();
            return;
        }

        elapsed++;
        double progress = Math.min(1.0, (double) elapsed / durationTicks);

        playDueCues(progress);
        drawFrame(progress);

        // A rising note ladder underneath the score, so there's always
        // something climbing even between cues.
        int noteEvery = rarity == Rarity.MYTHICAL ? 10 : 6;
        if (elapsed % noteEvery == 0) {
            float pitch = (float) Math.min(2.0, 0.5 + progress * 1.5);
            player.getWorld().playSound(player.getLocation(),
                    rarity == Rarity.EPIC ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BELL,
                    1.2f, pitch);
        }

        // The task keeps running past 1.0 until reveal() lands, holding the
        // flare — a roll can outlast the aura if the player's Speed is low.
    }

    private void playDueCues(double progress) {
        World world = player.getWorld();
        Location at = player.getLocation();
        while (nextCue < score.size() && progress >= score.get(nextCue).at()) {
            Cue cue = score.get(nextCue++);
            world.playSound(at, cue.sound(), cue.volume(), cue.pitch());
        }
    }

    /**
     * Phase-driven geometry. `progress` shapes the radius; `elapsed` drives
     * the spin, so the strands keep moving even while the radius holds.
     */
    private void drawFrame(double progress) {
        World world = player.getWorld();
        Location base = player.getLocation();

        double radius;
        double spinSpeed;
        double climb;
        if (progress < 0.55) {
            // GATHER: sweeps in from the full radius to a tight core.
            double p = progress / 0.55;
            radius = maxRadius - (maxRadius - 0.8) * ease(p);
            spinSpeed = 0.10 + 0.10 * p;
            climb = 0.06;
        } else if (progress < 0.90) {
            // CHARGE: tight and fast, pulsing.
            double p = (progress - 0.55) / 0.35;
            radius = 0.8 + 0.35 * Math.sin(elapsed * 0.45);
            spinSpeed = 0.22 + 0.25 * p;
            climb = 0.10 + 0.08 * p;
        } else {
            // FLARE: blows back out past the starting radius.
            double p = (progress - 0.90) / 0.10;
            radius = 0.8 + (maxRadius * 1.25 - 0.8) * ease(p);
            spinSpeed = 0.50;
            climb = 0.16;
        }

        double spin = elapsed * spinSpeed;
        for (int i = 0; i < strands; i++) {
            double angle = spin + (i * (Math.PI * 2 / strands));
            double height = ((elapsed * climb) + (i * (2.4 / strands))) % 2.4;
            Location point = base.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);

            world.spawnParticle(Particle.DUST, point, 2, 0.04, 0.04, 0.04, 0.0, dust);
            // Accents thicken as the roll closes in on the reveal.
            if (elapsed % Math.max(1, (int) (6 - progress * 5)) == 0) {
                world.spawnParticle(accent, point, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }

        // Legendary and up also get a ground ring marking the full radius,
        // so the footprint is obvious from a distance even when the strands
        // have wound inward.
        if (rarity.ordinal() >= Rarity.LEGENDARY.ordinal() && elapsed % 2 == 0) {
            drawRing(world, base, maxRadius, (int) (maxRadius * 6), dust, 0.05);
        }

        // Mythical adds an expanding shockwave that resets every 40 ticks,
        // plus a column of light straight up the player.
        if (rarity == Rarity.MYTHICAL) {
            double wave = (elapsed % 40) / 40.0;
            drawRing(world, base, 1.0 + wave * (maxRadius + 2.0), 40, dustBright, 0.02);

            if (elapsed % 2 == 0) {
                for (double y = 0.0; y < 3.0 + progress * 4.0; y += 0.5) {
                    world.spawnParticle(Particle.DUST, base.clone().add(0, y, 0), 1, 0.08, 0.0, 0.08, 0.0, dust);
                }
            }
        }
    }

    private void drawRing(World world, Location center, double radius, int points, Particle.DustOptions options,
                          double y) {
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 / points) * i;
            Location point = center.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, options);
        }
    }

    /** Smoothstep, so the radius eases rather than moving linearly. */
    private static double ease(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return clamped * clamped * (3 - 2 * clamped);
    }

    // ---------------------------------------------------------------- reveal

    /** The burst. Ends the build-up and fires the payoff for this rarity. */
    public void reveal() {
        if (finished) return;
        cancel();

        World world = player.getWorld();
        Location base = player.getLocation().add(0, 1.0, 0);

        world.spawnParticle(Particle.DUST, base, 160, maxRadius * 0.35, 1.0, maxRadius * 0.35, 0.0, dustBright);
        world.spawnParticle(accent, base, 70, maxRadius * 0.25, 0.8, maxRadius * 0.25, 0.3);

        switch (rarity) {
            case MYTHICAL -> {
                // Real (harmless) lightning sells the thunder far better
                // than the sound alone — strikeLightningEffect is visual
                // only, so nothing burns and nobody takes damage.
                for (int i = 0; i < 4; i++) {
                    double angle = (Math.PI / 2) * i;
                    world.strikeLightningEffect(player.getLocation()
                            .clone().add(Math.cos(angle) * 3.0, 0, Math.sin(angle) * 3.0));
                }
                world.spawnParticle(Particle.SONIC_BOOM, base, 1, 0.0, 0.0, 0.0, 0.0);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, base, 2, 1.0, 0.5, 1.0, 0.0);
                world.spawnParticle(Particle.DRAGON_BREATH, base, 200, 2.0, 1.2, 2.0, 0.35);

                world.playSound(base, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 4.0f, 0.8f);
                world.playSound(base, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 4.0f, 1.0f);
                world.playSound(base, Sound.ENTITY_ENDER_DRAGON_GROWL, 4.0f, 1.3f);
                world.playSound(base, Sound.ENTITY_WITHER_DEATH, 3.0f, 1.4f);
            }
            case LEGENDARY -> {
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, base, 120, 1.2, 0.9, 1.2, 0.4);
                world.spawnParticle(Particle.FIREWORK, base, 80, 1.0, 0.8, 1.0, 0.3);
                drawRing(world, player.getLocation(), maxRadius * 1.3, 60, dustBright, 0.1);

                world.playSound(base, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 3.0f, 1.0f);
                world.playSound(base, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);
                world.playSound(base, Sound.ITEM_TOTEM_USE, 2.0f, 1.2f);
            }
            default -> {
                world.spawnParticle(Particle.WITCH, base, 90, 0.8, 0.8, 0.8, 0.1);
                world.spawnParticle(Particle.END_ROD, base, 40, 0.5, 0.6, 0.5, 0.2);

                world.playSound(base, Sound.BLOCK_BEACON_POWER_SELECT, 2.0f, 1.6f);
                world.playSound(base, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f, 1.8f);
            }
        }
    }
}
