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
 * timer - the roll ticks every 2 ticks, which is too coarse for the
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
 * so anyone who switched THIS RARITY's aura off in /options is skipped.
 * The toggle is per tier because the tiers are different events: a
 * Mythical once a month is a spectacle, an Epic several times an hour can
 * be a nuisance, and one switch can't express that.
 */
public final class RollAura {

    /** One scheduled sound in a rarity's score, fired when progress passes `at`. */
    private record Cue(double at, Sound sound, float volume, float pitch) {
    }

    // Everything from this progress point on is the implosion - the strands
    // stop orbiting and collapse into the point the drop bursts out of.
    private static final double IMPLODE_FROM = 0.88;

    // The frame Mythical's last bolt falls on. The ding fires here too -
    // the sound IS the lightning, not a follow-up to it.
    private static final long FINAL_STRIKE = 16L;

    // Ticks after the strike that the ding lands on. Long enough that the
    // impact sounds have cleared, short enough that the bolt is still lit.
    private static final long DING_OFFSET = 5L;

    /**
     * No particle of a burst is ever drawn nearer than this to the
     * roller's own eyes.
     *
     * Every cloud in the finale used to be centred on their chest, which
     * is the one place a first person camera cannot see. From outside it
     * read as a burst and from inside it read as a screen full of dust
     * with the drop somewhere behind it. Leon put it plainly: "ik wil sws
     * niet particles in mn gezicht". A burst reads as big because of where
     * its EDGE is, so the clouds are shells around the player now and the
     * middle is left empty.
     */
    private static final double CLEAR = 2.6;

    // ---------------------------------------------------------- per-rarity

    /** How long the payoff runs after the reveal, and how long rolling is locked. */
    public static long finaleTicks(Rarity rarity) {
        if (!isBigDrop(rarity)) return 0L;
        return switch (rarity) {
            case DIVINE -> 60L;    // 3s, the sphere and the pillar both need it
            case MYTHICAL -> 46L;  // 2.3s, enough for the burst to settle
            case LEGENDARY -> 28L; // 1.4s
            default -> 20L;        // 1s, Epic
        };
    }

    /**
     * How long to hold the item name back for. The burst is the payoff;
     * dropping a full-screen title over it the same tick hides the thing
     * the player waited ten seconds for.
     */
    public static long titleDelayTicks(Rarity rarity) {
        if (!isBigDrop(rarity)) return 0L;
        return rarity == Rarity.MYTHICAL || rarity == Rarity.DIVINE ? 18L : 6L;
    }

