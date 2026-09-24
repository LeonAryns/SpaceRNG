package com.spacerng.solrng.roll;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.aura.AuraConcepts;
import com.spacerng.solrng.aura.AuraParts;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.rarity.RollFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The comet that falls on an Epic or better roll, the odds counting up in
 * front of the roller, and the climb through the rarity bands that both
 * of them ride.
 *
 * The build-up in {@link RollAura} happens on the player: strands winding
 * round them, a circle opening under their feet. That reads well from
 * outside and it reads as nothing at all in first person, because all of
 * it is behind or below the camera. This is the half of the reveal that
 * is meant to be looked AT. It starts high in the sky in the direction
 * the roller is already facing, so it is in view from the first frame
 * without anything touching their camera, and it comes down on them.
 *
 * It opens in the LOWEST band the roller can see, not in the drop's own.
 * A Divine used to be Divine from the first frame, which gives the answer
 * away before anything has happened. Now the counter climbs, and every
 * time it crosses into the next rarity's odds the comet grows, recolours
 * and hits, and the number is thrown out of the screen and pulled back.
 * {@link RollStages} owns that ladder and {@link RollAura} follows this
 * class up it, so the floor, the strands and the sky all change together.
 *
 * It belongs to the roller alone. Every piece is spawned hidden and shown
 * to one player, every particle and every sound is sent to that one
 * player, and nobody else sees a thing: from outside they are a person
 * standing still with the usual aura winding up. That is also why the
 * budget here is generous compared to the rest of the plugin. One viewer
 * means the per frame cost never multiplies, so the trail can be a solid
 * streak rather than a dotted line.
 *
 * The audio stops at the same point the aura's score does. A comet
 * screaming through the hush before the detonation would take the silence
 * that makes the bang land, so the last stretch is a plunge with no sound
 * under it at all.
 */
final class RollComet {

    /** The path is redrawn every this many ticks; the client glides between. */
    private static final int EVERY = 2;

    /**
     * How the fall is paced. Above 1 the comet hangs high early and then
     * plunges, which is what turns a straight line into something with
     * weight.
     */
    private static final double FALL_EASE = 1.8;

    /** How far the path swings round while it comes in, in degrees. */
    private static final double CURVE = 45.0;

    /** At most this many degrees of view steering per update, when it is on. */
    private static final float STEER_STEP = 2.5f;

    /** How long the final number hangs after the impact, in ticks. */
    private static final long HOLD_AFTER_LANDING = 30L;

    // ---------------------------------------------------------- per rarity

