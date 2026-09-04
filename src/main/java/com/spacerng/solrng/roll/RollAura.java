package com.spacerng.solrng.roll;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
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
 * Four phases, sized and timed by rarity:
 *   GATHER   a wide ring of strands collapses inward toward the player
 *   CHARGE   a tight, fast vortex climbs the player, ground rings pulse
 *   IMPLODE  everything rushes into a single point overhead and goes quiet
 *   FINALE   detonation, then shockwaves and embers over the next seconds
 *
 * The hush before the detonation is the point: a climax with nothing in
 * front of it doesn't read as a climax. Rolling is locked out for the
 * whole finale so an auto-roller doesn't start the next roll on top of
 * their own payoff.
 *
 * Particles and sounds are sent per-viewer rather than through the World,
 * so anyone who switched the aura off in /options is skipped.
 */
public final class RollAura {

    /** One scheduled sound in a rarity's score, fired when progress passes `at`. */
    private record Cue(double at, Sound sound, float volume, float pitch) {
    }

    // Everything from this progress point on is the implosion — the strands
    // stop orbiting and collapse into the point the drop bursts out of.
    private static final double IMPLODE_FROM = 0.88;

    // ---------------------------------------------------------- per-rarity

    private static long durationFor(Rarity rarity) {
        return switch (rarity) {
            case MYTHICAL -> 200L; // 10s
            case LEGENDARY -> 100L; // 5s
            default -> 60L;         // 3s, Epic
        };
    }

