package com.spacerng.solrng.roll;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.aura.AuraConcepts;
import com.spacerng.solrng.aura.AuraParts;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.rarity.RollFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
 * One act of a reveal: a comet falling out of the sky, and the odds
 * climbing in front of the roller while it comes.
 *
 * The build-up in {@link RollAura} happens on the player: strands winding
 * round them, a circle opening under their feet. That reads well from
 * outside and it reads as nothing at all in first person, because all of
 * it is behind or below the camera. This is the half of the reveal that
 * is meant to be looked AT. It starts high in the sky in the direction
 * the roller is already facing, so it is in view from the first frame
 * without anything touching their camera, and it comes down on them.
 *
 * <h2>A comet per act</h2>
 *
 * Each rarity band is its own act of fixed length, and each act gets its
 * own comet: it launches at the top of the act and hits at the bottom of
 * it. That is not decoration, it is the fix for the thing Leon caught in
 * V196. The path used to be sized from the DROP's rarity so it would not
 * jump when a band changed, which meant a Divine's comet started ninety
 * blocks up on its very first frame and an Epic's forty, and you knew
 * what you had before the first act was over. Now the path is sized from
 * the ACT, so the opening act of a Divine is identical to the only act of
 * an Epic, and the size of the thing coming down at you tells you exactly
 * as much as it should: which band you are in right now.
 *
 * It belongs to the roller alone. Every piece is spawned hidden and shown
 * to one player, every particle and every sound is sent to that one
 * player, and nobody else sees a thing: from outside they are a person
 * standing still with the usual aura winding up. That is also why the
 * budget here is generous compared to the rest of the plugin. One viewer
 * means the per frame cost never multiplies, so the trail can be a solid
 * streak rather than a dotted line.
 *
 * Nothing it draws lands inside {@link #CLEAR} blocks of the roller's
 * head. Particles in your own face are not an effect, they are a screen
 * full of dust where the effect used to be.
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

    /**
     * No particle of a burst is ever drawn nearer than this to the
     * roller's eyes. Leon asked for it twice in one message: "ik wil sws
     * niet particles in mn gezicht". A burst reads as big because of where
     * its EDGE is, and a cloud centred on the camera hides its own shape.
     */
    private static final double CLEAR = 2.6;

    // ---------------------------------------------------- per act, by band

    /** How high the act's comet starts. */
    private static double heightFor(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> 90.0;
            case MYTHICAL -> 70.0;
            case LEGENDARY -> 55.0;
            default -> 40.0; // Epic
        };
    }

    /** How far out in front of the roller it starts. */
    private static double reachFor(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> 50.0;
            case MYTHICAL -> 40.0;
            case LEGENDARY -> 30.0;
            default -> 22.0; // Epic
        };
    }

    /** How big the head is, in blocks. */
    private static float sizeFor(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> 2.6f;
            case MYTHICAL -> 2.0f;
            case LEGENDARY -> 1.5f;
            default -> 1.1f; // Epic
        };
    }

    /** How many points of trail are laid down per step. */
    private static int trailFor(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> 14;
            case MYTHICAL -> 12;
            case LEGENDARY -> 10;
            default -> 8; // Epic
        };
    }

    /**
     * How big the number on the screen is in each band, before the
     * multiplier in config. Raised across the board in V197: Leon read it
     * in game and asked for more. Sized against the screen rather than by
     * eye, a display 1.5 blocks in front of the camera looks onto about
     * 3.7 blocks of width and "1 in 10,000,000" at scale 1 is about 1.6
     * blocks of text, so the top of this table fills about two thirds of
     * it.
     */
    private static float counterScaleFor(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> 1.55f;
            case MYTHICAL -> 1.3f;
            case LEGENDARY -> 1.05f;
            default -> 0.85f; // Epic
        };
    }

    // -------------------------------------------------------------- state

    private final SolRNGPlugin plugin;
    private final Player player;
    private final RollStages stages;
    private final long odds;
    private final long actTicks;
    private final boolean counter;
    private final boolean steer;
    private final float counterScale;
    /** Which way it comes in from, fixed at the start so it cannot chase a turning head. */
    private final double bearing;

    // The act, and everything the act paints.
    private int act = -1;
    private Rarity band = Rarity.EPIC;
    private Color colour = Color.WHITE;
    private Particle.DustTransition tail;
    private Particle.DustOptions spark;
    private Particle accent = Particle.END_ROD;
    private int trailPoints = 8;
    private double height;
    private double reach;

    private BlockDisplay headPiece;
    private RollCounter readout;
    private Location last;
    private long lastStep = -EVERY;
    private long lastRoar = 0L;
    private long frames = 0L;
    private boolean done;

    RollComet(SolRNGPlugin plugin, Player player, RollStages stages, long odds, long actTicks) {
        this.plugin = plugin;
        this.player = player;
        this.stages = stages;
        this.odds = odds;
        this.actTicks = Math.max(1L, actTicks);
        this.counter = plugin.getConfig().getBoolean("roll-item.comet.counter", true);
        // Off by default. Steering writes the player's real rotation, and
        // the client reports that back, so everybody else would see them
        // spin round. Aiming the comet into the view they already have is
        // what keeps the whole thing private.
        this.steer = plugin.getConfig().getBoolean("roll-item.comet.steer-view", false);
        this.counterScale = (float) Math.max(0.2,
                plugin.getConfig().getDouble("roll-item.comet.counter-scale", 1.0));

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

    /**
     * Whether this comet is the one drawing the text on the screen. When
     * the counter is switched off in config the reel keeps the title and
     * the drop shows its usual candidates, so nothing is left blank.
     */
    boolean ownsScreen() {
        return counter;
    }

    // ---------------------------------------------------------- lifecycle

    /**
     * Opens one act: a new comet in that band's size and colour, launched
     * from that band's height, to arrive when the act ends.
     */
    void startAct(int index) {
        this.act = index;
        paint(stages.rarityAt(index));
        this.lastStep = -EVERY;
        this.lastRoar = 0L;

        Location at = positionAt(0.0);
        this.last = at;
        if (headPiece != null && headPiece.isValid()) headPiece.remove();
        // Reach a little past the starting height, or the head is culled by
        // the client on the frames where it is furthest away and the whole
        // thing appears out of nothing halfway down.
        headPiece = plugin.getAuraManager().parts().block(at,
                AuraConcepts.lantern(band).createBlockData(), height * 1.6, true, box(band));
        // The one piece of the reveal that is seen through terrain. A comet
        // behind a hill that cannot be seen until it clears the ridge is a
        // comet half the players never notice.
        AuraParts.glowing(headPiece, colour);
        // Not riding anything, so it wants smoothing: the path is sent every
        // two ticks and the client glides across them.
        headPiece.setTeleportDuration(EVERY);
        player.showEntity(plugin, headPiece);

        if (counter && wantsText()) {
            float scale = counterScale * counterScaleFor(band);
            if (readout == null) {
                readout = new RollCounter(plugin, player, scale);
                readout.show(line(stages.shownOdds(act, 0.0)));
            } else {
                readout.pop(line(stages.shownOdds(act, 0.0)), scale);
            }
        }
        player.playSound(at, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.7f, 0.5f);
    }

    /** One step of the fall. {@code actElapsed} is ticks into the current act. */
    void tick(long actElapsed, double hushFrom) {
        if (done || headPiece == null || !headPiece.isValid()) return;
        if (actElapsed - lastStep < EVERY) return;
        lastStep = actElapsed;
        frames++;
        // A roller who walked through a portal mid-roll. Teleporting a
        // display across worlds to chase them is not worth the edge cases.
        if (!player.getWorld().equals(headPiece.getWorld())) {
            stop();
            return;
        }

        double progress = Math.min(1.0, (double) actElapsed / actTicks);
        Location now = positionAt(progress);
        headPiece.teleport(now);
        streak(last, now);
        last = now;

        if (counter && readout != null) readout.show(line(stages.shownOdds(act, progress)));
        if (progress < hushFrom) roar(actElapsed, progress, now);
        if (steer) steerTowards(now);
    }

    /**
     * The end of an act. {@code last} says whether this is the drop's own
     * band, in which case the reveal stops here and {@link RollAura} plays
     * the band's ending; otherwise it is a breakthrough and the next act
     * opens on top of it.
     *
     * Either way the number lands on the figure the act was climbing to,
     * and is thrown out of the screen. That figure is the next band's
     * entry on a breakthrough and the drop's real odds on the last act,
     * so the pop always happens ON the number that matters.
     */
    void impact(boolean last) {
        if (done) return;
        if (headPiece != null) {
            if (headPiece.isValid()) headPiece.remove();
            headPiece = null;
        }
        burst();
        if (counter && readout != null) {
            long reached = last ? odds : stages.target(act);
            readout.pop(line(reached), counterScale * counterScaleFor(band));
        }
        if (last) {
            done = true;
            if (readout != null) {
                RollCounter hanging = readout;
                readout = null;
                plugin.getServer().getScheduler().runTaskLater(plugin, hanging::stop, HOLD_AFTER_LANDING);
            }
        } else {
            // Low and high together, a step higher each band, so a
            // breakthrough reads as one large sound rather than two.
            float step = 0.18f * Math.max(0, act);
            Location at = player.getLocation();
            player.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, Math.min(2.0f, 0.7f + step));
            player.playSound(at, Sound.ENTITY_ENDER_EYE_DEATH, 0.9f, Math.min(2.0f, 1.1f + step));
        }
    }

    /**
     * The comet's own share of an impact: a coloured flash and a ring of
     * its trail thrown outward.
     *
     * A ring at {@link #CLEAR} blocks and not a cloud on the player. The
     * flash is a single particle with no position to speak of, so it is
     * the one thing that can sit on the camera.
     */
    private void burst() {
        Location eye = player.getEyeLocation();
        // FLASH takes a Colour since 1.21.11, which is the one particle in
        // the game that fills the screen in a colour we choose.
        player.spawnParticle(Particle.FLASH, eye, 1, 0.0, 0.0, 0.0, 0.0, colour);
        int points = 28;
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 / points * i;
            for (double r = CLEAR; r <= CLEAR + 1.6; r += 0.8) {
                Location at = eye.clone().add(Math.cos(a) * r, -0.4, Math.sin(a) * r);
                player.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 1, 0.1, 0.25, 0.1, 0.0, tail);
            }
            if (i % 3 == 0) {
                Location at = eye.clone().add(Math.cos(a) * CLEAR, 0.1, Math.sin(a) * CLEAR);
                player.spawnParticle(accent, at, 2, 0.1, 0.2, 0.1, 0.02);
            }
        }
    }

    /** Stops it dead, for a roll that was abandoned. */
    void stop() {
        done = true;
        if (headPiece != null) {
            if (headPiece.isValid()) headPiece.remove();
            headPiece = null;
        }
        if (readout != null) {
            readout.stop();
            readout = null;
        }
    }

    /** Everything one band paints, in one place. */
    private void paint(Rarity rarity) {
        this.band = rarity;
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
        this.height = heightFor(rarity);
        this.reach = reachFor(rarity);
    }

    // ------------------------------------------------------------ the path

    /**
     * Where the comet is at {@code progress} through its act.
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
        // It stops a little short of the roller rather than inside them,
        // for the same reason the burst does.
        double drop = 1.0 + CLEAR * t;
        Location at = player.getLocation().add(Math.cos(angle) * radius, height * (1.0 - t) + drop,
                Math.sin(angle) * radius);
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
        double clearSq = CLEAR * CLEAR;
        Location eye = player.getEyeLocation();
        for (int i = 0; i < trailPoints; i++) {
            cursor.add(step);
            if (cursor.distanceSquared(eye) < clearSq) continue;
            player.spawnParticle(Particle.DUST_COLOR_TRANSITION, cursor, 1, 0.12, 0.12, 0.12, 0.0, tail);
        }
        if (to.distanceSquared(eye) < clearSq) return;
        // Embers shedding off the head, thrown backwards along the path.
        player.spawnParticle(accent, to, 3, 0.25, 0.25, 0.25, 0.02);
        player.spawnParticle(Particle.DUST, to, 2, 0.4, 0.4, 0.4, 0.0, spark);
    }

    /**
     * The approach. One heavy beat, getting louder and higher as it closes,
     * with the pitch nudged each time so a repeated sound does not start to
     * read as a machine.
     */
    private void roar(long actElapsed, double progress, Location at) {
        if (actElapsed - lastRoar < 16L) return;
        lastRoar = actElapsed;
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

    /**
     * One frame of the number, with a highlight travelling along it.
     *
     * Leon asked for "wat meer leven erin dus kleur ofzo". A flat gradient
     * is still a flat gradient however fast the digits change, so every
     * character is mixed between the band's colour and a warm near-white
     * on a wave that walks across the text a little each frame. It costs
     * one component per character on one viewer's screen.
     */
    private Component line(long value) {
        String text = RollFormat.chance(value);
        Component out = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            double phase = (double) i / Math.max(1, text.length()) - frames * 0.05;
            double lit = 0.5 + 0.5 * Math.sin(phase * Math.PI * 2.0);
            out = out.append(Component.text(text.charAt(i))
                    .color(TextColor.color(mix(colour.getRed(), 255, lit),
                            mix(colour.getGreen(), 252, lit), mix(colour.getBlue(), 224, lit)))
                    .decoration(TextDecoration.BOLD, true));
        }
        return out;
    }

    private static int mix(int from, int to, double amount) {
        return Math.max(0, Math.min(255, (int) Math.round(from + (to - from) * amount)));
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