    /**
     * How high it starts, and how far out in front. Both come from the
     * DROP's rarity and never change with the stage: the ladder moves the
     * look, and a path that resized halfway down would visibly jump.
     */
    private static double heightFor(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> 90.0;
            case MYTHICAL -> 70.0;
            case LEGENDARY -> 55.0;
            default -> 40.0; // Epic
        };
    }

    private static double reachFor(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> 50.0;
            case MYTHICAL -> 40.0;
            case LEGENDARY -> 30.0;
            default -> 22.0; // Epic
        };
    }

    /** How big the head is, in blocks. This one IS per stage. */
    private static float sizeFor(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> 2.6f;
            case MYTHICAL -> 2.0f;
            case LEGENDARY -> 1.5f;
            default -> 1.1f; // Epic
        };
    }

    /** How many points of trail are laid down per step. Per stage as well. */
    private static int trailFor(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> 14;
            case MYTHICAL -> 12;
            case LEGENDARY -> 10;
            default -> 8; // Epic
        };
    }

    /**
     * How big the number on the screen is at each stage, before the
     * multiplier in config. Epic is meant to look ordinary so that a
     * promotion to Legendary reads as one.
     */
    private static float counterScaleFor(Rarity rarity) {
        // Sized against the screen rather than by eye. A display 1.5 blocks
        // in front of the camera looks onto about 3.7 blocks of width, and
        // "1 in 10,000,000" at scale 1 is about 1.6 blocks of text, so the
        // top of this table already fills nearly half the screen and
        // anything near 2 would run off both edges. The real judgement is
        // in game, which is what roll-item.comet.counter-scale is for.
        return switch (rarity) {
            case DIVINE -> 1.05f;
            case MYTHICAL -> 0.85f;
            case LEGENDARY -> 0.7f;
            default -> 0.55f; // Epic
        };
    }

    // -------------------------------------------------------------- state

    private final SolRNGPlugin plugin;
    private final Player player;
    private final Rarity drop;
    private final RollStages stages;
    private final long odds;
    private final boolean counter;
    private final boolean steer;
    private final float counterScale;
    private final double height;
    private final double reach;
    /**
     * How long the comet has to arrive, which is the length of the ROLL
     * and not of the aura's build-up.
     *
     * The two are not the same number. An Epic's build-up is three seconds
     * and the roll it sits in is five or more, and the aura simply holds
     * at full implosion for the rest. A comet cannot hold: flown against
     * the aura's clock it would land two seconds early and then hang
     * inside the roller's chest until the drop finally arrived.
     */
    private final long flightTicks;
    /** Which way it comes in from, fixed when it starts so it cannot chase a turning head. */
    private final double bearing;

    // The stage, and everything the stage paints.
    private int stageIndex;
    private Rarity stage;
    private Color colour;
    private Particle.DustTransition tail;
    private Particle.DustOptions spark;
    private Particle accent;
    private int trailPoints;

    private BlockDisplay head;
    private RollCounter readout;
    private Location last;
    private long lastStep = -EVERY;
    private long lastRoar = 0L;
    private boolean done;

    RollComet(SolRNGPlugin plugin, Player player, Rarity drop, RollStages stages, long odds,
              long flightTicks) {
        this.plugin = plugin;
        this.player = player;
        this.drop = drop;
        this.stages = stages;
        this.odds = odds;
        this.counter = plugin.getConfig().getBoolean("roll-item.comet.counter", true);
        // Off by default. Steering writes the player's real rotation, and
        // the client reports that back, so everybody else would see them
        // spin round. Aiming the comet into the view they already have is
        // what keeps the whole thing private.
        this.steer = plugin.getConfig().getBoolean("roll-item.comet.steer-view", false);
        this.counterScale = (float) Math.max(0.2,
                plugin.getConfig().getDouble("roll-item.comet.counter-scale", 1.0));
        this.height = heightFor(drop);
        this.reach = reachFor(drop);
        this.flightTicks = Math.max(1L, flightTicks);
        this.stageIndex = 0;
        paint(stages.rarityAt(0));

        Vector look = player.getLocation().getDirection().setY(0.0);
        if (look.lengthSquared() < 1.0e-4) look = new Vector(0.0, 0.0, 1.0);
        look.normalize();
        this.bearing = Math.atan2(look.getZ(), look.getX());
    }

    /** Whether a comet should fly at all: switched on, and this roller wants the show. */
    static boolean wanted(SolRNGPlugin plugin, Player player, Rarity rarity) {
        if (!plugin.getConfig().getBoolean("roll-item.comet.enabled", true)) return false;
        return plugin.getPlayerDataManager().get(player.getUniqueId()).isAuraEnabled(rarity);
    }

    /** Which rung of the ladder the look is on, for the aura to follow. */
    int stageIndex() {
        return stageIndex;
    }

    Rarity stageRarity() {
        return stage;
    }

    /**
     * Whether this comet is the one drawing the text on the screen. When
     * the counter is switched off in config the reel keeps the title and
     * the drop shows its usual candidates, so nothing is left blank.
     */
    boolean ownsScreen() {
        return counter;
    }

    // ---------------------------------------------------------- lifecycle

    /** Puts it in the sky, lit and glowing, visible to the roller and to nobody else. */
    void start() {
        Location at = positionAt(0.0);
        this.last = at;
        // Reach a little past the starting height, or the head is culled by
        // the client on the frames where it is furthest away and the whole
        // thing appears out of nothing halfway down.
        head = plugin.getAuraManager().parts().block(at,
                AuraConcepts.lantern(stage).createBlockData(), height * 1.6, true, box(stage));
        // The one piece of the reveal that is seen through terrain. A comet
        // behind a hill that cannot be seen until it clears the ridge is a
        // comet half the players never notice.
        AuraParts.glowing(head, colour);
        // Not riding anything, so it wants smoothing: the path is sent every
        // two ticks and the client glides across them.
        head.setTeleportDuration(EVERY);
        player.showEntity(plugin, head);

        if (counter && wantsText()) {
            readout = new RollCounter(plugin, player, counterScale * counterScaleFor(stage));
            readout.show(line(stages.shownOdds(0.0)));
        }
        player.playSound(at, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.7f, 0.5f);
    }

    /**
     * One step of the fall, on the aura's own clock but against the roll's
     * length, so the comet arrives exactly when the drop does.
     */
    void tick(long elapsed, double hushFrom) {
        if (done || head == null || !head.isValid()) return;
        if (elapsed - lastStep < EVERY) return;
        lastStep = elapsed;
        // A roller who walked through a portal mid-roll. Teleporting a
        // display across worlds to chase them is not worth the edge cases.
        if (!player.getWorld().equals(head.getWorld())) {
            stop();
            return;
        }

        double progress = Math.min(1.0, (double) elapsed / flightTicks);
        Location now = positionAt(progress);
        head.teleport(now);
        streak(last, now);
        last = now;

        long shown = stages.shownOdds(progress);
        int wanted = stages.indexAt(shown);
        if (wanted > stageIndex) promote(wanted, shown, now);
        else if (counter && readout != null) readout.show(line(shown));

        if (progress < hushFrom) roar(elapsed, progress, now);
        if (steer) steerTowards(now);
    }

    /**
     * A promotion: the counter has crossed into the next rarity's odds.
     *
     * Everything moves at once, which is the whole point. The head swells
     * into the new band's size and takes its colour and its lit block, the
     * trail thickens, the number is thrown out of the screen, and two
     * sounds land together low and high so the step is audible with your
     * eyes shut. {@link RollAura} reads the new stage off this on the same
     * tick and brings the floor and the strands with it.
     *
     * A jump of more than one rung is possible on a fast roll, and lands
     * as one promotion straight to the band it reached rather than as a
     * stutter of two.
     */
    private void promote(int index, long shown, Location at) {
        stageIndex = index;
        paint(stages.rarityAt(index));
        if (head.isValid()) {
            head.setBlock(AuraConcepts.lantern(stage).createBlockData());
            head.setGlowColorOverride(colour);
            head.setInterpolationDelay(0);
            head.setInterpolationDuration(EVERY * 2);
            head.setTransformationMatrix(box(stage));
        }
        if (counter && readout != null) {
            readout.pop(line(shown), counterScale * counterScaleFor(stage));
        }
        // Low and high together, climbing a step per rung, so a promotion
        // reads as one larger sound rather than as two stacked.
        float step = 0.18f * index;
        player.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, Math.min(2.0f, 0.7f + step));
        player.playSound(at, Sound.ENTITY_ENDER_EYE_DEATH, 0.9f, Math.min(2.0f, 1.1f + step));
    }

    /** Everything one rung of the ladder paints, in one place. */
    private void paint(Rarity rarity) {
        this.stage = rarity;
        this.colour = RollAura.colorFor(rarity);
        // Warm near-white. A pure white tail reads as a rendering glitch.
        Color pale = Color.fromRGB(255, 252, 224);
        this.tail = new Particle.DustTransition(colour, pale, 2.4f);
        this.spark = new Particle.DustOptions(pale, 1.1f);
        this.accent = switch (rarity) {
            case MYTHICAL -> Particle.DRAGON_BREATH;
            case LEGENDARY -> Particle.FLAME;
            default -> Particle.END_ROD;
        };
        this.trailPoints = trailFor(rarity);
    }

    /**
     * The impact. The burst itself belongs to {@link RollAura#reveal()},
     * which already detonates in the rarity's own colour, so this is only
     * the comet's own share of it: the head goes, the sky flashes and the
     * last of the trail is thrown out sideways.
     *
     * The number stops on the drop's real odds and hangs a moment longer
     * than the burst, so the thing the whole build-up was counting towards
     * is still on the screen when the drop's name arrives under it.
     */
    void land() {
        if (done) return;
        done = true;
        Location at = player.getLocation().add(0.0, 1.0, 0.0);
        if (head != null) {
            if (head.isValid()) head.remove();
            head = null;
        }
        // FLASH takes a Colour since 1.21.11, which is the one particle in
        // the game that fills the screen in a colour we choose.
        player.spawnParticle(Particle.FLASH, at, 1, 0.0, 0.0, 0.0, 0.0, colour);
        player.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 40, 0.9, 0.9, 0.9, 0.0, tail);
        player.spawnParticle(accent, at, 24, 0.5, 0.5, 0.5, 0.18);

        if (readout != null) {
            RollCounter last = readout;
            readout = null;
            if (counter && odds > 0L) {
                last.pop(line(odds), counterScale * counterScaleFor(stages.rarityAt(stageIndex)));
            }
            plugin.getServer().getScheduler().runTaskLater(plugin, last::stop, HOLD_AFTER_LANDING);
        }
    }

    /** Stops it dead, for a roll that was abandoned. */
    void stop() {
        done = true;
        if (head != null) {
            if (head.isValid()) head.remove();
            head = null;
        }
        if (readout != null) {
            readout.stop();
            readout = null;
        }
    }

    // ------------------------------------------------------------ the path

    /**
     * Where the comet is at {@code progress}.
     *
     * The target is read fresh every step rather than fixed at the start,
     * so a roller who walks a few blocks still gets hit by it instead of
     * watching it land where they used to be. The bearing is not: that is
     * fixed, or a comet would swing round the sky every time somebody
     * turned their head.
     */
    private Location positionAt(double progress) {
        double t = Math.pow(Math.max(0.0, Math.min(1.0, progress)), FALL_EASE);
        double radius = reach * (1.0 - t);
        double angle = bearing + Math.toRadians(CURVE) * t;
        Location at = player.getLocation().add(Math.cos(angle) * radius,
                height * (1.0 - t) + 1.0, Math.sin(angle) * radius);
        // A display takes the rotation of wherever it is put, and this is
        // built off the player's own location. Left alone, the comet would
        // tumble with every turn of the roller's head.
        at.setYaw(0f);
        at.setPitch(0f);
        return at;
    }

    /** The head's model, centred on its own origin so it grows from the middle. */
    private static Matrix4f box(Rarity rarity) {
        float size = sizeFor(rarity);
        return AuraParts.box(0f, 0f, 0f, new Quaternionf(), size, size, size);
    }

    /** The trail, laid down along the ground it covered since the last step. */
    private void streak(Location from, Location to) {
        Vector step = to.toVector().subtract(from.toVector()).multiply(1.0 / trailPoints);
        Location cursor = from.clone();
        for (int i = 0; i < trailPoints; i++) {
            cursor.add(step);
            player.spawnParticle(Particle.DUST_COLOR_TRANSITION, cursor, 1, 0.12, 0.12, 0.12, 0.0, tail);
        }
        // Embers shedding off the head, thrown backwards along the path.
        player.spawnParticle(accent, to, 3, 0.25, 0.25, 0.25, 0.02);
        player.spawnParticle(Particle.DUST, to, 2, 0.4, 0.4, 0.4, 0.0, spark);
    }

    /**
     * The approach. One heavy beat, getting louder and higher as it closes,
     * with the pitch nudged each time so a repeated sound does not start to
     * read as a machine.
     */
    private void roar(long elapsed, double progress, Location at) {
        long every = drop == Rarity.EPIC ? 10L : 16L;
        if (elapsed - lastRoar < every) return;
        lastRoar = elapsed;
        float jitter = (float) (ThreadLocalRandom.current().nextDouble() * 0.1 - 0.05);
        player.playSound(at, Sound.ENTITY_ENDER_DRAGON_FLAP,
                (float) (0.4 + 0.6 * progress), (float) (0.5 + 0.7 * progress) + jitter);
        if (progress > 0.5) {
            player.playSound(at, Sound.ITEM_FIRECHARGE_USE,
                    (float) (0.3 + 0.5 * progress), (float) (0.8 + 0.6 * progress) + jitter);
        }
    }

    // --------------------------------------------------------- the counter

    /**
     * Whether text belongs on this player's screen at all. The same switch
     * the reel's own titles obey: somebody who turned the rolling
     * animation off asked for no text, and a comet is not a reason to put
     * some back.
     */
    private boolean wantsText() {
        return plugin.getPlayerDataManager().get(player.getUniqueId()).isRollAnimationEnabled();
    }

    /** One frame of the number, in the colours of the band it is currently in. */
    private Component line(long value) {
        return LegacyComponentSerializer.legacySection()
                .deserialize(plugin.getRarityManager().styleBold(stage, RollFormat.chance(value)));
    }

    // ---------------------------------------------------------- the camera

    /**
     * Swings the view towards the comet, a couple of degrees at a time, for
     * anybody who switches it on.
     *
     * This is the one part of the cutscene other people can see, and there
     * is no way round it: Paper has no camera of its own, so the only lever
     * is the player's real rotation, and the client reports that straight
     * back to the server. Left off by default for exactly that reason.
     */
    private void steerTowards(Location comet) {
        Vector to = comet.toVector().subtract(player.getEyeLocation().toVector());
        if (to.lengthSquared() < 1.0e-4) return;
        to.normalize();
        float wantYaw = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
        float wantPitch = (float) Math.toDegrees(-Math.asin(Math.max(-1.0, Math.min(1.0, to.getY()))));
        Location at = player.getLocation();
        player.setRotation(towards(at.getYaw(), wantYaw, STEER_STEP),
                towards(at.getPitch(), wantPitch, STEER_STEP));
    }

    /** One step of at most {@code max} degrees from {@code current} to {@code target}, the short way round. */
    private static float towards(float current, float target, float max) {
        float delta = ((target - current) % 360f + 540f) % 360f - 180f;
        if (delta > max) delta = max;
        if (delta < -max) delta = -max;
        return current + delta;
    }
}