    /** How long the payoff runs after the reveal, and how long rolling is locked. */
    public static long finaleTicks(Rarity rarity) {
        if (!isBigDrop(rarity)) return 0L;
        return switch (rarity) {
            case MYTHICAL -> 60L;  // 3s
            case LEGENDARY -> 40L; // 2s
            default -> 24L;        // 1.2s, Epic
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
     * The audio score for the build-up. Nothing is scheduled past
     * IMPLODE_FROM on purpose — the silence there is what makes the
     * detonation land.
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
                cues.add(new Cue(0.82, Sound.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 1.0f));
            }
            case LEGENDARY -> {
                cues.add(new Cue(0.00, Sound.BLOCK_BEACON_ACTIVATE, 2.5f, 0.8f));
                cues.add(new Cue(0.25, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2.5f, 0.8f));
                cues.add(new Cue(0.50, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 2.5f, 0.9f));
                cues.add(new Cue(0.75, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2.5f, 0.8f));
            }
            default -> {
                cues.add(new Cue(0.00, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.4f));
                cues.add(new Cue(0.55, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 1.2f));
            }
        }
        return cues;
    }

    // ---------------------------------------------------------------- state

    private final SolRNGPlugin plugin;
    private final Player player;
    private final Rarity rarity;
    private final long durationTicks;
    private final double maxRadius;
    private final int strands;
    private final Particle.DustOptions dust;
    private final Particle.DustOptions dustBright;
    private final Particle accent;
    private final List<Cue> score;
    private final double viewRange;

    private final List<Player> audience = new ArrayList<>();
    private int nextCue = 0;
    private long elapsed = 0L;
    private BukkitTask task;
    private boolean finished = false;

    private RollAura(SolRNGPlugin plugin, Player player, Rarity rarity) {
        this.plugin = plugin;
        this.player = player;
        this.rarity = rarity;
        this.durationTicks = durationFor(rarity);
        this.maxRadius = maxRadiusFor(rarity);
        this.strands = strandsFor(rarity);
        Color color = colorFor(rarity);
        this.dust = new Particle.DustOptions(color, 1.3f);
        this.dustBright = new Particle.DustOptions(color, 2.4f);
        this.accent = accentFor(rarity);
        this.score = scoreFor(rarity);
        // Matches the loudest cue's reach (16 blocks per 1.0 volume), so
        // anyone who can hear it can also see it.
        this.viewRange = rarity == Rarity.MYTHICAL ? 64.0 : rarity == Rarity.LEGENDARY ? 48.0 : 32.0;
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
    public static RollAura start(SolRNGPlugin plugin, Player player, Rarity rarity) {
        if (!isBigDrop(rarity)) return null;

        RollAura aura = new RollAura(plugin, player, rarity);
        aura.task = plugin.getServer().getScheduler().runTaskTimer(plugin, aura::tick, 0L, 1L);
        return aura;
    }

    /** Stops the build-up without a payoff — used when a roll is abandoned. */
    public void cancel() {
        finished = true;
        if (task != null) task.cancel();
    }

    // ------------------------------------------------------------- delivery

    /**
     * Everyone in range who hasn't switched the aura off. Recomputed once
     * per frame — a Mythical frame makes dozens of particle calls, and
     * rescanning the world for each one would be wasteful.
     *
     * The roller is included on the same terms as anybody else: if they
     * turned it off, they don't get it either.
     */
    private void refreshAudience() {
        audience.clear();
        double rangeSq = viewRange * viewRange;
        Location origin = player.getLocation();
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(origin) > rangeSq) continue;
            if (!plugin.getPlayerDataManager().get(nearby.getUniqueId()).isRevealAuraEnabled()) continue;
            audience.add(nearby);
        }
    }

    private void dustAt(Location at, int count, double spread, Particle.DustOptions options) {
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

    // ---------------------------------------------------------------- frames

    private void tick() {
        if (finished) return;
        if (!player.isOnline()) {
            cancel();
            return;
        }

        elapsed++;
        refreshAudience();
        double progress = Math.min(1.0, (double) elapsed / durationTicks);

        playDueCues(progress);

        if (progress < IMPLODE_FROM) {
            drawBuildUp(progress);
            // A rising note ladder under the score, so something is always
            // climbing even between cues.
            int noteEvery = rarity == Rarity.MYTHICAL ? 10 : 6;
            if (elapsed % noteEvery == 0) {
                sound(rarity == Rarity.EPIC ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BELL,
                        1.2f, (float) Math.min(2.0, 0.5 + progress * 1.5));
            }
        } else {
            drawImplosion((progress - IMPLODE_FROM) / (1.0 - IMPLODE_FROM));
        }
    }

    private void playDueCues(double progress) {
        while (nextCue < score.size() && progress >= score.get(nextCue).at()) {
            Cue cue = score.get(nextCue++);
            sound(cue.sound(), cue.volume(), cue.pitch());
        }
    }

    /**
     * GATHER then CHARGE. `progress` shapes the radius; `elapsed` drives the
     * spin, so the strands keep moving even while the radius holds.
     */
    private void drawBuildUp(double progress) {
        Location base = player.getLocation();

        double radius;
        double spinSpeed;
        double climb;
        if (progress < 0.55) {
            double p = progress / 0.55;
            radius = maxRadius - (maxRadius - 0.8) * ease(p);
            spinSpeed = 0.10 + 0.10 * p;
            climb = 0.06;
        } else {
            double p = (progress - 0.55) / (IMPLODE_FROM - 0.55);
            radius = 0.8 + 0.35 * Math.sin(elapsed * 0.45);
            spinSpeed = 0.22 + 0.25 * p;
            climb = 0.10 + 0.08 * p;
        }

        double spin = elapsed * spinSpeed;
        for (int i = 0; i < strands; i++) {
            double angle = spin + (i * (Math.PI * 2 / strands));
            double height = ((elapsed * climb) + (i * (2.4 / strands))) % 2.4;
            Location point = base.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);

            dustAt(point, 2, 0.04, dust);
            if (elapsed % Math.max(1, (int) (6 - progress * 5)) == 0) {
                puff(accent, point, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }

        // Legendary and up mark their full radius on the ground, so the
        // footprint reads from a distance once the strands wind inward.
        if (rarity.ordinal() >= Rarity.LEGENDARY.ordinal() && elapsed % 2 == 0) {
            ring(base, maxRadius, (int) (maxRadius * 6), dust, 0.05);
        }

        // Mythical adds a shockwave that resets every 40 ticks and a column
        // of light straight up.
        if (rarity == Rarity.MYTHICAL) {
            double wave = (elapsed % 40) / 40.0;
            ring(base, 1.0 + wave * (maxRadius + 2.0), 40, dustBright, 0.02);

            if (elapsed % 2 == 0) {
                for (double y = 0.0; y < 3.0 + progress * 4.0; y += 0.5) {
                    dustAt(base.clone().add(0, y, 0), 1, 0.08, dust);
                }
            }
        }
    }

    /**
     * IMPLODE. Everything the build-up threw outward is dragged into one
     * point above the player's head, and the score goes silent — this is
     * the inhale before the detonation.
     */
    private void drawImplosion(double p) {
        Location base = player.getLocation();
        Location core = base.clone().add(0, 1.6, 0);

        double radius = maxRadius * 1.3 * (1.0 - ease(p));
        int arms = strands * 3;
        for (int i = 0; i < arms; i++) {
            double angle = (elapsed * 0.6) + (i * (Math.PI * 2 / arms));
            double y = 1.6 + (1.0 - p) * 1.4 * Math.sin(i * 1.7);
            dustAt(base.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius), 1, 0.0, dust);
        }

        // The core tightens and brightens as everything falls into it.
        dustAt(core, 6, 0.12 * (1.0 - p), dustBright);
        if (rarity == Rarity.MYTHICAL) {
            puff(Particle.ELECTRIC_SPARK, core, 4, 0.15, 0.15, 0.15, 0.02);
        }
    }

    private void ring(Location center, double radius, int points, Particle.DustOptions options, double y) {
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 / points) * i;
            dustAt(center.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius), 1, 0.0, options);
        }
    }

    /** Smoothstep, so radii ease rather than moving linearly. */
    private static double ease(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return clamped * clamped * (3 - 2 * clamped);
    }

    // ---------------------------------------------------------------- finale

    /**
     * The payoff. Detonates immediately, then keeps running for
     * {@link #finaleTicks} — expanding shockwaves and falling embers — so
     * the end has a shape instead of being one frame that vanishes.
     */
    public void reveal() {
        if (finished) return;
        cancel();
        if (!player.isOnline()) return;

        detonate();

        long length = finaleTicks(rarity);
        final long[] frame = {0L};
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            frame[0]++;
            if (frame[0] > length || !player.isOnline()) {
                holder[0].cancel();
                return;
            }
            aftermath(frame[0], length);
        }, 1L, 1L);
    }

    private void detonate() {
        refreshAudience();
        Location base = player.getLocation();
        Location core = base.clone().add(0, 1.6, 0);

        dustAt(core, 220, maxRadius * 0.3, dustBright);
        puff(accent, core, 90, maxRadius * 0.22, 0.9, maxRadius * 0.22, 0.35);

        switch (rarity) {
            case MYTHICAL -> {
                // Real (harmless) lightning sells the thunder far better
                // than sound alone. strikeLightningEffect is visual only —
                // nothing burns and nobody takes damage. It's the one part
                // of the effect the engine can't send per-player, so it
                // shows even for players who muted the aura.
                for (int i = 0; i < 6; i++) {
                    double angle = (Math.PI * 2 / 6) * i;
                    player.getWorld().strikeLightningEffect(
                            base.clone().add(Math.cos(angle) * 4.0, 0, Math.sin(angle) * 4.0));
                }
                puff(Particle.SONIC_BOOM, core, 1, 0.0, 0.0, 0.0, 0.0);
                puff(Particle.EXPLOSION_EMITTER, core, 3, 1.2, 0.6, 1.2, 0.0);
                puff(Particle.DRAGON_BREATH, core, 260, 2.2, 1.4, 2.2, 0.4);
                puff(Particle.FLASH, core, 2, 0.0, 0.0, 0.0, 0.0);

                sound(Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 4.0f, 0.7f);
                sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 4.0f, 0.9f);
                sound(Sound.ENTITY_ENDER_DRAGON_GROWL, 4.0f, 1.3f);
                sound(Sound.ENTITY_WITHER_DEATH, 3.5f, 1.3f);
                sound(Sound.BLOCK_END_PORTAL_SPAWN, 3.0f, 1.4f);
            }
            case LEGENDARY -> {
                puff(Particle.TOTEM_OF_UNDYING, core, 140, 1.3, 1.0, 1.3, 0.45);
                puff(Particle.FIREWORK, core, 90, 1.1, 0.9, 1.1, 0.35);
                puff(Particle.FLASH, core, 1, 0.0, 0.0, 0.0, 0.0);
                ring(base, maxRadius * 1.3, 60, dustBright, 0.1);

                sound(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 3.0f, 1.0f);
                sound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.5f, 1.0f);
                sound(Sound.ITEM_TOTEM_USE, 2.5f, 1.2f);
            }
            default -> {
                puff(Particle.WITCH, core, 100, 0.9, 0.9, 0.9, 0.15);
                puff(Particle.END_ROD, core, 50, 0.6, 0.7, 0.6, 0.25);

                sound(Sound.BLOCK_BEACON_POWER_SELECT, 2.0f, 1.6f);
                sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f, 1.8f);
            }
        }
    }

    /**
     * What's left after the bang: rings rolling outward along the ground
     * and embers drifting down through where the drop appeared.
     */
    private void aftermath(long frame, long length) {
        refreshAudience();
        Location base = player.getLocation();
        double p = (double) frame / length;

        // Three staggered shockwaves so the ground keeps moving.
        for (int wave = 0; wave < 3; wave++) {
            double offset = wave * 0.22;
            double wp = p - offset;
            if (wp <= 0.0 || wp > 1.0) continue;
            ring(base, wp * (maxRadius * 2.2), (int) (18 + wp * 40), wave == 0 ? dustBright : dust, 0.03);
        }

        // Embers raining back down through the burst.
        if (frame % 2 == 0) {
            puff(accent, base.clone().add(0, 2.6, 0), 6, maxRadius * 0.35, 0.5, maxRadius * 0.35, 0.02);
        }

        if (rarity == Rarity.MYTHICAL) {
            // A long tail of settling sparks, and one late aftershock.
            puff(Particle.ELECTRIC_SPARK, base.clone().add(0, 1.2, 0), 5, 1.6, 0.9, 1.6, 0.05);
            if (frame == length / 2) {
                sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3.0f, 0.6f);
                puff(Particle.EXPLOSION_EMITTER, base.clone().add(0, 1.0, 0), 1, 0.5, 0.3, 0.5, 0.0);
            }
        }
    }
}