    private static double maxRadiusFor(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> 9.0;
            case MYTHICAL -> 7.0;
            case LEGENDARY -> 4.5;
            default -> 2.5; // Epic
        };
    }

    private static int strandsFor(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> 9;
            case MYTHICAL -> 7;
            case LEGENDARY -> 5;
            default -> 3; // Epic
        };
    }

    public static Color colorFor(Rarity rarity) {
        return switch (rarity) {
            // Divine is the warm near-white, never pure white: a
            // 255,255,255 dust cloud reads as a rendering glitch rather
            // than as light. Mythical takes the red, which is the most
            // aggressive colour in the plugin and belongs to the drop that
            // sounds like something breaking.
            case DIVINE -> Color.fromRGB(255, 252, 224);
            case MYTHICAL -> Color.fromRGB(255, 60, 60);
            case LEGENDARY -> Color.fromRGB(255, 170, 0);
            default -> Color.fromRGB(168, 85, 247); // Epic
        };
    }

    private static Particle accentFor(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> Particle.END_ROD;
            case MYTHICAL -> Particle.DRAGON_BREATH;
            case LEGENDARY -> Particle.FLAME;
            default -> Particle.END_ROD; // Epic
        };
    }

    /**
     * The audio score for the build-up. Nothing is scheduled past
     * IMPLODE_FROM on purpose - the silence there is what makes the
     * detonation land.
     */
    private static List<Cue> scoreFor(Rarity rarity) {
        List<Cue> cues = new ArrayList<>();
        switch (rarity) {
            case DIVINE -> {
                // The heavy one. Cue positions are fractions of the run,
                // so the same shape stretched over 15 seconds instead of
                // 10 lands with more air between the hits, not faster.
                cues.add(new Cue(0.00, Sound.ENTITY_ENDER_DRAGON_GROWL, 4.0f, 0.6f));
                cues.add(new Cue(0.10, Sound.BLOCK_PORTAL_TRIGGER, 2.0f, 0.5f));
                cues.add(new Cue(0.22, Sound.ENTITY_WITHER_SPAWN, 3.0f, 0.7f));
                cues.add(new Cue(0.34, Sound.BLOCK_CONDUIT_ACTIVATE, 4.0f, 0.6f));
                cues.add(new Cue(0.45, Sound.ENTITY_ENDER_DRAGON_GROWL, 4.0f, 0.8f));
                cues.add(new Cue(0.58, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 2.5f, 1.2f));
                cues.add(new Cue(0.68, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 4.0f, 1.4f));
                cues.add(new Cue(0.78, Sound.ENTITY_WITHER_SPAWN, 3.0f, 1.0f));
                cues.add(new Cue(0.86, Sound.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 1.0f));
            }
            case MYTHICAL -> {
                // Choral rather than violent: this one sounds like
                // something arriving. The thunder and the sonic boom stay
                // out of it so the Divine keeps the heaviest hits to
                // itself.
                cues.add(new Cue(0.00, Sound.BLOCK_BEACON_ACTIVATE, 4.0f, 0.5f));
                cues.add(new Cue(0.14, Sound.BLOCK_CONDUIT_ACTIVATE, 4.0f, 0.7f));
                cues.add(new Cue(0.30, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 4.0f, 0.6f));
                cues.add(new Cue(0.48, Sound.BLOCK_BEACON_POWER_SELECT, 4.0f, 0.5f));
                cues.add(new Cue(0.64, Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 4.0f, 1.4f));
                cues.add(new Cue(0.82, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 3.0f, 1.8f));
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
    // The look, which is the CURRENT stage's rather than the drop's.
    //
    // A reveal climbs the rarity ladder while its counter runs: it opens
    // small and violet in the lowest band the roller can see, and grows
    // and recolours each time the odds cross into the next one. Every one
    // of these is simply that stage's own rarity value, so a stage of the
    // climb and the aura that rarity wears on its own can never drift
    // apart. They are not final for that reason alone.
    private double maxRadius;
    private int strands;
    private Particle.DustOptions dust;
    private Particle.DustOptions dustBright;
    private Particle accent;
    /** The rarity whose look is currently being worn, which is the act's. */
    private Rarity look;

    // The acts. One per rarity band, each the same fixed length, and the
    // reveal simply stops at the end of the drop's own.
    private RollStages stages;
    private long actTicks = 100L;
    private int act = 0;
    private long actElapsed = 0L;
    // Each act plays its OWN band's score, so the seam between two
    // acts is audible as a change of instrument and not only as a
    // louder version of the same one.
    private List<Cue> score;
    private final double viewRange;

    private final List<Player> audience = new ArrayList<>();
    private int nextCue = 0;
    private long elapsed = 0L;
    private BukkitTask task;
    private boolean finished = false;
    // The ground circle, the only part of the reveal that is a shape rather
    // than a cloud of particles.
    private RollCircle circle;
    // The comet falling on the roller, and the odds counter under it. The
    // roller's half of the reveal: everything else here is drawn on them
    // and therefore behind their own camera.
    private RollComet comet;

    private RollAura(SolRNGPlugin plugin, Player player, Rarity rarity) {
        this.plugin = plugin;
        this.player = player;
        this.rarity = rarity;
        this.look = rarity;
        this.maxRadius = maxRadiusFor(rarity);
        this.strands = strandsFor(rarity);
        // The stage ladder overwrites these before the first frame when a
        // comet is flying; without one the drop's own rarity is the whole
        // look, which is what every reveal did before the ladder existed.
        Color color = colorFor(rarity);
        this.dust = new Particle.DustOptions(color, 1.3f);
        this.dustBright = new Particle.DustOptions(color, 2.4f);
        this.accent = accentFor(rarity);
        this.score = scoreFor(rarity);
        // Matches the loudest cue's reach (16 blocks per 1.0 volume), so
        // anyone who can hear it can also see it.
        this.viewRange = rarity == Rarity.DIVINE ? 72.0
                : rarity == Rarity.MYTHICAL ? 64.0
                : rarity == Rarity.LEGENDARY ? 48.0 : 32.0;
    }

    // ------------------------------------------------------------- lifecycle

    /** Epic and up get the aura; anything below rolls quietly. */
    public static boolean isBigDrop(Rarity rarity) {
        return rarity != null && rarity.ordinal() >= Rarity.EPIC.ordinal();
    }

    /**
     * How long a reveal runs: one act per rarity band, each the same fixed
     * length.
     *
     * Leon set this shape himself: five seconds of Epic, and then either
     * it ends there or it breaks through into five of Legendary, and so
     * on, so a Divine is twenty seconds and a Divine for somebody with the
     * Epic aura switched off is fifteen. The per rarity durations this
     * class used to carry are gone with it, because a roll whose LENGTH
     * was decided by the drop told the player what they had before the
     * first act was over.
     */
    public static long durationTicks(RollStages stages, long actTicks) {
        return stages == null || stages.isEmpty() ? 0L : stages.acts() * actTicks;
    }

    /** How long one act runs, from config, in ticks. */
    public static long actTicks(SolRNGPlugin plugin) {
        double seconds = Math.max(1.0, plugin.getConfig().getDouble("roll-item.comet.stage-seconds", 5.0));
        return Math.round(seconds * 20.0);
    }

    /**
     * Starts the build-up on its own task. Returns null for anything below
     * Epic, so callers can just null-check instead of branching on rarity.
     *
     * {@code odds} is the drop's own label, which the last act's counter
     * climbs to. {@code stages} is the ladder of bands this reveal walks;
     * an empty one means no climb and no comet, which is what an admin
     * preview with no drop behind it gets.
     */
    public static RollAura start(SolRNGPlugin plugin, Player player, Rarity rarity, long odds,
                                 RollStages stages, long actTicks) {
        if (!isBigDrop(rarity)) return null;

        RollAura aura = new RollAura(plugin, player, rarity);
        aura.stages = stages;
        aura.actTicks = Math.max(1L, actTicks);

        // The comet comes first, because its act decides which band the
        // whole reveal opens in. Built after the circle it would repaint a
        // floor already drawn in the drop's own colour, and the first
        // frame would give the answer away.
        //
        // For the roller alone, and only if they have this rarity's aura
        // switched on. Everybody else sees them standing still.
        if (stages != null && !stages.isEmpty() && RollComet.wanted(plugin, player, rarity)) {
            aura.comet = new RollComet(plugin, player, stages, odds, aura.actTicks);
            aura.wear(stages.rarityAt(0));
        }

        // The one solid shape in an effect made of particles. Past twenty
        // blocks a few hundred specks read as weather; a ring does not.
        aura.circle = new RollCircle(plugin, player, rarity, colorFor(aura.look), aura.maxRadius);
        aura.circle.start();
        // The comet opens only once the floor is under it, so the first
        // frame of an act is the whole act, not half of it.
        if (aura.comet != null) aura.comet.startAct(0);
        aura.task = plugin.getServer().getScheduler().runTaskTimer(plugin, aura::tick, 0L, 1L);
        return aura;
    }

    /**
     * Whether a comet is flying AND drawing the text on the screen.
     *
     * The reel asks this to decide whether to hand the title over and hold
     * a question mark in front of the roller for the whole build-up. With
     * no comet, or with its counter switched off in config, the reel keeps
     * its candidates and nothing changes.
     */
    public boolean ownsScreen() {
        return comet != null && comet.ownsScreen();
    }

    /** Stops the build-up without a payoff - used when a roll is abandoned. */
    public void cancel() {
        finished = true;
        if (task != null) task.cancel();
        if (circle != null) {
            circle.stop();
            circle = null;
        }
        if (comet != null) {
            comet.stop();
            comet = null;
        }
    }

    // ------------------------------------------------------------- delivery

    /**
     * Everyone in range who hasn't switched the aura off. Recomputed once
     * per frame - a Mythical frame makes dozens of particle calls, and
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
            if (!plugin.getPlayerDataManager().get(nearby.getUniqueId()).isAuraEnabled(rarity)) continue;
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

    /** True when a point is far enough from the roller's eyes to be drawn. */
    private boolean clearOfFace(Location at) {
        return at.getWorld() != null && at.getWorld().equals(player.getWorld())
                && at.distanceSquared(player.getEyeLocation()) >= CLEAR * CLEAR;
    }

    /**
     * A cloud turned inside out: {@code count} points scattered through a
     * shell around {@code centre} rather than a Gaussian ball centred on
     * it. Same number of particles, same reach, and the roller can see
     * through the middle of it.
     */
    private void shellDust(Location centre, int count, Particle.DustOptions options) {
        for (int i = 0; i < count; i++) {
            Location at = onShell(centre);
            for (Player viewer : audience) {
                viewer.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, options);
            }
        }
    }

    /** The same shell for a particle that takes no colour. */
    private void shellPuff(Particle particle, Location centre, int count, double extra) {
        for (int i = 0; i < count; i++) {
            Location at = onShell(centre);
            for (Player viewer : audience) {
                viewer.spawnParticle(particle, at, 1, 0.0, 0.0, 0.0, extra);
            }
        }
    }

    /** One point somewhere in the shell between CLEAR and the effect's own reach. */
    private Location onShell(Location centre) {
        double outer = Math.max(CLEAR + 1.2, maxRadius * 0.9);
        double radius = CLEAR + Math.random() * (outer - CLEAR);
        double theta = Math.random() * Math.PI * 2.0;
        // Uniform over the sphere rather than bunched at the poles.
        double z = Math.random() * 2.0 - 1.0;
        double ring = Math.sqrt(Math.max(0.0, 1.0 - z * z));
        return centre.clone().add(Math.cos(theta) * ring * radius, z * radius * 0.7,
                Math.sin(theta) * ring * radius);
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
        actElapsed++;
        refreshAudience();
        // Progress is through the ACT, not through the whole reveal. Every
        // act is a complete build-up: it gathers, charges, implodes and
        // either breaks through into the next band or is the ending.
        double progress = Math.min(1.0, (double) actElapsed / actTicks);

        playDueCues(progress);

        if (circle != null) {
            circle.tick(elapsed, progress, IMPLODE_FROM);
            // People walk in, and somebody who just switched this rarity
            // back on should see the rest of it.
            if (elapsed % 20 == 0) circle.refreshAudience();
        }

        if (comet != null) {
            // Its own catch rather than safely(), which logs and carries on:
            // a comet that throws would throw again every other tick for the
            // rest of the build-up. One line, then it is taken out and the
            // rest of the reveal plays without it.
            try {
                comet.tick(actElapsed, IMPLODE_FROM);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Roll comet (" + rarity + ") failed: " + ex);
                comet.stop();
                comet = null;
            }
        }

        if (progress < IMPLODE_FROM) {
            drawBuildUp(progress);
            // A rising note ladder under the score, so something is always
            // climbing even between cues.
            int noteEvery = look == Rarity.DIVINE ? 10 : 6;
            if (elapsed % noteEvery == 0) {
                sound(look == Rarity.EPIC ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BELL,
                        1.2f, (float) Math.min(2.0, 0.5 + progress * 1.5));
            }
        } else {
            drawImplosion((progress - IMPLODE_FROM) / (1.0 - IMPLODE_FROM));
        }

        // The act is over. Either the band the drop was actually found in
        // is next, and the reveal holds here for reveal() to end it, or it
        // breaks through and the next act opens on top of the burst.
        if (actElapsed >= actTicks && stages != null && stages.climbsAfter(act)) {
            breakthrough();
        }
    }

    /**
     * The seam between two acts: the counter has reached the next band's
     * entry, so the reveal grows into it.
     *
     * The comet hits first, which is what the moment sounds like, then
     * everything is repainted in the new band and a fresh comet launches
     * for the act that follows. The cue list restarts with it, so each act
     * plays its own band's score rather than one score stretched over the
     * whole run.
     */
    private void breakthrough() {
        act++;
        actElapsed = 0L;
        nextCue = 0;
        Rarity next = stages.rarityAt(act);
        if (comet != null) {
            try {
                comet.impact(false);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Roll comet breakthrough (" + rarity + ") failed: " + ex);
            }
        }
        wear(next);
        // The ring thrown outward on the seam, for everybody watching from
        // outside, who cannot see the comet or the counter at all. It is
        // the only thing that tells them the roll just got bigger.
        Location base = player.getLocation();
        for (int wave = 0; wave < 3; wave++) {
            ring(base, maxRadius * (0.6 + wave * 0.45), (int) (20 + maxRadius * 4),
                    wave == 0 ? dustBright : dust, 0.1 + wave * 0.6);
        }
        sound(Sound.ITEM_TRIDENT_THUNDER, 1.4f, (float) Math.min(2.0, 0.8 + 0.15 * act));
        if (comet != null) {
            try {
                comet.startAct(act);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Roll comet act " + act + " (" + rarity + ") failed: " + ex);
                comet.stop();
                comet = null;
            }
        }
    }

    /**
     * Puts on one stage of the ladder: that rarity's colour, size, strand
     * count and accent, and the ground circle with it.
     *
     * The score is deliberately NOT restyled. Its cues are fractions of
     * the whole run and they are the arc of the drop that is actually
     * coming, so a Divine keeps the Divine score from the first frame
     * while the picture climbs towards it. What marks a promotion in the
     * audio is the comet's own hit on the frame it happens.
     */
    private void wear(Rarity stage) {
        this.look = stage;
        this.score = scoreFor(stage);
        Color colour = colorFor(stage);
        this.maxRadius = maxRadiusFor(stage);
        this.strands = strandsFor(stage);
        this.dust = new Particle.DustOptions(colour, 1.3f);
        this.dustBright = new Particle.DustOptions(colour, 2.4f);
        this.accent = accentFor(stage);
        if (circle != null) circle.restyle(colour, maxRadius);
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

        // Divine adds a shockwave that resets every 40 ticks and a column
        // of light straight up.
        if (rarity == Rarity.DIVINE) {
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
     * point above the player's head, and the score goes silent - this is
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
        if (rarity == Rarity.DIVINE) {
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
     * The payoff. Runs as a scripted timeline rather than one burst.
     *
     * The first version drew everything at the player's own head, which is
     * exactly where a first-person camera can't see it - you end up
     * standing inside a two-block cloud while a full-screen title covers
     * it. The build-up read well for the opposite reason: it was seven
     * blocks out in front of you. So the finale now travels OUTWARD and
     * UPWARD, through and past the viewer.
     *
     * Every beat is wrapped so one bad call can't silently kill the rest
     * of the sequence - a thrown particle used to take the remaining
     * sounds and visuals down with it, with nothing in the log to say so.
     */
    public void reveal() {
        if (finished) return;
        // Taken out of the field before cancel(), which stops the circle
        // dead for an abandoned roll. This is the other case: it has earned
        // its throw outward, and it plays over the first frames of the
        // finale whichever script that finale is.
        RollCircle thrown = circle;
        circle = null;
        // Same reason: the comet has earned its impact, and cancel() would
        // take it out of the sky a frame before it arrived.
        RollComet landing = comet;
        comet = null;
        cancel();
        if (!player.isOnline()) {
            if (thrown != null) thrown.stop();
            if (landing != null) landing.stop();
            return;
        }
        if (thrown != null) thrown.detonate();
        if (landing != null) landing.impact(true);

        long length = finaleTicks(rarity);
        final long[] frame = {0L};
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            frame[0]++;
            if (frame[0] > length || !player.isOnline()) {
                holder[0].cancel();
                return;
            }
            safely("finale frame " + frame[0], () -> {
                refreshAudience();
                if (rarity == Rarity.DIVINE) {
                    beatFinale(frame[0], length);
                } else {
                    if (frame[0] == 1) detonate();
                    aftermath(frame[0], length);
                }
            });
        }, 0L, 1L);
    }

    private void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Reveal aura (" + rarity + ") failed at " + what
                    + ": " + ex);
        }
    }

    // ------------------------------------------------------- mythical script

    /**
     * Five seconds, built so it's readable from inside the effect and from
     * across the map:
     *
     *   f1       the crack - flash, explosion, eight bolts at 6 blocks
     *   f1-45    a shell expanding from 1 to 18 blocks, sweeping past you
     *   f1-100   a pillar of light 30 blocks into the sky
     *   f6/16/28 three widening lightning rings, thunder dropping in pitch
     *   f1-100   four ground shockwaves rolling out to 24 blocks
     *   f20-100  embers raining down from above through the whole area
     *   f50      the aftershock
     *   f78      the settle
     */
    /**
     * The big finale: a real sphere, a pillar, and the strike. Divine's
     * alone now - Mythical takes the detonate-and-settle ending, which is
     * the quieter of the two and belongs on the commoner drop.
     */
    private void beatFinale(long frame, long length) {
        Location base = player.getLocation();
        Location core = base.clone().add(0, 1.6, 0);

        if (frame == 1) {
            puff(Particle.FLASH, core, 3, 0.0, 0.0, 0.0, 0.0);
            puff(Particle.SONIC_BOOM, base.clone().add(0, 6.0, 0), 1, 0.0, 0.0, 0.0, 0.0);
            shellPuff(Particle.EXPLOSION_EMITTER, core, 4, 0.0);
            shellPuff(accent, core, 340, 0.45);
            shellDust(core, 300, dustBright);
            lightningRing(8.0, 10);

            sound(Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 4.0f, 0.6f);
            sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 4.0f, 0.8f);
            sound(Sound.ENTITY_WITHER_DEATH, 4.0f, 1.2f);
            sound(Sound.BLOCK_END_PORTAL_SPAWN, 3.5f, 1.2f);
        }

        // The shell: a real sphere growing out through the viewer. This is
        // the part that makes the burst visible from inside it.
        if (frame <= 26) {
            double p = frame / 26.0;
            double radius = CLEAR + ease(p) * 20.0;
            // Thins out as it grows so the far edge doesn't turn into a wall.
            int rings = p < 0.5 ? 7 : 5;
            int points = p < 0.5 ? 18 : 12;
            sphere(core, radius, rings, points, p < 0.35 ? dustBright : dust);
        }

        // The pillar: straight up, so it's the landmark everyone turns to.
        double pillarLife = 1.0 - ((double) frame / length);
        double pillarWidth = 0.4 + 1.6 * Math.sin(Math.min(1.0, frame / 14.0) * Math.PI * 0.5) * pillarLife;
        for (double y = 0.0; y < 40.0; y += 1.0) {
            double sway = Math.sin((y * 0.4) + (frame * 0.25)) * pillarWidth;
            Location point = base.clone().add(sway, y,
                    Math.cos((y * 0.4) + (frame * 0.25)) * pillarWidth);
            // The column stands through the roller, so the two or three
            // points at their own head are left out. From outside the gap
            // is invisible; from inside it is the difference between a
            // landmark and a faceful of dust.
            if (!clearOfFace(point)) continue;
            dustAt(point, 1, 0.05, y < 6 ? dustBright : dust);
        }
        if (frame % 3 == 0) {
            puff(Particle.ELECTRIC_SPARK, base.clone().add(0, 4.0 + (frame % 20), 0), 4, 0.6, 0.6, 0.6, 0.04);
        }

        // Two widening rings, then THE strike - the last bolt and the ding
        // land on the same frame, so the sound is the lightning rather than
        // an afterthought several seconds behind it.
        if (frame == 8) {
            lightningRing(10.0, 6);
            sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 4.0f, 0.7f);
        } else if (frame == FINAL_STRIKE) {
            lightningRing(16.0, 10);
            puff(Particle.FLASH, core, 2, 0.0, 0.0, 0.0, 0.0);
            shellPuff(Particle.EXPLOSION_EMITTER, core, 2, 0.0);
            sound(Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 4.0f, 0.7f);
            sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 4.0f, 0.5f);
        } else if (frame == FINAL_STRIKE + DING_OFFSET) {
            // The ding gets its own frame. On the strike tick it was
            // competing with a thunder crack, an impact and a growl, all at
            // volume 4 - Minecraft drops samples when too many play at once,
            // and the quiet bells are the first to go. Five ticks later the
            // bolt is still on screen but the channel is clear.
            dingChord(4.0f);
        }

        // Ground shockwaves rolling out past the aura. Drawn every other
        // frame and at moderate density - at full rate these rings alone
        // were most of the packets in the scene.
        if (frame % 2 == 0) {
            for (int wave = 0; wave < 3; wave++) {
                double wp = ((double) frame / length) - (wave * 0.18);
                if (wp <= 0.0 || wp > 1.0) continue;
                double radius = ease(wp) * 24.0;
                ring(base, radius, (int) (14 + radius * 1.6), wave == 0 ? dustBright : dust, 0.05);
            }
        }

        // Embers falling back down through the whole area - this is the
        // only thing that runs past the strike, so the scene settles rather
        // than cutting out.
        if (frame >= 10 && frame % 2 == 0) {
            puff(Particle.DRAGON_BREATH, base.clone().add(0, 18.0, 0), 12, 11.0, 3.0, 11.0, 0.01);
            puff(Particle.ELECTRIC_SPARK, base.clone().add(0, 12.0, 0), 8, 8.0, 4.0, 8.0, 0.03);
        }
    }

    /**
     * The ding. Five samples on the SAME tick rather than an arpeggio over
     * three: spread out it arrived long after the visual and read as a
     * separate event, where a stacked chord lands as one bright hit on the
     * lightning itself.
     *
     * The two bells a fifth apart plus the chime give it body; the toast
     * sting on top is what makes it read as "you got something".
     */
    private void dingChord(float volume) {
        sound(Sound.BLOCK_NOTE_BLOCK_BELL, volume, 1.50f);
        sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume, 1.20f);
        sound(Sound.UI_TOAST_CHALLENGE_COMPLETE, volume, 1.0f);
    }

    /** A ring of harmless, visual-only bolts around the player. */
    private void lightningRing(double radius, int bolts) {
        Location base = player.getLocation();
        for (int i = 0; i < bolts; i++) {
            double angle = (Math.PI * 2 / bolts) * i + (radius * 0.7);
            player.getWorld().strikeLightningEffect(
                    base.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
        }
    }

    /** A hollow sphere drawn as latitude rings - the expanding shell. */
    private void sphere(Location centre, double radius, int rings, int pointsPerRing,
                        Particle.DustOptions options) {
        for (int i = 1; i < rings; i++) {
            double phi = Math.PI * i / rings;
            double y = Math.cos(phi) * radius;
            double r = Math.sin(phi) * radius;
            for (int j = 0; j < pointsPerRing; j++) {
                double theta = (Math.PI * 2 / pointsPerRing) * j;
                dustAt(centre.clone().add(Math.cos(theta) * r, y, Math.sin(theta) * r), 1, 0.0, options);
            }
        }
    }

    // -------------------------------------------------- epic / legendary

    private void detonate() {
        Location base = player.getLocation();
        Location core = base.clone().add(0, 1.6, 0);

        shellDust(core, 220, dustBright);
        shellPuff(accent, core, 90, 0.35);

        if (rarity == Rarity.MYTHICAL) {
            // Mythical ran the beat finale until the two swapped, and it
            // landed on the shared ending underneath, which finished the
            // second longest build-up in the plugin on an XP pickup noise.
            // It gets its own arrival now.
            // FLASH is a screen wide flare with no position to speak of,
            // so it is the one thing that may sit on the camera. The boom
            // goes overhead, where it is a shape rather than a wall.
            puff(Particle.FLASH, core, 1, 0.0, 0.0, 0.0, 0.0);
            puff(Particle.SONIC_BOOM, base.clone().add(0, 5.0, 0), 1, 0.0, 0.0, 0.0, 0.0);
            shellPuff(Particle.EXPLOSION_EMITTER, core, 2, 0.0);
            shellPuff(Particle.TOTEM_OF_UNDYING, core, 170, 0.5);
            shellPuff(accent, core, 120, 0.4);
            shellDust(core, 200, dustBright);
            ring(base, maxRadius * 1.5, 70, dustBright, 0.1);
            lightningRing(maxRadius * 0.8, 6);

            // Impact first, then the portal, then the shriek on top. Three
            // sounds on one frame read as one big one as long as their
            // pitches are far enough apart to stay distinct.
            sound(Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 4.0f, 0.9f);
            sound(Sound.BLOCK_END_PORTAL_SPAWN, 3.5f, 1.3f);
            sound(Sound.ENTITY_WITHER_SPAWN, 3.0f, 1.5f);
        } else if (rarity == Rarity.LEGENDARY) {
            shellPuff(Particle.TOTEM_OF_UNDYING, core, 140, 0.45);
            shellPuff(Particle.FIREWORK, core, 90, 0.35);
            puff(Particle.FLASH, core, 1, 0.0, 0.0, 0.0, 0.0);
            ring(base, maxRadius * 1.3, 60, dustBright, 0.1);

            sound(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 3.0f, 1.0f);
            sound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.5f, 1.0f);
            sound(Sound.ITEM_TOTEM_USE, 2.5f, 1.2f);
        } else {
            shellPuff(Particle.WITCH, core, 100, 0.15);
            shellPuff(Particle.END_ROD, core, 50, 0.25);

            sound(Sound.BLOCK_BEACON_POWER_SELECT, 2.0f, 1.6f);
            sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f, 1.8f);
        }
    }

    /**
     * What's left after the bang: rings rolling outward along the ground
     * and embers drifting down through where the drop appeared.
     */
    private void aftermath(long frame, long length) {
        Location base = player.getLocation();
        double p = (double) frame / length;

        for (int wave = 0; wave < 3; wave++) {
            double wp = p - (wave * 0.22);
            if (wp <= 0.0 || wp > 1.0) continue;
            ring(base, wp * (maxRadius * 2.2), (int) (18 + wp * 40), wave == 0 ? dustBright : dust, 0.03);
        }

        if (frame % 2 == 0) {
            puff(accent, base.clone().add(0, 2.6, 0), 6, maxRadius * 0.35, 0.5, maxRadius * 0.35, 0.02);
        }

        // The burst is on frame 1, so the ding belongs right behind it. At
        // frame 26 it was landing well after the visual had already
        // finished, which read as an afterthought rather than a payoff.
        if (frame == 5 && (rarity == Rarity.LEGENDARY || rarity == Rarity.MYTHICAL)) {
            dingChord(rarity == Rarity.MYTHICAL ? 4.0f : 3.0f);
        }
    }
}
